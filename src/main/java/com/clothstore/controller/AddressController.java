package com.clothstore.controller;

import com.clothstore.dto.AddressDto;
import com.clothstore.dto.ApiResponse;
import com.clothstore.service.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AddressDto>>> list(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(addressService.list(auth.getName())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AddressDto>> create(
            @Valid @RequestBody AddressDto dto,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok("Address saved", addressService.create(auth.getName(), dto)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AddressDto>> update(
            @PathVariable Long id,
            @Valid @RequestBody AddressDto dto,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok("Address updated", addressService.update(auth.getName(), id, dto)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, Authentication auth) {
        addressService.delete(auth.getName(), id);
        return ResponseEntity.ok(ApiResponse.ok("Address deleted", null));
    }

    @PatchMapping("/{id}/default")
    public ResponseEntity<ApiResponse<AddressDto>> setDefault(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok("Default address updated",
                addressService.setDefault(auth.getName(), id)));
    }
}
