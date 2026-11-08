import type { AxiosResponse } from "axios";
import api from "./api";

interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface Order {
  id: number;
  orderNo: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  totalAmount: number;
  status: string;
  statusLabel: string;
  shippingAddress: string;
  createdAt: string;
}

export interface OrderPage {
  records: Order[];
  total: number;
  pageNo: number;
  pageSize: number;
}

export interface StatisticsOverview {
  totalOrders: number;
  todayOrders: number;
  monthOrders: number;
  totalAmount: number;
  pendingPaymentCount: number;
  pendingShipmentCount: number;
  statusCounts: Record<string, number>;
}

export interface OrderQuery {
  pageNo: number;
  pageSize: number;
  orderNo?: string;
  status?: string;
}

/** 订单状态展示元数据，与后端 OrderStatus 枚举一一对应 */
export const STATUS_META: Record<string, { label: string; tagColor: string; hex: string }> = {
  PENDING_PAYMENT: { label: "待付款", tagColor: "gold", hex: "#faad14" },
  PAID: { label: "已付款", tagColor: "blue", hex: "#1677ff" },
  PENDING_SHIPMENT: { label: "待发货", tagColor: "cyan", hex: "#13c2c2" },
  SHIPPED: { label: "已发货", tagColor: "green", hex: "#52c41a" },
  CANCELLED: { label: "已取消", tagColor: "default", hex: "#bfbfbf" },
};

export const STATUS_OPTIONS = Object.entries(STATUS_META).map(([value, meta]) => ({
  value,
  label: meta.label,
}));

async function unwrap<T>(request: Promise<AxiosResponse<ApiResponse<T>>>): Promise<T> {
  const response = await request;
  if (response.data.code !== 0) {
    throw new Error(response.data.message || "请求失败");
  }
  return response.data.data;
}

export function fetchOrders(query: OrderQuery): Promise<OrderPage> {
  return unwrap(api.get<ApiResponse<OrderPage>>("/api/orders", { params: query }));
}

export function fetchOrderDetail(id: number): Promise<Order> {
  return unwrap(api.get<ApiResponse<Order>>(`/api/orders/${id}`));
}

export function fetchStatistics(): Promise<StatisticsOverview> {
  return unwrap(api.get<ApiResponse<StatisticsOverview>>("/api/statistics/overview"));
}
