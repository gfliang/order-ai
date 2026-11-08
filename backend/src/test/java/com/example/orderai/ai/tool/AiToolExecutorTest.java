package com.example.orderai.ai.tool;

import com.example.orderai.ai.model.ToolCall;
import com.example.orderai.common.exception.BusinessException;
import com.example.orderai.order.dto.OrderQueryRequest;
import com.example.orderai.order.dto.OrderVO;
import com.example.orderai.order.dto.PageResult;
import com.example.orderai.order.dto.StatisticsOverviewVO;
import com.example.orderai.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiToolExecutorTest {

    @Mock
    private OrderService orderService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private AiToolExecutor executor;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        executor = new AiToolExecutor(orderService, objectMapper, validator);
    }

    @Test
    void unknownToolIsRejectedWithoutExecution() {
        ToolExecutionResult result = executor.execute(toolCall("call-1", "deleteOrder", "{}"));

        assertTrue(result.getContent().contains("\"success\":false"));
        assertTrue(result.getContent().contains("不支持的工具调用"));
        verifyNoInteractions(orderService);
    }

    @Test
    void invalidCreateArgumentsAreRejectedBeforeExecution() {
        ToolExecutionResult result = executor.execute(toolCall("call-2", AiToolCatalog.CREATE_ORDER,
                "{\"productName\":\"机械键盘\",\"quantity\":0,\"unitPrice\":299.00}"));

        assertTrue(result.getContent().contains("\"success\":false"));
        assertTrue(result.getContent().contains("数量必须大于 0"));
        verifyNoInteractions(orderService);
    }

    @Test
    void queryOrdersMapsChineseStatusAndPaging() {
        when(orderService.page(any(OrderQueryRequest.class)))
                .thenReturn(PageResult.of(List.of(), 0, 1, 10));

        ToolExecutionResult result = executor.execute(toolCall("call-3", AiToolCatalog.QUERY_ORDERS,
                "{\"status\":\"待付款\",\"pageSize\":10}"));

        assertTrue(result.getContent().contains("\"success\":true"));
        ArgumentCaptor<OrderQueryRequest> captor = ArgumentCaptor.forClass(OrderQueryRequest.class);
        verify(orderService).page(captor.capture());
        OrderQueryRequest query = captor.getValue();
        assertEquals("PENDING_PAYMENT", query.getStatus());
        assertEquals(1, query.getPageNo());
        assertEquals(10, query.getPageSize());
    }

    @Test
    void queryPageSizeIsCapped() {
        ToolExecutionResult result = executor.execute(toolCall("call-4", AiToolCatalog.QUERY_ORDERS,
                "{\"pageSize\":50}"));

        assertTrue(result.getContent().contains("每页查询数量不能超过 20"));
        verifyNoInteractions(orderService);
    }

    @Test
    void writeToolExecutesDirectly() {
        when(orderService.findOrderIdByNo("ORD10001")).thenReturn(7L);
        OrderVO cancelled = new OrderVO();
        cancelled.setOrderNo("ORD10001");
        cancelled.setStatus("CANCELLED");
        when(orderService.cancel(7L)).thenReturn(cancelled);

        ToolExecutionResult result = executor.execute(toolCall("call-5", AiToolCatalog.CANCEL_ORDER,
                "{\"orderNo\":\"ORD10001\"}"));

        assertTrue(result.getContent().contains("\"success\":true"));
        assertTrue(result.getContent().contains("CANCELLED"));
        verify(orderService).cancel(7L);
    }

    @Test
    void writeBusinessErrorIsReturnedAsFailure() {
        when(orderService.findOrderIdByNo("ORD99999")).thenReturn(99L);
        when(orderService.cancel(99L)).thenThrow(BusinessException.notFound("订单不存在"));

        ToolExecutionResult result = executor.execute(toolCall("call-7", AiToolCatalog.CANCEL_ORDER,
                "{\"orderNo\":\"ORD99999\"}"));

        assertTrue(result.getContent().contains("\"success\":false"));
        assertTrue(result.getContent().contains("40400"));
        assertTrue(result.getContent().contains("订单不存在"));
        verify(orderService).cancel(99L);
    }

    @Test
    void malformedArgumentsAreReported() {
        ToolExecutionResult result = executor.execute(toolCall("call-8", AiToolCatalog.QUERY_ORDERS,
                "{bad json"));

        assertTrue(result.getContent().contains("工具参数不是有效的 JSON"));
        verifyNoInteractions(orderService);
    }

    @Test
    void missingOrderNoIsRejected() {
        ToolExecutionResult result = executor.execute(toolCall("call-9", AiToolCatalog.CANCEL_ORDER, "{}"));

        assertTrue(result.getContent().contains("订单编号不能为空"));
        verify(orderService, never()).cancel(any());
    }

    @Test
    void statisticsReturnsOverview() {
        when(orderService.overview()).thenReturn(new StatisticsOverviewVO());

        ToolExecutionResult result = executor.execute(toolCall("call-10",
                AiToolCatalog.STATISTICS_ORDERS, "{}"));

        assertTrue(result.getContent().contains("\"success\":true"));
        verify(orderService).overview();
    }

    private ToolCall toolCall(String id, String name, String arguments) {
        return new ToolCall(id, "function", new ToolCall.FunctionCall(name, arguments));
    }
}
