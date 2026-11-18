-- 首次启动 MySQL 容器时自动执行 (docker-entrypoint-initdb.d)
CREATE DATABASE IF NOT EXISTS mall_user    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mall_product DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mall_trade   DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- ===================== user =====================
USE mall_user;
CREATE TABLE IF NOT EXISTS t_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(64)  NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB;

-- ===================== product =====================
USE mall_product;
CREATE TABLE IF NOT EXISTS product (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    brand       VARCHAR(64)  NOT NULL,
    category    VARCHAR(32)  NOT NULL,
    model_no    VARCHAR(64)  NOT NULL COMMENT '商品型号, 知识库按它过滤检索',
    tags        VARCHAR(255) NULL COMMENT '用途标签, 逗号分隔',
    description TEXT         NULL,
    specs       JSON         NULL,
    status      TINYINT      NOT NULL DEFAULT 1,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_model_no (model_no),
    KEY idx_category (category),
    KEY idx_brand (brand)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS sku (
    id         BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT        NOT NULL,
    sku_name   VARCHAR(128)  NOT NULL,
    color      VARCHAR(32)   NULL,
    ram_gb     INT           NULL,
    storage_gb INT           NULL,
    price      DECIMAL(10,2) NOT NULL,
    stock      INT           NOT NULL DEFAULT 0,
    KEY idx_product (product_id),
    KEY idx_price (price)
) ENGINE = InnoDB;

-- 库存扣减幂等日志: 一个订单只会扣减/归还一次
CREATE TABLE IF NOT EXISTS stock_log (
    order_no   VARCHAR(64) NOT NULL PRIMARY KEY,
    status     VARCHAR(16) NOT NULL COMMENT 'DEDUCTED / FAILED / RELEASED',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB;

-- ===================== trade =====================
USE mall_trade;
CREATE TABLE IF NOT EXISTS orders (
    id            BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_no      VARCHAR(64)   NOT NULL,
    user_id       BIGINT        NOT NULL,
    status        TINYINT       NOT NULL COMMENT '0创建 1待支付 2已支付 3已取消',
    total_amount  DECIMAL(12,2) NOT NULL,
    remark        VARCHAR(255)  NULL,
    cancel_reason VARCHAR(128)  NULL,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at       DATETIME      NULL,
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user (user_id, created_at),
    KEY idx_status_created (status, created_at)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS order_item (
    id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_no     VARCHAR(64)   NOT NULL,
    sku_id       BIGINT        NOT NULL,
    product_id   BIGINT        NOT NULL,
    product_name VARCHAR(128)  NOT NULL,
    sku_name     VARCHAR(128)  NOT NULL,
    price        DECIMAL(10,2) NOT NULL,
    quantity     INT           NOT NULL,
    KEY idx_order_no (order_no)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS payment (
    id         BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_no   VARCHAR(64)   NOT NULL,
    user_id    BIGINT        NOT NULL,
    amount     DECIMAL(12,2) NOT NULL,
    status     TINYINT       NOT NULL DEFAULT 0 COMMENT '0支付中 1成功 2订单已取消需退款',
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_no (order_no)
) ENGINE = InnoDB;
