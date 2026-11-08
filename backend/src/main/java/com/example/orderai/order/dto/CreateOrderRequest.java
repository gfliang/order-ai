package com.example.orderai.order.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/** 创建订单请求 */
@Data
public class CreateOrderRequest {

    @NotBlank(message = "商品名称不能为空")
    @Size(max = 200, message = "商品名称长度不能超过 200")
    private String productName;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量必须大于 0")
    @Max(value = 100000, message = "数量超出允许范围")
    private Integer quantity;

    @NotNull(message = "单价不能为空")
    @DecimalMin(value = "0.00", message = "单价不能为负数")
    @Digits(integer = 10, fraction = 2, message = "单价格式不正确")
    private BigDecimal unitPrice;

    @Size(max = 500, message = "收货地址长度不能超过 500")
    private String shippingAddress;
}
