package com.clothstore.controller;

import com.clothstore.dto.ApiResponse;
import com.clothstore.dto.PincodeResponse;
import com.clothstore.service.PincodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pincode")
@RequiredArgsConstructor
public class PincodeController {

    private final PincodeService pincodeService;

    @GetMapping("/{pincode}")
    public ResponseEntity<ApiResponse<PincodeResponse>> validate(@PathVariable String pincode) {
        PincodeResponse result = pincodeService.validate(pincode);
        return ResponseEntity.ok(ApiResponse.ok(result.getMessage(), result));
    }
}
