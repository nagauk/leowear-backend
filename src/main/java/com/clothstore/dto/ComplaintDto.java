package com.clothstore.dto;

import com.clothstore.entity.ComplaintStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintDto {
    private Long id;
    private Long userId;
    private String username;
    private Long orderId;
    private String orderNumber;

    @NotBlank(message = "Subject is required")
    @Size(max = 100)
    private String subject;

    @NotBlank(message = "Message is required")
    @Size(max = 2000)
    private String message;

    private ComplaintStatus status;
    private String adminResponse;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
