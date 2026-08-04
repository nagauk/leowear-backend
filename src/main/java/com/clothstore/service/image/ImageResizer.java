package com.clothstore.service.image;

import com.clothstore.config.CloudinaryProperties;
import com.clothstore.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Server-side image resizer used by {@code AdminUploadController} before a
 * picked file is sent to Cloudinary. Standardises every uploaded image to
 * JPEG of manageable dimensions and bounded byte size.
 *
 * <p>Strategy:
 * <ol>
 *   <li>Decode JPEG/PNG/WEBP via {@link ImageIO}. TwelveMonkeys
 *       {@code imageio-webp} listens via {@code META-INF/services} so WEBP
 *       decoding is transparent.</li>
 *   <li>Try a sequence of fall-back longest-side targets
 *       ({@code [props.maxDimension, 1200, 1024, 800]}). For each:
 *       <ul>
 *         <li>Downscale with bilinear interpolation, white background, opaque
 *             RGB (so transparent PNGs end up opaque JPEG — fine for product
 *             photos).</li>
 *         <li>Re-encode as JPEG at quality 0.85, stepping down by 0.10 to a
 *             floor of 0.30. If bytes &le; target, return immediately.</li>
 *       </ul>
 *   </li>
 *   <li>After the last fall-back, accept the best-effort bytes and log a
 *       WARN. We never silently fail.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImageResizer {

    private final CloudinaryProperties props;

    /** Output of {@link #resize(byte[], String)}. */
    public record ResizedImage(
            byte[] bytes,
            int width,
            int height,
            long size,
            int originalWidth,
            int originalHeight,
            long originalSize) {}

    public ResizedImage resize(byte[] input, String contentType) {
        if (input == null || input.length == 0) {
            throw new BadRequestException("Empty file");
        }
        validateContentType(contentType);

        BufferedImage src;
        try (InputStream in = new ByteArrayInputStream(input)) {
            src = ImageIO.read(in);
        } catch (IOException e) {
            throw new BadRequestException("Could not decode image: " + e.getMessage());
        }
        if (src == null) {
            throw new BadRequestException("Unsupported image format: " + contentType);
        }

        int origW = src.getWidth();
        int origH = src.getHeight();
        long origBytes = input.length;
        long target = props.getMaxFileSizeBytes();

        int[] fallbacks = { props.getMaxDimension(), 1200, 1024, 800 };
        for (int dim : fallbacks) {
            BufferedImage scaled = scaleIfNeeded(src, dim);
            byte[] encoded = encodeWithQualityLoop(scaled, target);
            if (encoded.length <= target) {
                return new ResizedImage(
                        encoded,
                        scaled.getWidth(),
                        scaled.getHeight(),
                        encoded.length,
                        origW, origH, origBytes);
            }
        }

        // Last resort: 800px @ q=0.30. Log a WARN so the issue is visible.
        BufferedImage scaled = scaleIfNeeded(src, 800);
        byte[] encoded = encodeWithQualityLoop(scaled, target);
        log.warn("Image could not be reduced below target bytes ({}). Final: {} bytes, {}x{}",
                target, encoded.length, scaled.getWidth(), scaled.getHeight());
        return new ResizedImage(
                encoded,
                scaled.getWidth(),
                scaled.getHeight(),
                encoded.length,
                origW, origH, origBytes);
    }

    private BufferedImage scaleIfNeeded(BufferedImage src, int maxDim) {
        int w = src.getWidth();
        int h = src.getHeight();
        int longestSide = Math.max(w, h);
        if (longestSide <= maxDim) return src;

        double scale = (double) maxDim / longestSide;
        int nw = (int) Math.round(w * scale);
        int nh = (int) Math.round(h * scale);

        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, nw, nh);
            g.drawImage(src, 0, 0, nw, nh, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /**
     * Re-encode as JPEG starting at q=0.85, stepping down by 0.10 to a floor of
     * 0.30. Returns the first encoding that fits {@code target} bytes, or the
     * lowest-quality encoding as a best-effort fallback.
     */
    private byte[] encodeWithQualityLoop(BufferedImage img, long target) {
        double q = 0.85;
        byte[] last = null;
        while (q >= 0.30) {
            byte[] bytes = encodeJpeg(img, q);
            last = bytes;
            if (bytes.length <= target) return bytes;
            q -= 0.10;
        }
        return last;
    }

    private byte[] encodeJpeg(BufferedImage img, double quality) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageWriter writer = null;
        ImageOutputStream ios = null;
        try {
            writer = ImageIO.getImageWritersByFormatName("jpg").next();
            ImageWriteParam p = writer.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality((float) quality);
            ios = ImageIO.createImageOutputStream(baos);
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), p);
        } catch (IOException e) {
            throw new BadRequestException("Failed to encode JPEG: " + e.getMessage());
        } finally {
            if (writer != null) writer.dispose();
            if (ios != null) try { ios.close(); } catch (IOException ignored) { }
        }
        return baos.toByteArray();
    }

    private void validateContentType(String contentType) {
        if (contentType == null) {
            throw new BadRequestException("Missing content type");
        }
        String ct = contentType.toLowerCase(Locale.ROOT);
        if (!ct.equals("image/jpeg") && !ct.equals("image/png") && !ct.equals("image/webp")) {
            throw new BadRequestException("Unsupported image type: " + contentType);
        }
    }
}
