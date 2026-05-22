-- 秒杀系统数据库结构 v1.1.0
-- 新增：商品中心 (SPU + SKU)

-- ============================================================
-- SPU商品表
-- ============================================================
DROP TABLE IF EXISTS t_product;
CREATE TABLE t_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '商品ID',
    name VARCHAR(100) NOT NULL COMMENT '商品名称',
    category VARCHAR(50) COMMENT '分类',
    seckill_enabled TINYINT(1) DEFAULT 0 COMMENT '是否开启秒杀',
    seckill_start_time DATETIME COMMENT '秒杀开始时间',
    seckill_end_time DATETIME COMMENT '秒杀结束时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SPU商品表';

-- ============================================================
-- SKU规格表
-- ============================================================
DROP TABLE IF EXISTS t_product_sku;
CREATE TABLE t_product_sku (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'SKU ID',
    product_id BIGINT NOT NULL COMMENT '关联SPU',
    sku_name VARCHAR(100) NOT NULL COMMENT 'SKU名称',
    spec VARCHAR(50) COMMENT '规格',
    origin VARCHAR(50) COMMENT '产地',
    price DECIMAL(10,2) COMMENT '原价',
    seckill_price DECIMAL(10,2) COMMENT '秒杀价',
    stock INT DEFAULT 0 COMMENT '总库存',
    reserved_stock INT DEFAULT 0 COMMENT '预留库存',
    locked_stock INT DEFAULT 0 COMMENT '锁定库存',
    status TINYINT DEFAULT 1 COMMENT '状态 0-禁用 1-启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (product_id) REFERENCES t_product(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SKU规格表';

-- ============================================================
-- 订单表（原有）
-- ============================================================
DROP TABLE IF EXISTS t_order;
CREATE TABLE t_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) UNIQUE NOT NULL COMMENT '订单号',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    sku_id BIGINT COMMENT 'SKU ID',
    quantity INT DEFAULT 1 COMMENT '数量',
    status VARCHAR(20) DEFAULT 'SUCCESS' COMMENT '状态',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- ============================================================
-- 初始化测试数据：水果礼盒（5种水果7个SKU）
-- ============================================================
INSERT INTO t_product (id, name, category, seckill_enabled, seckill_start_time, seckill_end_time) VALUES
(1, '水果礼盒', '水果', 1, NOW(), DATE_ADD(NOW(), INTERVAL 2 HOUR));

INSERT INTO t_product_sku (id, product_id, sku_name, spec, origin, price, seckill_price, stock, reserved_stock, status) VALUES
(1, 1, '山东红富士苹果', '12个装', '山东', 88.00, 49.00, 100, 0, 1),
(2, 1, '山东红富士苹果', '24个装', '山东', 158.00, 89.00, 50, 0, 1),
(3, 1, '江西赣南脐橙', '12个装', '江西', 68.00, 39.00, 80, 0, 1),
(4, 1, '湖北秭归脐橙', '24个装', '湖北', 128.00, 69.00, 60, 0, 1),
(5, 1, '海南香蕉', '5斤装', '海南', 35.00, 19.00, 120, 0, 1),
(6, 1, '新疆葡萄', '5斤装', '新疆', 98.00, 59.00, 40, 0, 1),
(7, 1, '宁夏西瓜', '1个装', '宁夏', 58.00, 29.00, 50, 0, 1);