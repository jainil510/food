package com.foodrush.backend.dto;

import java.math.BigDecimal;

public record DashboardStatsDTO(
        long totalOrders,
        long todayOrders,
        long pendingOrders,
        BigDecimal revenue
) {
}
