package com.example.orderai.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** 订单列表查询条件 */
@Data
public class OrderQueryRequest {

    @Min(value = 1, message = "页码必须大于 0")
    private Integer pageNo = 1;

    @Min(value = 1, message = "每页数量必须大于 0")
    @Max(value = 100, message = "每页数量不能超过 100")
    private Integer pageSize = 10;

    private String orderNo;

    private String status;

    private String productName;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;
}
