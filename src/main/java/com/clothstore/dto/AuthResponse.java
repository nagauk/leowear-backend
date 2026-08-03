package com.clothstore.dto;

import com.clothstore.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    /** Short-lived JWT access token */
    private String accessToken;
    /** Long-lived opaque refresh token (rotated on each use) */
    private String refreshToken;
    private String type;
    private Long id;
    private String username;
    private String email;
    private String phone;
    private String fullName;
    private Role role;
    /** Access token lifetime in seconds (for client-side scheduling) */
    private Long expiresIn;
    private Boolean emailVerified;
    private Boolean phoneVerified;
}

