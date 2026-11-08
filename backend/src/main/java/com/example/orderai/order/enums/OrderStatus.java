package com.example.orderai.order.enums;

import lombok.Getter;

/** 订单状态与允许的状态转换 */
@Getter
public enum OrderStatus {

    PENDING_PAYMENT("待付款"),
    PAID("已付款"),
    PENDING_SHIPMENT("待发货"),
    SHIPPED("已发货"),
    CANCELLED("已取消");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public static OrderStatus of(String code) {
        for (OrderStatus status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知订单状态: " + code);
    }

    /** 未发货的订单可以取消；已发货、已取消为终态 */
    public boolean cancellable() {
        return this == PENDING_PAYMENT || this == PAID || this == PENDING_SHIPMENT;
    }

    /** 已付款或待发货的订单可以发货 */
    public boolean shippable() {
        return this == PAID || this == PENDING_SHIPMENT;
    }
}
