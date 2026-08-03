package com.clothstore.service;

import com.clothstore.dto.DashboardStats;
import com.clothstore.entity.OrderStatus;
import com.clothstore.entity.ReturnStatus;
import com.clothstore.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final ReturnRequestRepository returnRepository;
    private final UserRepository userRepository;

    public DashboardStats getStats() {
        List<Map<String, Object>> recentOrders = orderRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(0, 5))
                .getContent()
                .stream()
                .map(o -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", o.getId());
                    m.put("orderNumber", o.getOrderNumber());
                    m.put("username", o.getUser().getUsername());
                    m.put("totalAmount", o.getTotalAmount());
                    m.put("status", o.getStatus().name());
                    m.put("createdAt", o.getCreatedAt().toString());
                    return m;
                })
                .collect(Collectors.toList());

        List<Map<String, Object>> lowStock = productRepository
                .findByStockLessThanAndActiveTrue(10)
                .stream()
                .limit(10)
                .map(p -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", p.getId());
                    m.put("name", p.getName());
                    m.put("stock", p.getStock());
                    m.put("price", p.getPrice());
                    return m;
                })
                .collect(Collectors.toList());

        return DashboardStats.builder()
                .totalProducts(productRepository.countByActiveTrue())
                .lowStockProducts(productRepository.countByStockLessThanEqualAndActiveTrue(10))
                .totalOrders(orderRepository.count())
                .pendingOrders(orderRepository.countByStatus(OrderStatus.PENDING))
                .deliveredOrders(orderRepository.countByStatus(OrderStatus.DELIVERED))
                .cancelledOrders(orderRepository.countByStatus(OrderStatus.CANCELLED))
                .returnedOrders(orderRepository.countByStatus(OrderStatus.RETURNED))
                .pendingReturns(returnRepository.countByStatus(ReturnStatus.PENDING))
                .totalSales(orderRepository.getTotalSales())
                .deliveredSales(orderRepository.getDeliveredSales())
                .totalCustomers(userRepository.count())
                .recentOrders(recentOrders)
                .lowStockItems(lowStock)
                .build();
    }
}
