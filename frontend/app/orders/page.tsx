"use client";

import { useEffect, useState } from "react";
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import {
  AccountBookOutlined,
  CalendarOutlined,
  CarOutlined,
  ClockCircleOutlined,
  DollarOutlined,
  SearchOutlined,
  UnorderedListOutlined,
} from "@ant-design/icons";
import { ensureUser } from "@/lib/user";
import {
  fetchOrderDetail,
  fetchOrders,
  fetchStatistics,
  Order,
  OrderQuery,
  STATUS_META,
  STATUS_OPTIONS,
  StatisticsOverview,
} from "@/lib/orders";
import OrderStatusChart from "@/components/OrderStatusChart";

const { Title, Text } = Typography;

function formatDateTime(value?: string): string {
  if (!value) {
    return "-";
  }
  return value.replace("T", " ").slice(0, 19);
}

function formatMoney(value?: number): string {
  return `¥${Number(value ?? 0).toFixed(2)}`;
}

export default function OrdersPage() {
  const { message } = App.useApp();
  const [userId, setUserId] = useState("");
  const [userError, setUserError] = useState("");

  const [statistics, setStatistics] = useState<StatisticsOverview>();
  const [statLoading, setStatLoading] = useState(true);

  const [records, setRecords] = useState<Order[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [orderNo, setOrderNo] = useState("");
  const [status, setStatus] = useState<string>();
  const [listLoading, setListLoading] = useState(true);

  const [detail, setDetail] = useState<Order>();
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);

  const [form] = Form.useForm();

  // 确保当前浏览器已有匿名用户后再加载数据
  useEffect(() => {
    ensureUser()
      .then(setUserId)
      .catch(() => setUserError("用户初始化失败，请确认后端服务已启动"));
  }, []);

  // 统计信息与当前用户绑定，切换用户或首次加载时刷新
  useEffect(() => {
    if (!userId) {
      return;
    }
    let cancelled = false;
    setStatLoading(true);
    fetchStatistics()
      .then((data) => {
        if (!cancelled) {
          setStatistics(data);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          message.error(err instanceof Error ? err.message : "统计信息加载失败");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setStatLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [userId]);

  // 订单列表：搜索条件、状态筛选与分页变化时重新请求
  useEffect(() => {
    if (!userId) {
      return;
    }
    let cancelled = false;
    setListLoading(true);
    const query: OrderQuery = {
      pageNo,
      pageSize,
      ...(orderNo ? { orderNo } : {}),
      ...(status ? { status } : {}),
    };
    fetchOrders(query)
      .then((data) => {
        if (!cancelled) {
          setRecords(data.records);
          setTotal(data.total);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          message.error(err instanceof Error ? err.message : "订单列表加载失败");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setListLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [userId, pageNo, pageSize, orderNo, status]);

  const handleSearch = (values: { orderNo?: string; status?: string }) => {
    setOrderNo(values.orderNo?.trim() ?? "");
    setStatus(values.status);
    setPageNo(1);
  };

  const handleReset = () => {
    form.resetFields();
    setOrderNo("");
    setStatus(undefined);
    setPageNo(1);
  };

  const openDetail = async (order: Order) => {
    setDetail(order);
    setDetailOpen(true);
    setDetailLoading(true);
    try {
      setDetail(await fetchOrderDetail(order.id));
    } catch (err) {
      message.error(err instanceof Error ? err.message : "订单详情加载失败");
    } finally {
      setDetailLoading(false);
    }
  };

  const statCards = [
    {
      key: "totalOrders",
      title: "订单总数",
      value: statistics?.totalOrders ?? 0,
      icon: <UnorderedListOutlined />,
      color: "#1677ff",
    },
    {
      key: "todayOrders",
      title: "今日订单数",
      value: statistics?.todayOrders ?? 0,
      icon: <CalendarOutlined />,
      color: "#52c41a",
    },
    {
      key: "monthOrders",
      title: "本月订单数",
      value: statistics?.monthOrders ?? 0,
      icon: <ClockCircleOutlined />,
      color: "#13c2c2",
    },
    {
      key: "totalAmount",
      title: "累计订单金额",
      value: statistics?.totalAmount ?? 0,
      prefix: "¥",
      precision: 2,
      icon: <DollarOutlined />,
      color: "#fa8c16",
    },
    {
      key: "pendingPaymentCount",
      title: "待付款数量",
      value: statistics?.pendingPaymentCount ?? 0,
      icon: <AccountBookOutlined />,
      color: "#faad14",
    },
    {
      key: "pendingShipmentCount",
      title: "待发货数量",
      value: statistics?.pendingShipmentCount ?? 0,
      icon: <CarOutlined />,
      color: "#722ed1",
    },
  ];

  const columns: ColumnsType<Order> = [
    {
      title: "订单编号",
      dataIndex: "orderNo",
      width: 200,
      render: (value: string) => <span style={{ whiteSpace: "nowrap" }}>{value}</span>,
    },
    { title: "商品名称", dataIndex: "productName", width: 130, ellipsis: true },
    { title: "数量", dataIndex: "quantity", width: 70, align: "right" },
    {
      title: "单价",
      dataIndex: "unitPrice",
      width: 100,
      align: "right",
      render: (value: number) => formatMoney(value),
    },
    {
      title: "总金额",
      dataIndex: "totalAmount",
      width: 110,
      align: "right",
      render: (value: number) => <Text strong>{formatMoney(value)}</Text>,
    },
    {
      title: "状态",
      dataIndex: "status",
      width: 100,
      render: (value: string, record) => {
        const meta = STATUS_META[value];
        return <Tag color={meta?.tagColor ?? "default"}>{record.statusLabel || meta?.label || value}</Tag>;
      },
    },
    { title: "收货地址", dataIndex: "shippingAddress", width: 200, ellipsis: true },
    {
      title: "创建时间",
      dataIndex: "createdAt",
      width: 180,
      render: (value: string) => formatDateTime(value),
    },
    {
      title: "操作",
      key: "action",
      width: 80,
      fixed: "right",
      render: (_, record) => (
        <Button type="link" size="small" onClick={() => openDetail(record)}>
          详情
        </Button>
      ),
    },
  ];

  const detailTag = detail ? (
    <Tag color={STATUS_META[detail.status]?.tagColor ?? "default"}>
      {detail.statusLabel || STATUS_META[detail.status]?.label || detail.status}
    </Tag>
  ) : (
    "-"
  );

  return (
    <main className="orders-page">
      <div style={{ marginBottom: 20 }}>
        <Title level={4} style={{ margin: 0 }}>
          订单管理
        </Title>
        <Text type="secondary" className="orders-subtitle">
          仅展示当前匿名用户的订单与统计数据{userId ? `（用户 ${userId}）` : ""}
        </Text>
      </div>

      {userError && <Alert type="error" showIcon message={userError} style={{ marginBottom: 16 }} />}

      <Row gutter={[16, 16]}>
        {statCards.map((card) => (
          <Col key={card.key} xs={12} sm={12} lg={8} xl={4}>
            <Card loading={statLoading} className="orders-stat-card">
              <Statistic
                title={
                  <Space size={6}>
                    <span style={{ color: card.color }}>{card.icon}</span>
                    <span>{card.title}</span>
                  </Space>
                }
                value={card.value}
                precision={card.precision}
                prefix={card.prefix}
              />
            </Card>
          </Col>
        ))}
      </Row>

      <Card title="订单状态分布" styles={{ body: { padding: "8px 16px" } }} style={{ marginTop: 16 }}>
        <div className="order-chart-wrap">
          <OrderStatusChart counts={statistics?.statusCounts ?? {}} />
        </div>
      </Card>

      <Card className="orders-card" style={{ marginTop: 16 }}>
        <Form
          form={form}
          className="orders-search"
          layout="inline"
          onFinish={handleSearch}
          style={{ gap: 12, rowGap: 12 }}
        >
          <Form.Item name="orderNo" label="订单编号" className="orders-search-item">
            <Input
              allowClear
              placeholder="请输入订单编号"
              style={{ width: "100%" }}
              prefix={<SearchOutlined />}
            />
          </Form.Item>
          <Form.Item
            name="status"
            label="订单状态"
            className="orders-search-item orders-search-item--status"
          >
            <Select
              allowClear
              placeholder="全部状态"
              options={STATUS_OPTIONS}
              style={{ width: "100%" }}
            />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                查询
              </Button>
              <Button onClick={handleReset}>重置</Button>
            </Space>
          </Form.Item>
        </Form>

        <Table<Order>
          className="orders-table"
          style={{ marginTop: 16 }}
          rowKey="id"
          columns={columns}
          dataSource={records}
          loading={listLoading}
          scroll={{ x: 1170 }}
          locale={{ emptyText: <Empty description="暂无订单数据" /> }}
          pagination={{
            current: pageNo,
            pageSize,
            total,
            showSizeChanger: true,
            pageSizeOptions: ["10", "20", "50"],
            showTotal: (count) => `共 ${count} 条`,
            onChange: (page, size) => {
              setPageNo(page);
              setPageSize(size);
            },
          }}
        />
      </Card>

      <Drawer
        title="订单详情"
        width={520}
        rootClassName="orders-detail-drawer"
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
      >
        {detailLoading ? (
          <Card loading />
        ) : (
          <Descriptions
            column={1}
            bordered
            size="small"
            items={[
              { key: "orderNo", label: "订单编号", children: detail?.orderNo ?? "-" },
              { key: "productName", label: "商品名称", children: detail?.productName ?? "-" },
              { key: "quantity", label: "数量", children: detail?.quantity ?? "-" },
              { key: "unitPrice", label: "单价", children: formatMoney(detail?.unitPrice) },
              { key: "totalAmount", label: "总金额", children: formatMoney(detail?.totalAmount) },
              { key: "status", label: "订单状态", children: detailTag },
              { key: "shippingAddress", label: "收货地址", children: detail?.shippingAddress ?? "-" },
              { key: "createdAt", label: "创建时间", children: formatDateTime(detail?.createdAt) },
            ]}
          />
        )}
      </Drawer>
    </main>
  );
}
