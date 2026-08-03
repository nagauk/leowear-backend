package com.clothstore.service;

import com.clothstore.dto.ComplaintDto;
import com.clothstore.entity.*;
import com.clothstore.exception.BadRequestException;
import com.clothstore.exception.ResourceNotFoundException;
import com.clothstore.repository.ComplaintRepository;
import com.clothstore.repository.OrderRepository;
import com.clothstore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public ComplaintDto create(String username, ComplaintDto dto) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getRole() == Role.ADMIN) {
            throw new BadRequestException("Admin can only view and respond to complaints");
        }

        Order order = null;
        if (dto.getOrderId() != null) {
            order = orderRepository.findById(dto.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
            if (!order.getUser().getId().equals(user.getId())) {
                throw new BadRequestException("You can only complain about your own orders");
            }
        }

        Complaint c = Complaint.builder()
                .user(user)
                .order(order)
                .subject(dto.getSubject().trim())
                .message(dto.getMessage().trim())
                .status(ComplaintStatus.OPEN)
                .build();

        return toDto(complaintRepository.save(c));
    }

    public Page<ComplaintDto> myComplaints(String username, Pageable pageable) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return complaintRepository.findByUserOrderByCreatedAtDesc(user, pageable).map(this::toDto);
    }

    public Page<ComplaintDto> all(Pageable pageable) {
        return complaintRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toDto);
    }

    @Transactional
    public ComplaintDto updateStatus(Long id, ComplaintStatus status, String adminResponse) {
        Complaint c = complaintRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Complaint not found"));
        if (status != null) c.setStatus(status);
        if (adminResponse != null) c.setAdminResponse(adminResponse);
        return toDto(complaintRepository.save(c));
    }

    private ComplaintDto toDto(Complaint c) {
        return ComplaintDto.builder()
                .id(c.getId())
                .userId(c.getUser().getId())
                .username(c.getUser().getUsername())
                .orderId(c.getOrder() != null ? c.getOrder().getId() : null)
                .orderNumber(c.getOrder() != null ? c.getOrder().getOrderNumber() : null)
                .subject(c.getSubject())
                .message(c.getMessage())
                .status(c.getStatus())
                .adminResponse(c.getAdminResponse())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
