package com.example.orderai.order.dto;

import com.example.orderai.order.entity.OrderEntity;
import com.example.orderai.order.enums.OrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单展示对象，隔离 Entity 与接口输出 */
@Data
public class OrderVO {

    private Long id;
    private String orderNo;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private String status;
    private String statusLabel;
    private String shippingAddress;
    private LocalDateTime createdAt;

    public static OrderVO from(OrderEntity entity) {
        OrderVO vo = new OrderVO();
        vo.setId(entity.getId());
        vo.setOrderNo(entity.getOrderNo());
        vo.setProductName(entity.getProductName());
        vo.setQuantity(entity.getQuantity());
        vo.setUnitPrice(entity.getUnitPrice());
        vo.setTotalAmount(entity.getTotalAmount());
        vo.setStatus(entity.getStatus());
        vo.setStatusLabel(labelOf(entity.getStatus()));
        vo.setShippingAddress(entity.getShippingAddress());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }

    private static String labelOf(String status) {
        try {
            return OrderStatus.of(status).getLabel();
        } catch (IllegalArgumentException ex) {
            return status;
        }
    }
}
