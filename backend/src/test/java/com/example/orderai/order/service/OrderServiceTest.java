package com.example.orderai.order.service;

import com.example.orderai.common.context.UserContext;
import com.example.orderai.common.exception.BusinessException;
import com.example.orderai.order.dto.CreateOrderRequest;
import com.example.orderai.order.dto.OrderVO;
import com.example.orderai.order.dto.UpdateOrderRequest;
import com.example.orderai.order.entity.OrderEntity;
import com.example.orderai.order.mapper.OrderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final String CURRENT_USER = "user-a";

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        UserContext.setUserId(CURRENT_USER);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void createOrderCalculatesAmountAndInjectsOwner() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setProductName("无线键盘");
        request.setQuantity(2);
        request.setUnitPrice(new BigDecimal("99.00"));

        OrderVO vo = orderService.create(request);

        assertThat(vo.getTotalAmount()).isEqualByComparingTo("198.00");
        assertThat(vo.getStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(vo.getStatusLabel()).isEqualTo("待付款");

        ArgumentCaptor<OrderEntity> captor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderMapper).insert(captor.capture());
        // owner_user_id 必须来自后端用户上下文
        assertThat(captor.getValue().getOwnerUserId()).isEqualTo(CURRENT_USER);
        assertThat(captor.getValue().getOrderNo()).startsWith("ORD");
    }

    @Test
    void updateOrderRecalculatesTotalAmount() {
        OrderEntity existing = orderEntity(1L, "PENDING_PAYMENT", 2, "99.00");
        when(orderMapper.selectOne(any())).thenReturn(existing);
        when(orderMapper.updateById(any(OrderEntity.class))).thenReturn(1);

        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setQuantity(3);

        OrderVO vo = orderService.update(1L, request);

        assertThat(vo.getQuantity()).isEqualTo(3);
        assertThat(vo.getTotalAmount()).isEqualByComparingTo("297.00");
    }

    @Test
    void updateShippedOrderIsRejected() {
        when(orderMapper.selectOne(any())).thenReturn(orderEntity(1L, "SHIPPED", 1, "10.00"));

        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setQuantity(5);

        assertThatThrownBy(() -> orderService.update(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许修改");
    }

    @Test
    void cancelShippedOrderIsRejected() {
        when(orderMapper.selectOne(any())).thenReturn(orderEntity(1L, "SHIPPED", 1, "10.00"));

        assertThatThrownBy(() -> orderService.cancel(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许取消");
    }

    @Test
    void shipCancelledOrderIsRejected() {
        when(orderMapper.selectOne(any())).thenReturn(orderEntity(1L, "CANCELLED", 1, "10.00"));

        assertThatThrownBy(() -> orderService.ship(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许发货");
    }

    @Test
    void shipPendingShipmentOrderSucceeds() {
        when(orderMapper.selectOne(any())).thenReturn(orderEntity(1L, "PENDING_SHIPMENT", 1, "10.00"));
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        OrderVO vo = orderService.ship(1L);

        assertThat(vo.getStatus()).isEqualTo("SHIPPED");
    }

    @Test
    void detailOfOtherUserOrderIsTreatedAsNotFound() {
        // 其他用户的订单在带 owner 条件查询下返回 null
        when(orderMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> orderService.detail(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("订单不存在");
    }

    private OrderEntity orderEntity(Long id, String status, int quantity, String unitPrice) {
        OrderEntity entity = new OrderEntity();
        entity.setId(id);
        entity.setOwnerUserId(CURRENT_USER);
        entity.setProductName("测试商品");
        entity.setQuantity(quantity);
        entity.setUnitPrice(new BigDecimal(unitPrice));
        entity.setTotalAmount(new BigDecimal(unitPrice).multiply(BigDecimal.valueOf(quantity)));
        entity.setStatus(status);
        return entity;
    }
}
