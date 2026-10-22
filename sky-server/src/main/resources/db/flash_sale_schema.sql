-- Flash sale module schema and indexes.
-- Run this file once before using /admin/flash-sale or /user/flash-sale/submit.

CREATE TABLE IF NOT EXISTS flash_sale_activity (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    setmeal_id BIGINT NOT NULL COMMENT 'setmeal id',
    title VARCHAR(64) NOT NULL COMMENT 'activity title',
    price DECIMAL(10,2) NOT NULL COMMENT 'flash sale price',
    stock INT NOT NULL COMMENT 'flash sale stock',
    begin_time DATETIME NOT NULL COMMENT 'begin time',
    end_time DATETIME NOT NULL COMMENT 'end time',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0 disabled 1 enabled',
    create_time DATETIME DEFAULT NULL COMMENT 'create time',
    update_time DATETIME DEFAULT NULL COMMENT 'update time',
    create_user BIGINT DEFAULT NULL COMMENT 'create user',
    update_user BIGINT DEFAULT NULL COMMENT 'update user',
    PRIMARY KEY (id),
    KEY idx_flash_sale_time_status (status, begin_time, end_time),
    KEY idx_flash_sale_setmeal (setmeal_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='flash sale activity';

CREATE TABLE IF NOT EXISTS flash_sale_order (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    activity_id BIGINT NOT NULL COMMENT 'activity id',
    setmeal_id BIGINT NOT NULL COMMENT 'setmeal id',
    user_id BIGINT NOT NULL COMMENT 'user id',
    order_id BIGINT DEFAULT NULL COMMENT 'order id',
    order_number VARCHAR(64) DEFAULT NULL COMMENT 'order number',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0 processing 1 created 2 failed',
    fail_reason VARCHAR(255) DEFAULT NULL COMMENT 'fail reason',
    create_time DATETIME DEFAULT NULL COMMENT 'create time',
    update_time DATETIME DEFAULT NULL COMMENT 'update time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_flash_sale_user (activity_id, user_id),
    KEY idx_flash_sale_order_user (user_id, create_time),
    KEY idx_flash_sale_order_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='flash sale order';

ALTER TABLE orders
    ADD INDEX idx_orders_user_status_time (user_id, status, order_time),
    ADD INDEX idx_orders_status_time (status, order_time),
    ADD INDEX idx_orders_number (number);

ALTER TABLE order_detail
    ADD INDEX idx_order_detail_order_id (order_id),
    ADD INDEX idx_order_detail_setmeal_id (setmeal_id);

ALTER TABLE shopping_cart
    ADD INDEX idx_cart_user_setmeal (user_id, setmeal_id),
    ADD INDEX idx_cart_user_dish (user_id, dish_id);

ALTER TABLE setmeal
    ADD INDEX idx_setmeal_category_status (category_id, status);
