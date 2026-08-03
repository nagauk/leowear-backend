package com.clothstore.controller;

import com.clothstore.dto.ApiResponse;
import com.clothstore.dto.AuthResponse;
import com.clothstore.dto.RegisterRequest;
import com.clothstore.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin creates employee accounts (username + password).
 * Employees cannot self-register as EMPLOYEE.
 */
@RestController
@RequestMapping("/api/admin/employees")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class EmployeeController {

    private final AuthService authService;

    @PostMapping
    public ResponseEntity<ApiResponse<AuthResponse>> createEmployee(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Employee account created",
                authService.createEmployee(request)));
    }
}
