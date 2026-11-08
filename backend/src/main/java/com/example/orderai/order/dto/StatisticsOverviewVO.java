package com.example.orderai.order.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/** 订单统计概览（仅当前用户数据） */
@Data
public class StatisticsOverviewVO {

    private long totalOrders;
    private long todayOrders;
    private long monthOrders;
    private BigDecimal totalAmount;
    private long pendingPaymentCount;
    private long pendingShipmentCount;
    private Map<String, Long> statusCounts;
}
