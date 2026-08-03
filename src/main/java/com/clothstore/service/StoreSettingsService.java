package com.clothstore.service;

import com.clothstore.dto.StoreSettingsDto;
import com.clothstore.entity.StoreSettings;
import com.clothstore.repository.StoreSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class StoreSettingsService {

    private final StoreSettingsRepository repository;

    public StoreSettings getEntity() {
        return repository.findAll().stream().findFirst().orElseGet(() ->
                repository.save(StoreSettings.builder()
                        .deliveryCharge(new BigDecimal("49.00"))
                        .freeDeliveryMinAmount(new BigDecimal("999.00"))
                        .build()));
    }

    public StoreSettingsDto get() {
        StoreSettings s = getEntity();
        return StoreSettingsDto.builder()
                .id(s.getId())
                .deliveryCharge(s.getDeliveryCharge())
                .freeDeliveryMinAmount(s.getFreeDeliveryMinAmount())
                .build();
    }

    @Transactional
    public StoreSettingsDto update(StoreSettingsDto dto) {
        StoreSettings s = getEntity();
        if (dto.getDeliveryCharge() != null) {
            s.setDeliveryCharge(dto.getDeliveryCharge());
        }
        if (dto.getFreeDeliveryMinAmount() != null) {
            s.setFreeDeliveryMinAmount(dto.getFreeDeliveryMinAmount());
        }
        s = repository.save(s);
        return StoreSettingsDto.builder()
                .id(s.getId())
                .deliveryCharge(s.getDeliveryCharge())
                .freeDeliveryMinAmount(s.getFreeDeliveryMinAmount())
                .build();
    }

    /** Compute delivery fee for a given items subtotal */
    public BigDecimal computeDeliveryCharge(BigDecimal subtotal) {
        StoreSettings s = getEntity();
        if (subtotal == null) subtotal = BigDecimal.ZERO;
        if (subtotal.compareTo(s.getFreeDeliveryMinAmount()) >= 0) {
            return BigDecimal.ZERO;
        }
        return s.getDeliveryCharge() != null ? s.getDeliveryCharge() : BigDecimal.ZERO;
    }
}
