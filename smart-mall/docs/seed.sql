CREATE TABLE IF NOT EXISTS `user` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(32) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  phone VARCHAR(20) UNIQUE,
  avatar_url VARCHAR(255),
  role VARCHAR(16) NOT NULL DEFAULT 'USER',
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL,
  parent_id BIGINT DEFAULT NULL,
  sort_order INT DEFAULT 0,
  FOREIGN KEY (parent_id) REFERENCES category(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS product (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  category_id BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL,
  description TEXT,
  price DECIMAL(10,2) NOT NULL,
  stock INT NOT NULL DEFAULT 0,
  sales_count INT NOT NULL DEFAULT 0,
  image_url VARCHAR(255),
  status TINYINT NOT NULL DEFAULT 1,
  version INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (category_id) REFERENCES category(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cart (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity INT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_product (user_id, product_id),
  FOREIGN KEY (user_id) REFERENCES `user`(id),
  FOREIGN KEY (product_id) REFERENCES product(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `order` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(32) NOT NULL UNIQUE,
  user_id BIGINT NOT NULL,
  total_amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING_PAYMENT',
  shipping_address VARCHAR(255) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  paid_at DATETIME DEFAULT NULL,
  FOREIGN KEY (user_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS order_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  product_name_snapshot VARCHAR(128) NOT NULL,
  price_snapshot DECIMAL(10,2) NOT NULL,
  quantity INT NOT NULL,
  FOREIGN KEY (order_id) REFERENCES `order`(id),
  FOREIGN KEY (product_id) REFERENCES product(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_conversation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id VARCHAR(64) NOT NULL,
  user_id BIGINT NOT NULL,
  role VARCHAR(10) NOT NULL,
  content TEXT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_preference (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  preference_tag VARCHAR(50) NOT NULL,
  weight DECIMAL(3,2) NOT NULL DEFAULT 1.0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_tag (user_id, preference_tag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @ft_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'product'
    AND index_name = 'ft_name_desc'
);
SET @ft_sql := IF(@ft_exists = 0,
  'ALTER TABLE product ADD FULLTEXT INDEX ft_name_desc (name, description) WITH PARSER ngram',
  'SELECT 1'
);
PREPARE stmt FROM @ft_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO `user` (id, username, password_hash, phone, avatar_url, role, status)
VALUES
  (1, 'admin', '$2a$10$vOEW0gMvdXGx0o9.OTpcOurxYzW1Blqc4.jgcGNDk9w6UQ08YiNYC', '13800000000', '', 'ADMIN', 1),
  (2, 'alice', '$2a$10$vOEW0gMvdXGx0o9.OTpcOurxYzW1Blqc4.jgcGNDk9w6UQ08YiNYC', '13800000001', '', 'USER', 1),
  (3, 'bob', '$2a$10$vOEW0gMvdXGx0o9.OTpcOurxYzW1Blqc4.jgcGNDk9w6UQ08YiNYC', '13800000002', '', 'USER', 1)
ON DUPLICATE KEY UPDATE username = VALUES(username);

INSERT INTO category (id, name, parent_id, sort_order)
VALUES
  (1, '数码电子', NULL, 1),
  (2, '电脑办公', NULL, 2),
  (3, '居家生活', NULL, 3),
  (4, '键盘鼠标', 2, 1),
  (5, '手机配件', 1, 2),
  (6, '智能家居', 3, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), parent_id = VALUES(parent_id), sort_order = VALUES(sort_order);

INSERT INTO product (id, category_id, name, description, price, stock, sales_count, image_url, status, version)
VALUES
  (1, 4, '极客机械键盘 K87', '适合敲代码的青轴机械键盘，PBT键帽，三模连接', 399.00, 80, 320, 'https://images.unsplash.com/photo-1587829741301-dc798b83add3?auto=format&fit=crop&w=900&q=80', 1, 0),
  (2, 4, '静音办公机械键盘', '茶轴手感，低噪音，适合办公室和宿舍', 459.00, 45, 210, 'https://images.unsplash.com/photo-1595225476474-87563907a212?auto=format&fit=crop&w=900&q=80', 1, 0),
  (3, 4, '电竞鼠标 M9', '轻量化鼠标，26000DPI，适合游戏和设计', 199.00, 120, 500, 'https://images.unsplash.com/photo-1615663245857-ac93bb7c39e7?auto=format&fit=crop&w=900&q=80', 1, 0),
  (4, 2, '27英寸 4K 显示器', 'IPS面板，Type-C供电，适合编程和设计', 1699.00, 35, 96, 'https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?auto=format&fit=crop&w=900&q=80', 1, 0),
  (5, 2, '轻薄办公笔记本', '16GB内存，1TB SSD，长续航', 5299.00, 25, 88, 'https://images.unsplash.com/photo-1496181133206-80ce9b88a853?auto=format&fit=crop&w=900&q=80', 1, 0),
  (6, 1, '降噪蓝牙耳机', '主动降噪，通勤学习都适合', 699.00, 60, 260, 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=900&q=80', 1, 0),
  (7, 1, '运动智能手表', '心率、睡眠、运动记录，7天续航', 899.00, 50, 150, 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=900&q=80', 1, 0),
  (8, 5, '65W 氮化镓充电器', '三口快充，适合手机和平板', 129.00, 150, 410, 'https://images.unsplash.com/photo-1603539444875-76e7684265f6?auto=format&fit=crop&w=900&q=80', 1, 0),
  (9, 5, '编织数据线套装', 'Type-C快充线，耐弯折', 39.00, 300, 800, 'https://images.unsplash.com/photo-1619410283995-43d9134e7656?auto=format&fit=crop&w=900&q=80', 1, 0),
  (10, 6, '智能台灯 Pro', '护眼照明，手机App控制，学习办公', 259.00, 70, 170, 'https://images.unsplash.com/photo-1507473885765-e6ed057f782c?auto=format&fit=crop&w=900&q=80', 1, 0),
  (11, 6, '智能插座 Mini', '远程开关，电量统计，定时任务', 59.00, 180, 390, 'https://images.unsplash.com/photo-1558618666-fcd25c85cd64?auto=format&fit=crop&w=900&q=80', 1, 0),
  (12, 3, '人体工学椅', '腰托可调，适合长时间办公', 799.00, 32, 90, 'https://images.unsplash.com/photo-1580480055273-228ff5388ef8?auto=format&fit=crop&w=900&q=80', 1, 0),
  (13, 3, '升降电脑桌', '电动升降，记忆高度，办公学习', 1299.00, 20, 74, 'https://images.unsplash.com/photo-1518455027359-f3f8164ba6bd?auto=format&fit=crop&w=900&q=80', 1, 0),
  (14, 4, '热插拔客制化键盘', 'Gasket结构，RGB灯效，适合键盘爱好者', 599.00, 40, 230, 'https://images.unsplash.com/photo-1618384887929-16ec33fab9ef?auto=format&fit=crop&w=900&q=80', 1, 0),
  (15, 4, '入门薄膜键盘', '安静耐用，预算敏感用户首选', 69.00, 200, 620, 'https://images.unsplash.com/photo-1589578228447-e1a4e481c6c8?auto=format&fit=crop&w=900&q=80', 1, 0),
  (16, 1, '便携蓝牙音箱', '小体积大音量，露营和宿舍适用', 239.00, 85, 160, 'https://images.unsplash.com/photo-1608043152269-423dbba4e7e1?auto=format&fit=crop&w=900&q=80', 1, 0),
  (17, 2, 'USB-C 扩展坞', 'HDMI、网口、读卡器，多接口扩展', 189.00, 95, 240, 'https://images.unsplash.com/photo-1625842268584-8f3296236761?auto=format&fit=crop&w=900&q=80', 1, 0),
  (18, 3, '桌面收纳架', '整理显示器和键盘区域，提升桌面空间', 119.00, 110, 140, 'https://images.unsplash.com/photo-1497366754035-f200968a6e72?auto=format&fit=crop&w=900&q=80', 1, 0),
  (19, 6, '扫地机器人 Lite', '自动清扫，智能避障，适合小户型', 1099.00, 18, 65, 'https://images.unsplash.com/photo-1563453392212-326f5e854473?auto=format&fit=crop&w=900&q=80', 1, 0),
  (20, 5, '磁吸充电宝', '10000mAh，轻薄便携，支持无线充', 169.00, 130, 350, 'https://images.unsplash.com/photo-1609091839311-d5365f9ff1c5?auto=format&fit=crop&w=900&q=80', 1, 0),
  (21, 2, '抢购测试商品', '用于100并发购买80库存验收', 9.90, 80, 0, 'https://images.unsplash.com/photo-1526947425960-945c6e72858f?auto=format&fit=crop&w=900&q=80', 1, 0)
ON DUPLICATE KEY UPDATE
  category_id = VALUES(category_id),
  name = VALUES(name),
  description = VALUES(description),
  price = VALUES(price),
  stock = VALUES(stock),
  sales_count = VALUES(sales_count),
  image_url = VALUES(image_url),
  status = VALUES(status);

INSERT INTO cart (user_id, product_id, quantity)
VALUES (2, 1, 1), (2, 8, 2)
ON DUPLICATE KEY UPDATE quantity = VALUES(quantity);

INSERT INTO `order` (id, order_no, user_id, total_amount, status, shipping_address, created_at, paid_at)
VALUES
  (1, 'SM202606300001', 2, 399.00, 'PAID', '上海市浦东新区软件园 1 号楼', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  user_id = VALUES(user_id),
  total_amount = VALUES(total_amount),
  status = VALUES(status),
  shipping_address = VALUES(shipping_address),
  paid_at = VALUES(paid_at);

INSERT INTO order_item (id, order_id, product_id, product_name_snapshot, price_snapshot, quantity)
VALUES
  (1, 1, 1, '极客机械键盘 K87', 399.00, 1)
ON DUPLICATE KEY UPDATE
  product_id = VALUES(product_id),
  product_name_snapshot = VALUES(product_name_snapshot),
  price_snapshot = VALUES(price_snapshot),
  quantity = VALUES(quantity);

INSERT INTO user_preference (user_id, preference_tag, weight)
VALUES (2, '机械键盘', 1.2)
ON DUPLICATE KEY UPDATE
  weight = GREATEST(weight, VALUES(weight)),
  updated_at = CURRENT_TIMESTAMP;
