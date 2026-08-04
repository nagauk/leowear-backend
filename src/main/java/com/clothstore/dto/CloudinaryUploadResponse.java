package com.clothstore.dto;

/**
 * Response payload returned from {@code POST /api/admin/uploads/image}.
 *
 * Includes:
 * <ul>
 *   <li>Final asset metadata ({@link #bytes}, {@link #width}, {@link #height}, {@link #format})
 *       as reported by Cloudinary after upload.</li>
 *   <li>Original source metadata ({@link #originalBytes}, {@link #originalWidth},
 *       {@link #originalHeight}) so the UI can show "resized 2.4 MB → 940 KB, 4032×3024 → 1600×1200".</li>
 *   <li>{@link #publicId} — Cloudinary's public identifier; returned for future
 *       delete support but not persisted in the product_images table today.</li>
 * </ul>
 */
public record CloudinaryUploadResponse(
        String url,
        String publicId,
        long bytes,
        int width,
        int height,
        long originalBytes,
        int originalWidth,
        int originalHeight,
        String format
) {}
