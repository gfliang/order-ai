package com.example.orderai.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("orders")
public class OrderEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private String ownerUserId;

    private String productName;

    private Integer quantity;

    private BigDecimal unitPrice;

    private BigDecimal totalAmount;

    private String status;

    private String shippingAddress;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
