package com.clothstore.controller;

import com.clothstore.dto.ApiResponse;
import com.clothstore.dto.FeedbackDto;
import com.clothstore.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    /** Anyone can submit feedback (guest or logged-in) */
    @PostMapping
    public ResponseEntity<ApiResponse<FeedbackDto>> submit(
            @Valid @RequestBody FeedbackDto dto,
            Authentication auth) {
        String username = auth != null && auth.isAuthenticated() ? auth.getName() : null;
        return ResponseEntity.ok(ApiResponse.ok("Thank you for your feedback!",
                feedbackService.submit(dto, username)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<FeedbackDto>>> all(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                feedbackService.all(PageRequest.of(page, size))));
    }
}
