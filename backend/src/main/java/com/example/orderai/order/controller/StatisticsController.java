package com.example.orderai.order.controller;

import com.example.orderai.common.response.ApiResponse;
import com.example.orderai.order.dto.StatisticsOverviewVO;
import com.example.orderai.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final OrderService orderService;

    /** 当前用户订单统计概览 */
    @GetMapping("/overview")
    public ApiResponse<StatisticsOverviewVO> overview() {
        return ApiResponse.ok(orderService.overview());
    }
}
