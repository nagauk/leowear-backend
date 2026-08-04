package com.clothstore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for Cloudinary image hosting and the server-side resizer.
 *
 * Bound from the {@code cloudinary.*} block in application.yml. Credentials
 * come from environment variables: CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY,
 * CLOUDINARY_API_SECRET.
 *
 * Tuning knobs:
 * <ul>
 *   <li>{@code max-file-size-bytes} — every uploaded image is reduced to
 *       &le; this byte size before being sent to Cloudinary. Default 1 MB.</li>
 *   <li>{@code max-dimension} — the longest edge of the resized output. If
 *       the source is larger, it is downscaled to this size first. Default 1600.</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "cloudinary")
public class CloudinaryProperties {

    private String cloudName;
    private String apiKey;
    private String apiSecret;
    private String folder = "leowear/products";
    private long maxFileSizeBytes = 1_048_576L; // 1 MB
    private int maxDimension = 1600;

    public String getCloudName() { return cloudName; }
    public void setCloudName(String cloudName) { this.cloudName = cloudName; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getApiSecret() { return apiSecret; }
    public void setApiSecret(String apiSecret) { this.apiSecret = apiSecret; }

    public String getFolder() { return folder; }
    public void setFolder(String folder) { this.folder = folder; }

    public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
    public void setMaxFileSizeBytes(long maxFileSizeBytes) { this.maxFileSizeBytes = maxFileSizeBytes; }

    public int getMaxDimension() { return maxDimension; }
    public void setMaxDimension(int maxDimension) { this.maxDimension = maxDimension; }
}
