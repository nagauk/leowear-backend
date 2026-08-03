package com.clothstore.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreSettingsDto {
    private Long id;

    @NotNull
    @DecimalMin("0")
    private BigDecimal deliveryCharge;

    @NotNull
    @DecimalMin("0")
    private BigDecimal freeDeliveryMinAmount;
}
