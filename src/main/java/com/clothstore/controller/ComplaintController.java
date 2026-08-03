package com.clothstore.controller;

import com.clothstore.dto.ApiResponse;
import com.clothstore.dto.ComplaintDto;
import com.clothstore.entity.ComplaintStatus;
import com.clothstore.service.ComplaintService;
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
@RequestMapping("/api/complaints")
@RequiredArgsConstructor
public class ComplaintController {

    private final ComplaintService complaintService;

    @PostMapping
    public ResponseEntity<ApiResponse<ComplaintDto>> create(
            @Valid @RequestBody ComplaintDto dto,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok("Complaint submitted",
                complaintService.create(auth.getName(), dto)));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<Page<ComplaintDto>>> my(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                complaintService.myComplaints(auth.getName(), PageRequest.of(page, size))));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ApiResponse<Page<ComplaintDto>>> all(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                complaintService.all(PageRequest.of(page, size))));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ApiResponse<ComplaintDto>> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        ComplaintStatus status = body.get("status") != null
                ? ComplaintStatus.valueOf(body.get("status")) : null;
        return ResponseEntity.ok(ApiResponse.ok("Complaint updated",
                complaintService.updateStatus(id, status, body.get("adminResponse"))));
    }
}
