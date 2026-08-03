package com.clothstore.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class DashboardStats {
    private long totalProducts;
    private long lowStockProducts;
    private long totalOrders;
    private long pendingOrders;
    private long deliveredOrders;
    private long cancelledOrders;
    private long returnedOrders;
    private long pendingReturns;
    private BigDecimal totalSales;
    private BigDecimal deliveredSales;
    private long totalCustomers;
    private List<Map<String, Object>> recentOrders;
    private List<Map<String, Object>> lowStockItems;
}
