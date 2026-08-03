package com.clothstore.service;

import com.clothstore.dto.ReturnRequestDto;
import com.clothstore.entity.*;
import com.clothstore.exception.BadRequestException;
import com.clothstore.exception.ResourceNotFoundException;
import com.clothstore.repository.OrderRepository;
import com.clothstore.repository.ProductRepository;
import com.clothstore.repository.ReturnRequestRepository;
import com.clothstore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReturnService {

    private final ReturnRequestRepository returnRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Transactional
    public ReturnRequestDto createReturn(String username, ReturnRequestDto dto) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getRole() == Role.ADMIN) {
            throw new BadRequestException("Admin cannot submit return requests");
        }

        Order order = orderRepository.findById(dto.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("You can only return your own orders");
        }

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new BadRequestException("Only delivered orders can be returned");
        }

        OrderItem line = null;
        int qty = 1;
        if (dto.getOrderItemId() != null) {
            line = order.getItems().stream()
                    .filter(i -> i.getId().equals(dto.getOrderItemId()))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("Selected item is not part of this order"));

            int ordered = line.getQuantity() != null ? line.getQuantity() : 1;
            qty = dto.getQuantity() != null ? dto.getQuantity() : ordered;
            if (qty < 1 || qty > ordered) {
                throw new BadRequestException("Return quantity must be between 1 and " + ordered);
            }
        }

        ReturnRequest request = ReturnRequest.builder()
                .order(order)
                .user(user)
                .orderItem(line)
                .quantity(line != null ? qty : null)
                .reason(dto.getReason().trim())
                .status(ReturnStatus.PENDING)
                .build();

        return toDto(returnRepository.save(request));
    }

    public Page<ReturnRequestDto> getMyReturns(String username, Pageable pageable) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return returnRepository.findByUserOrderByCreatedAtDesc(user, pageable).map(this::toDto);
    }

    public Page<ReturnRequestDto> getAllReturns(Pageable pageable) {
        return returnRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toDto);
    }

    @Transactional
    public ReturnRequestDto updateStatus(Long id, ReturnStatus status, String adminNotes, String refundTransactionId) {
        ReturnRequest request = returnRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Return request not found: " + id));

        request.setStatus(status);
        if (adminNotes != null) request.setAdminNotes(adminNotes);
        if (refundTransactionId != null && !refundTransactionId.isBlank()) {
            request.setRefundTransactionId(refundTransactionId.trim());
            request.setRefundStatus("COMPLETED");
        } else if (status == ReturnStatus.APPROVED || status == ReturnStatus.COMPLETED) {
            Order ord = request.getOrder();
            boolean paidOnline = ord.getPaymentStatus() != null
                    && "PAID".equals(ord.getPaymentStatus().name())
                    && ord.getPaymentMethod() != null
                    && "PREPAID".equals(ord.getPaymentMethod().name());
            if (paidOnline && request.getRefundTransactionId() == null) {
                request.setRefundStatus("PENDING");
            } else if (ord.getPaymentMethod() != null && "COD".equals(ord.getPaymentMethod().name())
                    && (ord.getPaymentStatus() == null || !"PAID".equals(ord.getPaymentStatus().name()))) {
                request.setRefundStatus("NOT_APPLICABLE");
            }
        }

        if (status == ReturnStatus.APPROVED || status == ReturnStatus.COMPLETED) {
            // Restore stock for returned line (or all items if whole-order return)
            if (request.getOrderItem() != null) {
                restoreStock(request.getOrderItem(), request.getQuantity() != null ? request.getQuantity() : 1);
            } else {
                for (OrderItem item : request.getOrder().getItems()) {
                    restoreStock(item, item.getQuantity());
                }
                Order order = request.getOrder();
                if (order.getStatus() != OrderStatus.RETURNED) {
                    order.setStatus(OrderStatus.RETURNED);
                    orderRepository.save(order);
                }
            }
        }

        return toDto(returnRepository.save(request));
    }

    private void restoreStock(OrderItem item, int qty) {
        if (item.getVariant() != null) {
            ProductVariant v = item.getVariant();
            v.setStock((v.getStock() != null ? v.getStock() : 0) + qty);
        }
        Product p = item.getProduct();
        if (p != null) {
            p.setStock((p.getStock() != null ? p.getStock() : 0) + qty);
            productRepository.save(p);
        }
    }

    public ReturnRequestDto toDto(ReturnRequest r) {
        ReturnRequestDto.ReturnRequestDtoBuilder b = ReturnRequestDto.builder()
                .id(r.getId())
                .orderId(r.getOrder().getId())
                .orderNumber(r.getOrder().getOrderNumber())
                .userId(r.getUser().getId())
                .username(r.getUser().getUsername())
                .reason(r.getReason())
                .status(r.getStatus())
                .adminNotes(r.getAdminNotes())
                .quantity(r.getQuantity())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt());

        if (r.getOrderItem() != null) {
            OrderItem oi = r.getOrderItem();
            b.orderItemId(oi.getId())
                    .productName(oi.getProduct() != null ? oi.getProduct().getName() : null)
                    .size(oi.getSize())
                    .color(oi.getColor());
        }
        b.refundTransactionId(r.getRefundTransactionId())
                .refundStatus(r.getRefundStatus());
        if (r.getOrder() != null) {
            Order o = r.getOrder();
            b.orderPaymentMethod(o.getPaymentMethod() != null ? o.getPaymentMethod().name() : null)
                    .orderPaymentStatus(o.getPaymentStatus() != null ? o.getPaymentStatus().name() : null)
                    .orderPaymentRef(o.getPaymentRef());
            // COD returns — no online refund
            if (r.getRefundStatus() == null && o.getPaymentMethod() != null
                    && o.getPaymentMethod().name().equals("COD")
                    && (o.getPaymentStatus() == null || !o.getPaymentStatus().name().equals("PAID"))) {
                b.refundStatus("NOT_APPLICABLE");
            }
        }
        return b.build();
    }
}
