package com.example.orderai.ai.tool;

import com.example.orderai.ai.model.ToolCall;
import com.example.orderai.common.exception.BusinessException;
import com.example.orderai.order.dto.CreateOrderRequest;
import com.example.orderai.order.dto.OrderQueryRequest;
import com.example.orderai.order.dto.UpdateOrderRequest;
import com.example.orderai.order.enums.OrderStatus;
import com.example.orderai.order.service.OrderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 工具执行层：按白名单分发工具调用，解析并校验模型参数。
 * 读工具直接查询；写工具在 AI 识别到明确执行意图后直接调用订单服务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiToolExecutor {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public ToolExecutionResult execute(ToolCall toolCall) {
        String name = toolCall == null || toolCall.getFunction() == null
                ? null : toolCall.getFunction().getName();
        String toolCallId = toolCall == null ? null : toolCall.getId();

        if (!AiToolCatalog.isSupported(name)) {
            // 模型请求了未声明的工具，直接拒绝，绝不执行
            log.warn("AI工具白名单拒绝 name={} toolCallId={}", name, toolCallId);
            return ToolExecutionResult.ofContent(
                    failure(40000, "不支持的工具调用：" + (name == null ? "未指定" : name)));
        }

        long startNanos = System.nanoTime();
        try {
            JsonNode args = readArguments(toolCall.getFunction().getArguments());
            Object data = AiToolCatalog.isWriteTool(name) ? writeResult(name, args) : readResult(name, args);
            log.info("AI工具执行成功 name={} toolCallId={} duration={}ms",
                    name, toolCallId, elapsedMillis(startNanos));
            return ToolExecutionResult.ofContent(success(data));
        } catch (BusinessException ex) {
            log.warn("AI工具业务失败 name={} code={} message={}",
                    name, ex.getCode(), ex.getMessage());
            return ToolExecutionResult.ofContent(failure(ex.getCode(), ex.getMessage()));
        } catch (Exception ex) {
            log.error("AI工具执行异常 name={}", name, ex);
            return ToolExecutionResult.ofContent(failure(50000, "工具执行失败，请稍后重试"));
        }
    }

    private Object readResult(String name, JsonNode args) {
        return switch (name) {
            case AiToolCatalog.QUERY_ORDERS -> orderService.page(buildQuery(args));
            case AiToolCatalog.STATISTICS_ORDERS -> orderService.overview();
            default -> throw BusinessException.badRequest("不支持的工具调用：" + name);
        };
    }

    /** 写工具直接执行；归属与状态规则由 OrderService 校验 */
    private Object writeResult(String name, JsonNode args) {
        return switch (name) {
            case AiToolCatalog.CREATE_ORDER -> orderService.create(buildCreate(args));
            case AiToolCatalog.UPDATE_ORDER -> {
                Long id = orderService.findOrderIdByNo(requireOrderNo(args));
                yield orderService.update(id, buildUpdate(args));
            }
            case AiToolCatalog.CANCEL_ORDER ->
                    orderService.cancel(orderService.findOrderIdByNo(requireOrderNo(args)));
            case AiToolCatalog.SHIP_ORDER ->
                    orderService.ship(orderService.findOrderIdByNo(requireOrderNo(args)));
            default -> throw BusinessException.badRequest("不支持的工具调用：" + name);
        };
    }

    private OrderQueryRequest buildQuery(JsonNode args) {
        OrderQueryRequest request = new OrderQueryRequest();
        request.setOrderNo(text(args, "orderNo"));
        request.setStatus(normalizeStatus(text(args, "status")));
        request.setProductName(text(args, "productName"));
        request.setStartDate(date(args, "startDate"));
        request.setEndDate(date(args, "endDate"));

        Integer pageNo = integer(args, "pageNo");
        if (pageNo != null) {
            request.setPageNo(pageNo);
        }
        Integer pageSize = integer(args, "pageSize");
        if (pageSize != null) {
            if (pageSize > 20) {
                throw BusinessException.badRequest("每页查询数量不能超过 20");
            }
            request.setPageSize(pageSize);
        }
        return validate(request);
    }

    private CreateOrderRequest buildCreate(JsonNode args) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setProductName(text(args, "productName"));
        request.setQuantity(integer(args, "quantity"));
        request.setUnitPrice(decimal(args, "unitPrice"));
        request.setShippingAddress(text(args, "shippingAddress"));
        return validate(request);
    }

    private UpdateOrderRequest buildUpdate(JsonNode args) {
        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setProductName(text(args, "productName"));
        request.setQuantity(integer(args, "quantity"));
        request.setUnitPrice(decimal(args, "unitPrice"));
        request.setShippingAddress(text(args, "shippingAddress"));
        if (request.isEmpty()) {
            throw BusinessException.badRequest("至少需要修改一个字段");
        }
        return validate(request);
    }

    private <T> T validate(T value) {
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (violations.isEmpty()) {
            return value;
        }
        String message = violations.stream()
                .map(ConstraintViolation::getMessage)
                .sorted()
                .collect(Collectors.joining("；"));
        throw BusinessException.badRequest(message);
    }

    private JsonNode readArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(arguments);
        } catch (JsonProcessingException ex) {
            throw BusinessException.badRequest("工具参数不是有效的 JSON");
        }
    }

    private String requireOrderNo(JsonNode args) {
        String orderNo = text(args, "orderNo");
        if (orderNo == null || orderNo.isBlank()) {
            throw BusinessException.badRequest("订单编号不能为空");
        }
        return orderNo;
    }

    private String normalizeStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (OrderStatus status : OrderStatus.values()) {
            if (status.name().equalsIgnoreCase(raw) || status.getLabel().equals(raw)) {
                return status.name();
            }
        }
        throw BusinessException.badRequest("无效的订单状态：" + raw);
    }

    private LocalDate date(JsonNode args, String field) {
        String raw = text(args, field);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException ex) {
            throw BusinessException.badRequest(field + " 日期格式应为 yyyy-MM-dd");
        }
    }

    private String text(JsonNode args, String field) {
        JsonNode value = args.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() && !value.isNumber()) {
            throw BusinessException.badRequest(field + " 必须是字符串");
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private Integer integer(JsonNode args, String field) {
        JsonNode value = args.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            try {
                return value.decimalValue().intValueExact();
            } catch (ArithmeticException ex) {
                throw BusinessException.badRequest(field + " 必须是整数");
            }
        }
        if (value.isTextual()) {
            try {
                return new BigDecimal(value.asText().trim()).intValueExact();
            } catch (NumberFormatException | ArithmeticException ex) {
                throw BusinessException.badRequest(field + " 必须是整数");
            }
        }
        throw BusinessException.badRequest(field + " 必须是整数");
    }

    private BigDecimal decimal(JsonNode args, String field) {
        JsonNode value = args.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.decimalValue();
        }
        if (value.isTextual()) {
            try {
                return new BigDecimal(value.asText().trim());
            } catch (NumberFormatException ex) {
                throw BusinessException.badRequest(field + " 必须是数字");
            }
        }
        throw BusinessException.badRequest(field + " 必须是数字");
    }

    private String success(Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", data);
        return toJson(body);
    }

    private String failure(int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", error);
        return toJson(body);
    }

    private String toJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            log.error("工具结果序列化失败", ex);
            return "{\"success\":false,\"error\":{\"code\":50000,\"message\":\"工具结果序列化失败\"}}";
        }
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
