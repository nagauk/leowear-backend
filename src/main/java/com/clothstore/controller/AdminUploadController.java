package com.clothstore.controller;

import com.clothstore.dto.ApiResponse;
import com.clothstore.dto.CloudinaryUploadResponse;
import com.clothstore.service.image.CloudinaryService;
import com.clothstore.service.image.ImageResizer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Admin/employee upload endpoint. Accepts a single image file via multipart,
 * resizes it server-side so the byte size is at or below the configured target
 * (default 1 MB), uploads the resized bytes to Cloudinary, and returns the
 * resulting CDN URL plus metadata.
 *
 * <p>The returned URL is meant to be dropped into the existing product form's
 * {@code imageList[].url} field; the unchanged product save flow then persists
 * it via {@code POST/PUT /api/admin/products}.
 */
@RestController
@RequestMapping("/api/admin/uploads")
@RequiredArgsConstructor
public class AdminUploadController {

    private final ImageResizer imageResizer;
    private final CloudinaryService cloudinaryService;

    @PostMapping("/image")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ApiResponse<CloudinaryUploadResponse>> uploadImage(
            @RequestParam("file") MultipartFile file) throws IOException {

        ImageResizer.ResizedImage resized = imageResizer.resize(file.getBytes(), file.getContentType());
        CloudinaryService.UploadResult uploaded = cloudinaryService.upload(
                resized.bytes(), file.getOriginalFilename());

        String msg = String.format(
                "Uploaded (resized %,d -> %,d bytes, %dx%d -> %dx%d)",
                resized.originalSize(), resized.size(),
                resized.originalWidth(), resized.originalHeight(),
                resized.width(), resized.height());

        CloudinaryUploadResponse data = new CloudinaryUploadResponse(
                uploaded.url(),
                uploaded.publicId(),
                uploaded.bytes(),
                uploaded.width(),
                uploaded.height(),
                resized.originalSize(),
                resized.originalWidth(),
                resized.originalHeight(),
                "jpg");

        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }
}
