package com.example.orderai.ai.tool;

import com.example.orderai.ai.model.ToolDefinition;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 工具目录：工具名称、Schema 与白名单的唯一来源。
 * Schema 中不包含 owner_user_id，用户身份只能来自后端上下文。
 */
public final class AiToolCatalog {

    public static final String QUERY_ORDERS = "queryOrders";
    public static final String CREATE_ORDER = "createOrder";
    public static final String UPDATE_ORDER = "updateOrder";
    public static final String CANCEL_ORDER = "cancelOrder";
    public static final String SHIP_ORDER = "shipOrder";
    public static final String STATISTICS_ORDERS = "statisticsOrders";

    private static final Set<String> SUPPORTED = Set.of(
            QUERY_ORDERS, CREATE_ORDER, UPDATE_ORDER, CANCEL_ORDER, SHIP_ORDER, STATISTICS_ORDERS);

    /** 写工具：AI 识别到用户明确执行意图后直接执行 */
    private static final Set<String> WRITE_TOOLS = Set.of(
            CREATE_ORDER, UPDATE_ORDER, CANCEL_ORDER, SHIP_ORDER);

    private static final String STATUS_DESCRIPTION =
            "订单状态代码：PENDING_PAYMENT(待付款)、PAID(已付款)、PENDING_SHIPMENT(待发货)、"
                    + "SHIPPED(已发货)、CANCELLED(已取消)；不填表示不限";

    private static final String DATE_DESCRIPTION = "日期，格式 yyyy-MM-dd";

    private static final List<ToolDefinition> DEFINITIONS = List.of(
            ToolDefinition.of(QUERY_ORDERS,
                    "查询当前用户的订单列表，支持按订单编号、状态、商品名称和创建日期范围筛选，返回分页结果",
                    objectSchema(Map.of(
                            "orderNo", Map.of("type", "string", "description", "订单编号，支持部分匹配"),
                            "status", Map.of("type", "string", "description", STATUS_DESCRIPTION),
                            "productName", Map.of("type", "string", "description", "商品名称，支持模糊匹配"),
                            "startDate", Map.of("type", "string", "description", DATE_DESCRIPTION + "，创建日期起"),
                            "endDate", Map.of("type", "string", "description", DATE_DESCRIPTION + "，创建日期止"),
                            "pageNo", Map.of("type", "integer", "minimum", 1, "description", "页码，默认 1"),
                            "pageSize", Map.of("type", "integer", "minimum", 1, "maximum", 20,
                                    "description", "每页数量，默认 10，最多 20")
                    ), List.of())),

            ToolDefinition.of(CREATE_ORDER,
                    "创建一笔新订单，订单初始状态为待付款；需要用户明确给出商品名称、数量和单价",
                    objectSchema(Map.of(
                            "productName", Map.of("type", "string", "description", "商品名称"),
                            "quantity", Map.of("type", "integer", "minimum", 1, "description", "购买数量"),
                            "unitPrice", Map.of("type", "number", "minimum", 0,
                                    "description", "商品单价，最多两位小数"),
                            "shippingAddress", Map.of("type", "string", "description", "收货地址")
                    ), List.of("productName", "quantity", "unitPrice"))),

            ToolDefinition.of(UPDATE_ORDER,
                    "修改已存在订单的商品名称、数量、单价或收货地址；已发货或已取消的订单不能修改",
                    objectSchema(Map.of(
                            "orderNo", Map.of("type", "string", "description", "要修改的订单编号"),
                            "productName", Map.of("type", "string", "description", "新的商品名称"),
                            "quantity", Map.of("type", "integer", "minimum", 1, "description", "新的购买数量"),
                            "unitPrice", Map.of("type", "number", "minimum", 0,
                                    "description", "新的商品单价，最多两位小数"),
                            "shippingAddress", Map.of("type", "string", "description", "新的收货地址")
                    ), List.of("orderNo"))),

            ToolDefinition.of(CANCEL_ORDER,
                    "取消当前用户的订单；已发货或已取消的订单不能取消",
                    objectSchema(Map.of(
                            "orderNo", Map.of("type", "string", "description", "要取消的订单编号")
                    ), List.of("orderNo"))),

            ToolDefinition.of(SHIP_ORDER,
                    "将已付款或待发货的订单标记为已发货",
                    objectSchema(Map.of(
                            "orderNo", Map.of("type", "string", "description", "要发货的订单编号")
                    ), List.of("orderNo"))),

            ToolDefinition.of(STATISTICS_ORDERS,
                    "统计当前用户的订单概览：订单总数、今日与本月订单数、累计金额、待付款与待发货数量，以及各状态数量",
                    objectSchema(Map.of(), List.of()))
    );

    private AiToolCatalog() {
    }

    public static boolean isSupported(String name) {
        return name != null && SUPPORTED.contains(name);
    }

    public static boolean isWriteTool(String name) {
        return name != null && WRITE_TOOLS.contains(name);
    }

    public static List<ToolDefinition> definitions() {
        return DEFINITIONS;
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties, "required", required);
    }
}
