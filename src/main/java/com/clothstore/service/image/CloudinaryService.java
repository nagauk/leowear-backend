package com.clothstore.service.image;

import com.clothstore.config.CloudinaryProperties;
import com.clothstore.exception.BadRequestException;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Thin wrapper around the Cloudinary Java SDK. Loads credentials from
 * {@link CloudinaryProperties} (which in turn reads from env vars) and exposes
 * a single {@link #upload(byte[], String)} method used by the admin upload
 * controller.
 *
 * <p>Credentials are not validated at startup; if any are blank the SDK call
 * will fail at upload time with a clear error. We log a WARN at startup so the
 * missing-config state is visible in logs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {

    private final CloudinaryProperties props;
    private Cloudinary cloudinary;

    /** Output of {@link #upload(byte[], String)}. */
    public record UploadResult(String url, String publicId, long bytes, int width, int height) {}

    @PostConstruct
    void init() {
        if (isBlank(props.getCloudName()) || isBlank(props.getApiKey()) || isBlank(props.getApiSecret())) {
            log.warn("Cloudinary credentials not configured. Set CLOUDINARY_CLOUD_NAME, "
                    + "CLOUDINARY_API_KEY and CLOUDINARY_API_SECRET in env. Uploads will fail until configured.");
        }
        cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", props.getCloudName(),
                "api_key", props.getApiKey(),
                "api_secret", props.getApiSecret(),
                "secure", true));
    }

    /**
     * Upload the resized bytes to Cloudinary and return the immutable CDN URL
     * plus key metadata. {@code filenameHint} is currently unused beyond the
     * public-id generation; kept for future use (e.g. derive a stable id).
     */
    public UploadResult upload(byte[] bytes, String filenameHint) {
        try {
            String publicId = props.getFolder() + "/" + UUID.randomUUID();
            Map<?, ?> result = cloudinary.uploader().upload(bytes, ObjectUtils.asMap(
                    "public_id", publicId,
                    "folder", props.getFolder(),
                    "overwrite", false,
                    "resource_type", "image",
                    "format", "jpg"));

            String url = (String) result.get("secure_url");
            String pid = (String) result.get("public_id");
            long b = ((Number) result.get("bytes")).longValue();
            int w = ((Number) result.get("width")).intValue();
            int h = ((Number) result.get("height")).intValue();
            return new UploadResult(url, pid, b, w, h);
        } catch (IOException e) {
            throw new BadRequestException("Cloudinary upload failed: " + e.getMessage());
        } catch (RuntimeException e) {
            // The SDK also throws RuntimeException for some 4xx responses (auth, etc.)
            throw new BadRequestException("Cloudinary upload failed: " + e.getMessage());
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
