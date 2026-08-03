package com.clothstore.dto;

import com.clothstore.entity.ReturnStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnRequestDto {
    private Long id;

    @NotNull
    private Long orderId;

    private String orderNumber;
    private Long userId;
    private String username;

    /** Optional — return a specific line item */
    private Long orderItemId;
    private String productName;
    private String size;
    private String color;
    private Integer quantity;

    @NotBlank
    private String reason;

    private ReturnStatus status;
    private String adminNotes;
    private String refundTransactionId;
    private String refundStatus;
    /** Original order payment info for admin */
    private String orderPaymentMethod;
    private String orderPaymentStatus;
    private String orderPaymentRef;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
