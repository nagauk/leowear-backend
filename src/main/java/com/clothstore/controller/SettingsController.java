package com.clothstore.controller;

import com.clothstore.dto.ApiResponse;
import com.clothstore.dto.StoreSettingsDto;
import com.clothstore.service.StoreSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final StoreSettingsService settingsService;

    /** Public — cart needs delivery rules */
    @GetMapping("/delivery")
    public ResponseEntity<ApiResponse<StoreSettingsDto>> getDeliverySettings() {
        return ResponseEntity.ok(ApiResponse.ok(settingsService.get()));
    }

    @PutMapping("/delivery")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StoreSettingsDto>> updateDeliverySettings(
            @Valid @RequestBody StoreSettingsDto dto) {
        return ResponseEntity.ok(ApiResponse.ok("Delivery settings updated", settingsService.update(dto)));
    }
}
