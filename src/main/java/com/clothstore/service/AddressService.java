package com.clothstore.service;

import com.clothstore.dto.AddressDto;
import com.clothstore.entity.User;
import com.clothstore.entity.UserAddress;
import com.clothstore.exception.BadRequestException;
import com.clothstore.exception.ResourceNotFoundException;
import com.clothstore.repository.UserAddressRepository;
import com.clothstore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final UserAddressRepository addressRepository;
    private final UserRepository userRepository;

    public List<AddressDto> list(String username) {
        User user = requireUser(username);
        return addressRepository.findByUserOrderByDefaultAddressDescCreatedAtDesc(user)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public AddressDto create(String username, AddressDto dto) {
        User user = requireUser(username);
        boolean makeDefault = dto.isDefaultAddress() || addressRepository.countByUser(user) == 0;

        if (makeDefault) {
            addressRepository.clearDefaultForUser(user);
        }

        UserAddress addr = UserAddress.builder()
                .user(user)
                .label(blankToNull(dto.getLabel()))
                .fullName(blankToNull(dto.getFullName()))
                .line1(dto.getLine1().trim())
                .line2(blankToNull(dto.getLine2()))
                .city(blankToNull(dto.getCity()))
                .state(blankToNull(dto.getState()))
                .pincode(blankToNull(dto.getPincode()))
                .phone(blankToNull(dto.getPhone()))
                .defaultAddress(makeDefault)
                .build();

        return toDto(addressRepository.save(addr));
    }

    @Transactional
    public AddressDto update(String username, Long id, AddressDto dto) {
        User user = requireUser(username);
        UserAddress addr = addressRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        if (dto.getLabel() != null) addr.setLabel(blankToNull(dto.getLabel()));
        if (dto.getFullName() != null) addr.setFullName(blankToNull(dto.getFullName()));
        if (dto.getLine1() != null && !dto.getLine1().isBlank()) addr.setLine1(dto.getLine1().trim());
        if (dto.getLine2() != null) addr.setLine2(blankToNull(dto.getLine2()));
        if (dto.getCity() != null) addr.setCity(blankToNull(dto.getCity()));
        if (dto.getState() != null) addr.setState(blankToNull(dto.getState()));
        if (dto.getPincode() != null) addr.setPincode(blankToNull(dto.getPincode()));
        if (dto.getPhone() != null) addr.setPhone(blankToNull(dto.getPhone()));

        if (dto.isDefaultAddress()) {
            addressRepository.clearDefaultForUser(user);
            addr.setDefaultAddress(true);
        }

        return toDto(addressRepository.save(addr));
    }

    @Transactional
    public void delete(String username, Long id) {
        User user = requireUser(username);
        UserAddress addr = addressRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        boolean wasDefault = addr.isDefaultAddress();
        addressRepository.delete(addr);
        if (wasDefault) {
            addressRepository.findByUserOrderByDefaultAddressDescCreatedAtDesc(user).stream()
                    .findFirst()
                    .ifPresent(a -> {
                        a.setDefaultAddress(true);
                        addressRepository.save(a);
                    });
        }
    }

    @Transactional
    public AddressDto setDefault(String username, Long id) {
        User user = requireUser(username);
        UserAddress addr = addressRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        addressRepository.clearDefaultForUser(user);
        addr.setDefaultAddress(true);
        return toDto(addressRepository.save(addr));
    }

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private AddressDto toDto(UserAddress a) {
        return AddressDto.builder()
                .id(a.getId())
                .label(a.getLabel())
                .fullName(a.getFullName())
                .line1(a.getLine1())
                .line2(a.getLine2())
                .city(a.getCity())
                .state(a.getState())
                .pincode(a.getPincode())
                .phone(a.getPhone())
                .defaultAddress(a.isDefaultAddress())
                .formatted(a.toShippingLine())
                .build();
    }
}
