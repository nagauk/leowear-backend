package com.clothstore.controller;

import com.clothstore.dto.ApiResponse;
import com.clothstore.dto.ReturnRequestDto;
import com.clothstore.entity.ReturnStatus;
import com.clothstore.service.ReturnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/returns")
@RequiredArgsConstructor
public class ReturnController {

    private final ReturnService returnService;

    @PostMapping
    public ResponseEntity<ApiResponse<ReturnRequestDto>> createReturn(
            @Valid @RequestBody ReturnRequestDto dto,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok("Return request submitted",
                returnService.createReturn(auth.getName(), dto)));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<Page<ReturnRequestDto>>> myReturns(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                returnService.getMyReturns(auth.getName(), PageRequest.of(page, size))));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<ReturnRequestDto>>> allReturns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                returnService.getAllReturns(PageRequest.of(page, size))));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ReturnRequestDto>> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        ReturnStatus status = ReturnStatus.valueOf(body.get("status"));
        String notes = body.get("adminNotes");
        String refundTxn = body.get("refundTransactionId");
        return ResponseEntity.ok(ApiResponse.ok("Return status updated",
                returnService.updateStatus(id, status, notes, refundTxn)));
    }
}
