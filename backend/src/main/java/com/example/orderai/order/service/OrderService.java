package com.example.orderai.order.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.orderai.common.context.UserContext;
import com.example.orderai.common.exception.BusinessException;
import com.example.orderai.order.dto.*;
import com.example.orderai.order.entity.OrderEntity;
import com.example.orderai.order.enums.OrderStatus;
import com.example.orderai.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 订单业务层：用户数据隔离、业务规则、金额计算、事务与数据库读写。
 * 不依赖任何 AI 模型供应商。
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final DateTimeFormatter ORDER_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final OrderMapper orderMapper;

    @Transactional
    public OrderVO create(CreateOrderRequest request) {
        String userId = UserContext.requireUserId();

        OrderEntity entity = new OrderEntity();
        entity.setOrderNo(generateOrderNo());
        // owner_user_id 只由后端从当前用户上下文注入
        entity.setOwnerUserId(userId);
        entity.setProductName(request.getProductName().trim());
        entity.setQuantity(request.getQuantity());
        entity.setUnitPrice(request.getUnitPrice());
        entity.setTotalAmount(calculateTotal(request.getQuantity(), request.getUnitPrice()));
        entity.setStatus(OrderStatus.PENDING_PAYMENT.name());
        entity.setShippingAddress(request.getShippingAddress());
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        orderMapper.insert(entity);
        return OrderVO.from(entity);
    }

    @Transactional
    public OrderVO update(Long id, UpdateOrderRequest request) {
        String userId = UserContext.requireUserId();
        if (request.isEmpty()) {
            throw BusinessException.badRequest("至少需要修改一个字段");
        }

        OrderEntity entity = findOwnedOrder(id, userId);
        OrderStatus status = OrderStatus.of(entity.getStatus());
        if (status == OrderStatus.SHIPPED || status == OrderStatus.CANCELLED) {
            throw BusinessException.badRequest("订单当前状态不允许修改：" + status.getLabel());
        }

        if (request.getProductName() != null) {
            entity.setProductName(request.getProductName().trim());
        }
        if (request.getQuantity() != null) {
            entity.setQuantity(request.getQuantity());
        }
        if (request.getUnitPrice() != null) {
            entity.setUnitPrice(request.getUnitPrice());
        }
        if (request.getShippingAddress() != null) {
            entity.setShippingAddress(request.getShippingAddress());
        }
        // 数量或单价变化后重新计算总金额
        entity.setTotalAmount(calculateTotal(entity.getQuantity(), entity.getUnitPrice()));
        entity.setUpdatedAt(LocalDateTime.now());

        orderMapper.updateById(entity);
        return OrderVO.from(entity);
    }

    @Transactional
    public OrderVO cancel(Long id) {
        String userId = UserContext.requireUserId();
        OrderEntity entity = findOwnedOrder(id, userId);
        OrderStatus status = OrderStatus.of(entity.getStatus());
        if (!status.cancellable()) {
            throw BusinessException.badRequest("订单当前状态不允许取消：" + status.getLabel());
        }
        changeStatus(entity, OrderStatus.CANCELLED, userId);
        return OrderVO.from(entity);
    }

    @Transactional
    public OrderVO ship(Long id) {
        String userId = UserContext.requireUserId();
        OrderEntity entity = findOwnedOrder(id, userId);
        OrderStatus status = OrderStatus.of(entity.getStatus());
        if (!status.shippable()) {
            throw BusinessException.badRequest("订单当前状态不允许发货：" + status.getLabel());
        }
        changeStatus(entity, OrderStatus.SHIPPED, userId);
        return OrderVO.from(entity);
    }

    public OrderVO detail(Long id) {
        return OrderVO.from(findOwnedOrder(id, UserContext.requireUserId()));
    }

    /** 按订单编号解析当前用户订单 ID；不存在或不属于当前用户时按不存在处理 */
    public Long findOrderIdByNo(String orderNo) {
        String userId = UserContext.requireUserId();
        OrderEntity entity = orderMapper.selectOne(new QueryWrapper<OrderEntity>()
                .eq("order_no", orderNo.trim())
                .eq("owner_user_id", userId));
        if (entity == null) {
            throw BusinessException.notFound("订单不存在");
        }
        return entity.getId();
    }

    public PageResult<OrderVO> page(OrderQueryRequest request) {
        String userId = UserContext.requireUserId();

        QueryWrapper<OrderEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", userId);
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            try {
                OrderStatus.of(request.getStatus());
            } catch (IllegalArgumentException ex) {
                throw BusinessException.badRequest("无效的订单状态：" + request.getStatus());
            }
            wrapper.eq("status", request.getStatus());
        }
        if (request.getOrderNo() != null && !request.getOrderNo().isBlank()) {
            wrapper.like("order_no", request.getOrderNo().trim());
        }
        if (request.getProductName() != null && !request.getProductName().isBlank()) {
            wrapper.like("product_name", request.getProductName().trim());
        }
        if (request.getStartDate() != null) {
            wrapper.ge("created_at", request.getStartDate().atStartOfDay());
        }
        if (request.getEndDate() != null) {
            wrapper.lt("created_at", request.getEndDate().plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc("created_at");

        Page<OrderEntity> page = orderMapper.selectPage(
                Page.of(request.getPageNo(), request.getPageSize()), wrapper);
        List<OrderVO> records = page.getRecords().stream()
                .map(OrderVO::from)
                .collect(Collectors.toList());
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public StatisticsOverviewVO overview() {
        String userId = UserContext.requireUserId();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        StatisticsOverviewVO vo = new StatisticsOverviewVO();
        vo.setTotalOrders(count(userId, null));
        vo.setTodayOrders(count(userId, todayStart));
        vo.setMonthOrders(count(userId, monthStart));
        vo.setTotalAmount(orderMapper.sumActiveAmount(userId));
        vo.setPendingPaymentCount(countByStatus(userId, OrderStatus.PENDING_PAYMENT));
        vo.setPendingShipmentCount(countByStatus(userId, OrderStatus.PENDING_SHIPMENT));
        vo.setStatusCounts(statusCounts(userId));
        return vo;
    }

    private OrderEntity findOwnedOrder(Long id, String userId) {
        OrderEntity entity = orderMapper.selectOne(new QueryWrapper<OrderEntity>()
                .eq("id", id)
                .eq("owner_user_id", userId));
        if (entity == null) {
            // 不属于当前用户时按不存在处理，不泄露其他用户数据
            throw BusinessException.notFound("订单不存在");
        }
        return entity;
    }

    /** 带当前状态条件的更新，避免并发或重复操作覆盖状态 */
    private void changeStatus(OrderEntity entity, OrderStatus target, String userId) {
        LocalDateTime now = LocalDateTime.now();
        int rows = orderMapper.update(null, new UpdateWrapper<OrderEntity>()
                .eq("id", entity.getId())
                .eq("owner_user_id", userId)
                .eq("status", entity.getStatus())
                .set("status", target.name())
                .set("updated_at", now));
        if (rows == 0) {
            throw BusinessException.badRequest("订单状态已发生变化，请刷新后重试");
        }
        entity.setStatus(target.name());
        entity.setUpdatedAt(now);
    }

    private long count(String userId, LocalDateTime since) {
        QueryWrapper<OrderEntity> wrapper = new QueryWrapper<OrderEntity>()
                .eq("owner_user_id", userId);
        if (since != null) {
            wrapper.ge("created_at", since);
        }
        return orderMapper.selectCount(wrapper);
    }

    private long countByStatus(String userId, OrderStatus status) {
        return orderMapper.selectCount(new QueryWrapper<OrderEntity>()
                .eq("owner_user_id", userId)
                .eq("status", status.name()));
    }

    private Map<String, Long> statusCounts(String userId) {
        QueryWrapper<OrderEntity> wrapper = new QueryWrapper<>();
        wrapper.select("status", "COUNT(*) AS cnt")
                .eq("owner_user_id", userId)
                .groupBy("status");
        List<Map<String, Object>> rows = orderMapper.selectMaps(wrapper);

        Map<String, Long> counts = new LinkedHashMap<>();
        for (OrderStatus status : OrderStatus.values()) {
            counts.put(status.name(), 0L);
        }
        rows.forEach(row -> {
            String statusKey = null;
            long count = 0L;
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if ("status".equalsIgnoreCase(entry.getKey())) {
                    statusKey = String.valueOf(entry.getValue());
                } else if (entry.getValue() instanceof Number number) {
                    count = number.longValue();
                }
            }
            if (statusKey != null) {
                counts.put(statusKey, count);
            }
        });
        return counts;
    }

    private BigDecimal calculateTotal(Integer quantity, BigDecimal unitPrice) {
        if (quantity == null || unitPrice == null) {
            throw BusinessException.badRequest("数量和单价不能为空");
        }
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    private String generateOrderNo() {
        return "ORD" + LocalDateTime.now().format(ORDER_NO_FORMAT)
                + ThreadLocalRandom.current().nextInt(100, 999);
    }
}
