-- 演示数据：两个用户及其订单，用于验证数据隔离与统计功能

INSERT INTO users (user_id) VALUES ('demo-user-a'), ('demo-user-b');

-- 用户 A 的订单：覆盖多种状态与不同时间
INSERT INTO orders (order_no, owner_user_id, product_name, quantity, unit_price, total_amount, status, shipping_address, created_at) VALUES
('ORD10001', 'demo-user-a', '无线键盘',   2,  99.00,  198.00,  'PENDING_PAYMENT',  '北京市海淀区中关村大街 1 号',   NOW()),
('ORD10002', 'demo-user-a', '机械鼠标',   1,  149.00, 149.00,  'PENDING_SHIPMENT', '北京市海淀区中关村大街 1 号',   DATE_SUB(NOW(), INTERVAL 1 DAY)),
('ORD10003', 'demo-user-a', '显示器支架', 1,  199.00, 199.00,  'SHIPPED',          '北京市朝阳区望京 SOHO',         DATE_SUB(NOW(), INTERVAL 3 DAY)),
('ORD10004', 'demo-user-a', 'USB 集线器', 3,  45.50,  136.50,  'CANCELLED',        '北京市朝阳区望京 SOHO',         DATE_SUB(NOW(), INTERVAL 10 DAY)),
('ORD10005', 'demo-user-a', '降噪耳机',   1,  899.00, 899.00,  'PAID',             '北京市海淀区中关村大街 1 号',   DATE_SUB(NOW(), INTERVAL 20 DAY));

-- 用户 B 的订单
INSERT INTO orders (order_no, owner_user_id, product_name, quantity, unit_price, total_amount, status, shipping_address, created_at) VALUES
('ORD20001', 'demo-user-b', '人体工学椅', 1, 1299.00, 1299.00, 'PENDING_SHIPMENT', '上海市浦东新区张江高科技园区', NOW()),
('ORD20002', 'demo-user-b', '升降桌',     1, 1899.00, 1899.00, 'SHIPPED',          '上海市浦东新区张江高科技园区', DATE_SUB(NOW(), INTERVAL 5 DAY)),
('ORD20003', 'demo-user-b', '屏幕挂灯',   2,  169.00,  338.00,  'PENDING_PAYMENT',  '上海市徐汇区漕河泾开发区',     DATE_SUB(NOW(), INTERVAL 2 DAY));
