package com.example.orderai.order.controller;

import com.example.orderai.common.response.ApiResponse;
import com.example.orderai.order.dto.*;
import com.example.orderai.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** 查询当前用户订单列表（分页、搜索、状态筛选） */
    @GetMapping
    public ApiResponse<PageResult<OrderVO>> page(@Valid OrderQueryRequest request) {
        return ApiResponse.ok(orderService.page(request));
    }

    /** 查询当前用户订单详情 */
    @GetMapping("/{id}")
    public ApiResponse<OrderVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(orderService.detail(id));
    }

    /** 创建订单 */
    @PostMapping
    public ApiResponse<OrderVO> create(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.ok(orderService.create(request));
    }

    /** 修改当前用户订单 */
    @PutMapping("/{id}")
    public ApiResponse<OrderVO> update(@PathVariable Long id,
                                       @Valid @RequestBody UpdateOrderRequest request) {
        return ApiResponse.ok(orderService.update(id, request));
    }

    /** 取消当前用户订单 */
    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderVO> cancel(@PathVariable Long id) {
        return ApiResponse.ok(orderService.cancel(id));
    }

    /** 确认发货 */
    @PostMapping("/{id}/ship")
    public ApiResponse<OrderVO> ship(@PathVariable Long id) {
        return ApiResponse.ok(orderService.ship(id));
    }
}
