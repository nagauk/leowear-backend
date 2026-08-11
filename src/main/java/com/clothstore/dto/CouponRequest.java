package com.clothstore.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Admin payload for create/update. Codes are auto-uppercased server-side and
 * restricted to a safe charset (alphanumeric + dash + underscore) so they don't
 * break URLs, logs, or the customer-side input box.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponRequest {

    @NotBlank
    @Size(min = 3, max = 20)
    @Pattern(regexp = "^[A-Za-z0-9_-]+$",
            message = "Code may contain only letters, digits, '-' and '_'")
    private String code;

    @Size(max = 200)
    private String description;

    @NotNull
    @DecimalMin(value = "0.01", message = "Discount percent must be greater than 0")
    @DecimalMax(value = "100.00", message = "Discount percent cannot exceed 100")
    private BigDecimal discountPercent;

    /** Optional. Null = no minimum. */
    @DecimalMin(value = "0.00")
    private BigDecimal minOrderAmount;

    /** Optional. Null = no expiry. */
    private LocalDateTime expiresAt;

    /** Optional. Null = unlimited global usage. */
    private Integer usageLimit;

    /** Optional. Null = unlimited per-user usage. */
    private Integer usagePerUser;

    private Boolean firstTimeUserOnly;

    private Boolean active;
}