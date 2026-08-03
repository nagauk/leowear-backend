package com.clothstore.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be 3–50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._]+$", message = "Username can only contain letters, numbers, dots and underscores")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    @Size(max = 100)
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#^()_\\-+={}\\[\\]|:;\"'<>,./~`]).{8,}$",
            message = "Password must include uppercase, lowercase, a number, and a special character"
    )
    private String password;

    @Size(max = 100)
    private String fullName;

    @Pattern(
            regexp = "^$|^[6-9]\\d{9}$|^\\+91[6-9]\\d{9}$",
            message = "Enter a valid 10-digit Indian mobile number"
    )
    private String phone;

    @Size(max = 255)
    private String address;
}
