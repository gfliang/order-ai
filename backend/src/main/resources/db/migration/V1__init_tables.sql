-- users 用户表与 orders 订单表初始化

CREATE TABLE users (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    user_id     VARCHAR(64)  NOT NULL COMMENT '对外唯一用户标识',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '匿名用户表';

CREATE TABLE orders (
    id               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    order_no         VARCHAR(64)    NOT NULL COMMENT '订单编号',
    owner_user_id    VARCHAR(64)    NOT NULL COMMENT '所属用户 ID',
    product_name     VARCHAR(200)   NOT NULL COMMENT '商品名称',
    quantity         INT            NOT NULL COMMENT '商品数量',
    unit_price       DECIMAL(12, 2) NOT NULL COMMENT '商品单价',
    total_amount     DECIMAL(12, 2) NOT NULL COMMENT '订单总金额',
    status           VARCHAR(32)    NOT NULL COMMENT '订单状态',
    shipping_address VARCHAR(500)   DEFAULT NULL COMMENT '收货地址',
    created_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_order_no (order_no),
    KEY idx_orders_owner_status (owner_user_id, status),
    KEY idx_orders_owner_created (owner_user_id, created_at),
    CONSTRAINT fk_orders_owner_user FOREIGN KEY (owner_user_id) REFERENCES users (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单表';
