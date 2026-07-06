SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE IF NOT EXISTS `user` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(32) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  phone VARCHAR(20) NULL,
  avatar_url VARCHAR(255) NULL,
  role VARCHAR(16) NOT NULL DEFAULT 'USER',
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uk_user_username UNIQUE (username),
  CONSTRAINT uk_user_phone UNIQUE (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL,
  parent_id BIGINT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  KEY idx_category_parent (parent_id),
  CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES category(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS product (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  category_id BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL,
  description TEXT NULL,
  price DECIMAL(10,2) NOT NULL,
  stock INT NOT NULL DEFAULT 0,
  sales_count INT NOT NULL DEFAULT 0,
  image_url VARCHAR(255) NULL,
  status TINYINT NOT NULL DEFAULT 1,
  version INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_product_category_status (category_id, status),
  KEY idx_product_status_sales (status, sales_count, id),
  KEY idx_product_status_stock (status, stock),
  CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cart (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity INT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_cart_user_product UNIQUE (user_id, product_id),
  KEY idx_cart_user_created (user_id, created_at),
  KEY idx_cart_product (product_id),
  CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES `user`(id),
  CONSTRAINT fk_cart_product FOREIGN KEY (product_id) REFERENCES product(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `order` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(32) NOT NULL,
  user_id BIGINT NOT NULL,
  total_amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING_PAYMENT',
  shipping_address VARCHAR(255) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  paid_at DATETIME NULL,
  CONSTRAINT uk_order_no UNIQUE (order_no),
  KEY idx_order_user_created (user_id, created_at),
  KEY idx_order_status_created (status, created_at),
  CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS order_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  product_name_snapshot VARCHAR(128) NOT NULL,
  price_snapshot DECIMAL(10,2) NOT NULL,
  quantity INT NOT NULL,
  KEY idx_order_item_order (order_id),
  KEY idx_order_item_product (product_id),
  CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES `order`(id),
  CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES product(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS product_favorite (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_favorite_user_product UNIQUE (user_id, product_id),
  KEY idx_favorite_user_created (user_id, created_at),
  KEY idx_favorite_product (product_id),
  CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES `user`(id),
  CONSTRAINT fk_favorite_product FOREIGN KEY (product_id) REFERENCES product(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS product_view_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  view_count INT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_viewed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_view_history_user_product UNIQUE (user_id, product_id),
  KEY idx_view_history_user_last (user_id, last_viewed_at),
  KEY idx_view_history_product (product_id),
  CONSTRAINT fk_view_history_user FOREIGN KEY (user_id) REFERENCES `user`(id),
  CONSTRAINT fk_view_history_product FOREIGN KEY (product_id) REFERENCES product(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS agent_conversation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id VARCHAR(64) NOT NULL,
  user_id BIGINT NOT NULL,
  role VARCHAR(10) NOT NULL,
  content TEXT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_agent_conversation_session (session_id),
  KEY idx_agent_conversation_user_session (user_id, session_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_preference (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  preference_tag VARCHAR(50) NOT NULL,
  weight DECIMAL(3,2) NOT NULL DEFAULT 1.00,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uk_user_preference_tag UNIQUE (user_id, preference_tag),
  KEY idx_user_preference_user_weight (user_id, weight, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @ft_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'product'
    AND index_name = 'ft_name_desc'
);

SET @ft_sql := IF(
  @ft_exists = 0,
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
ON DUPLICATE KEY UPDATE
  username = VALUES(username),
  password_hash = VALUES(password_hash),
  phone = VALUES(phone),
  avatar_url = VALUES(avatar_url),
  role = VALUES(role),
  status = VALUES(status);

INSERT INTO category (id, name, parent_id, sort_order)
VALUES
  (1, '数码电子', NULL, 1),
  (2, '电脑办公', NULL, 2),
  (3, '家居生活', NULL, 3),
  (4, '键盘鼠标', 2, 1),
  (5, '手机配件', 1, 2),
  (6, '智能家居', 3, 1),
  (7, '服饰鞋包', NULL, 4),
  (8, '美妆个护', NULL, 5),
  (9, '运动户外', NULL, 6),
  (10, '图书文创', NULL, 7),
  (11, '厨房餐具', 3, 2),
  (12, '母婴用品', 3, 3),
  (13, '男装女装', 7, 1),
  (14, '鞋靴箱包', 7, 2),
  (15, '护肤彩妆', 8, 1),
  (16, '个人护理', 8, 2),
  (17, '健身装备', 9, 1),
  (18, '户外露营', 9, 2),
  (19, '技术图书', 10, 1),
  (20, '文具手账', 10, 2)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  parent_id = VALUES(parent_id),
  sort_order = VALUES(sort_order);

INSERT INTO product (id, category_id, name, description, price, stock, sales_count, image_url, status, version)
VALUES
  (1, 4, '极客机械键盘 K87', '适合敲代码的青轴机械键盘，PBT 键帽，三模连接', 399.00, 80, 320, 'https://images.unsplash.com/photo-1587829741301-dc798b83add3?auto=format&fit=crop&w=900&q=80', 1, 0),
  (2, 4, '静音办公机械键盘', '茶轴手感，低噪音，适合办公室和宿舍', 459.00, 45, 210, 'https://images.unsplash.com/photo-1595225476474-87563907a212?auto=format&fit=crop&w=900&q=80', 1, 0),
  (3, 4, '电竞鼠标 M9', '轻量化鼠标，26000DPI，适合游戏和设计', 199.00, 120, 500, 'https://images.unsplash.com/photo-1615663245857-ac93bb7c39e7?auto=format&fit=crop&w=900&q=80', 1, 0),
  (4, 2, '27 英寸 4K 显示器', 'IPS 面板，Type-C 供电，适合编程和设计', 1699.00, 35, 96, 'https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?auto=format&fit=crop&w=900&q=80', 1, 0),
  (5, 2, '轻薄办公笔记本', '16GB 内存，1TB SSD，长续航', 5299.00, 25, 88, 'https://images.unsplash.com/photo-1496181133206-80ce9b88a853?auto=format&fit=crop&w=900&q=80', 1, 0),
  (6, 1, '降噪蓝牙耳机', '主动降噪，通勤学习都适合', 699.00, 60, 260, 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=900&q=80', 1, 0),
  (7, 1, '运动智能手表', '心率、睡眠、运动记录，7 天续航', 899.00, 50, 150, 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=900&q=80', 1, 0),
  (8, 5, '65W 氮化镓充电器', '三口快充，适合手机和平板', 129.00, 150, 410, 'https://images.unsplash.com/photo-1603539444875-76e7684265f6?auto=format&fit=crop&w=900&q=80', 1, 0),
  (9, 5, '编织数据线套装', 'USB-C 双头快充线，编织外被，耐弯折，支持手机和平板充电与数据传输', 39.00, 300, 800, 'https://images.unsplash.com/photo-1492107376256-4026437926cd?auto=format&fit=crop&w=900&q=80', 1, 0),
  (10, 6, '智能台灯 Pro', '护眼照明，手机 App 控制，学习办公适用', 259.00, 70, 170, 'https://images.unsplash.com/photo-1507473885765-e6ed057f782c?auto=format&fit=crop&w=900&q=80', 1, 0),
  (11, 6, '智能插座 Mini', '远程开关，电量统计，定时任务', 59.00, 180, 390, 'https://images.unsplash.com/photo-1558618666-fcd25c85cd64?auto=format&fit=crop&w=900&q=80', 1, 0),
  (12, 3, '人体工学椅', '腰托可调，适合长时间办公', 799.00, 32, 90, 'https://images.unsplash.com/photo-1580480055273-228ff5388ef8?auto=format&fit=crop&w=900&q=80', 1, 0),
  (13, 3, '升降电脑桌', '电动升降，记忆高度，办公学习适用', 1299.00, 20, 74, 'https://images.unsplash.com/photo-1518455027359-f3f8164ba6bd?auto=format&fit=crop&w=900&q=80', 1, 0),
  (14, 4, '热插拔客制化键盘', 'Gasket 结构，RGB 灯效，适合键盘爱好者', 599.00, 40, 230, 'https://images.unsplash.com/photo-1618384887929-16ec33fab9ef?auto=format&fit=crop&w=900&q=80', 1, 0),
  (15, 4, '入门薄膜键盘', '安静耐用，预算敏感用户首选', 69.00, 200, 620, 'https://images.unsplash.com/photo-1589578228447-e1a4e481c6c8?auto=format&fit=crop&w=900&q=80', 1, 0),
  (16, 1, '便携蓝牙音箱', '小体积大音量，露营和宿舍适用', 239.00, 85, 160, 'https://images.unsplash.com/photo-1608043152269-423dbba4e7e1?auto=format&fit=crop&w=900&q=80', 1, 0),
  (17, 2, 'USB-C 扩展坞', 'HDMI、网口、读卡器，多接口扩展', 189.00, 95, 240, 'https://images.unsplash.com/photo-1625842268584-8f3296236761?auto=format&fit=crop&w=900&q=80', 1, 0),
  (18, 3, '桌面收纳架', '整理显示器和键盘区域，提升桌面空间', 119.00, 110, 140, 'https://images.unsplash.com/photo-1497366754035-f200968a6e72?auto=format&fit=crop&w=900&q=80', 1, 0),
  (19, 6, '扫地机器人 Lite', '自动清扫，智能避障，适合小户型', 1099.00, 18, 65, 'https://images.unsplash.com/photo-1563453392212-326f5e854473?auto=format&fit=crop&w=900&q=80', 1, 0),
  (20, 5, '磁吸充电宝', '10000mAh，轻薄便携，支持无线充', 169.00, 130, 350, 'https://images.unsplash.com/photo-1609091839311-d5365f9ff1c5?auto=format&fit=crop&w=900&q=80', 1, 0),
  (21, 2, '抢购测试商品', '用于 100 并发购买 80 库存验收', 9.90, 80, 0, 'https://images.unsplash.com/photo-1526947425960-945c6e72858f?auto=format&fit=crop&w=900&q=80', 1, 0),
  (22, 13, '纯棉基础款 T 恤', '重磅纯棉面料，日常通勤和校园穿搭都适合', 79.00, 180, 260, 'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?auto=format&fit=crop&w=900&q=80', 1, 0),
  (23, 13, '轻薄防晒外套', 'UPF50+ 防晒，轻便可收纳，适合春夏出行', 199.00, 95, 180, 'https://images.unsplash.com/photo-1543076447-215ad9ba6923?auto=format&fit=crop&w=900&q=80', 1, 0),
  (24, 13, '直筒休闲牛仔裤', '弹力牛仔面料，版型利落，适合日常搭配', 229.00, 75, 145, 'https://images.unsplash.com/photo-1542272604-787c3835535d?auto=format&fit=crop&w=900&q=80', 1, 0),
  (25, 14, '城市通勤双肩包', '15.6 英寸电脑仓，多隔层收纳，防泼水面料', 269.00, 68, 210, 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?auto=format&fit=crop&w=900&q=80', 1, 0),
  (26, 14, '缓震跑步鞋 AirRun', '轻量缓震中底，适合日常慢跑和健身房训练', 399.00, 88, 170, 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=900&q=80', 1, 0),
  (27, 14, '真皮短款钱包', '头层牛皮，小巧耐用，支持卡位分区', 159.00, 120, 90, 'https://images.unsplash.com/photo-1627123424574-724758594e93?auto=format&fit=crop&w=900&q=80', 1, 0),
  (28, 15, '氨基酸洁面乳', '温和清洁，适合学生和通勤人群日常使用', 69.00, 150, 300, 'https://images.unsplash.com/photo-1556228578-8c89e6adf883?auto=format&fit=crop&w=900&q=80', 1, 0),
  (29, 15, '玻尿酸保湿精华', '轻薄易吸收，适合换季干燥和熬夜护肤', 129.00, 90, 220, 'https://images.unsplash.com/photo-1620916566398-39f1143ab7be?auto=format&fit=crop&w=900&q=80', 1, 0),
  (30, 15, '持妆粉底液', '自然雾面妆效，适合通勤和拍照场景', 189.00, 60, 135, 'https://images.unsplash.com/photo-1596462502278-27bfdc403348?auto=format&fit=crop&w=900&q=80', 1, 0),
  (31, 16, '电动牙刷 CleanPro', '五档清洁模式，智能计时，适合全家使用', 239.00, 110, 280, 'https://images.unsplash.com/photo-1609840114035-3c981b782dfe?auto=format&fit=crop&w=900&q=80', 1, 0),
  (32, 16, '高速吹风机', '大风量速干，恒温护发，低噪音设计', 499.00, 45, 155, 'https://images.unsplash.com/photo-1522338140262-f46f5913618a?auto=format&fit=crop&w=900&q=80', 1, 0),
  (33, 16, '筋膜按摩仪 Mini', '便携放松肩颈腿部，适合运动后恢复', 299.00, 70, 120, 'https://images.unsplash.com/photo-1518611012118-696072aa579a?auto=format&fit=crop&w=900&q=80', 1, 0),
  (34, 17, '可调节哑铃套装', '2.5kg 到 20kg 快速切换，适合家庭训练', 699.00, 32, 80, 'https://images.unsplash.com/photo-1583454110551-21f2fa2afe61?auto=format&fit=crop&w=900&q=80', 1, 0),
  (35, 17, '防滑瑜伽垫', '加厚 TPE 材质，防滑回弹，适合初学者', 89.00, 160, 260, 'https://images.unsplash.com/photo-1599901860904-17e6ed7083a0?auto=format&fit=crop&w=900&q=80', 1, 0),
  (36, 17, '运动水壶 700ml', 'Tritan 材质，单手开盖，健身通勤可用', 49.00, 220, 340, 'https://images.unsplash.com/photo-1602143407151-7111542de6e8?auto=format&fit=crop&w=900&q=80', 1, 0),
  (37, 18, '轻量露营帐篷', '双人三季帐，防水外帐，适合周末露营', 599.00, 36, 76, 'https://images.unsplash.com/photo-1504280390367-361c6d9f38f4?auto=format&fit=crop&w=900&q=80', 1, 0),
  (38, 18, '便携折叠椅', '铝合金支架，收纳小，露营钓鱼都适合', 139.00, 95, 190, 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=900&q=80', 1, 0),
  (39, 18, '户外保温杯', '316 不锈钢，长效保温，徒步和通勤可用', 99.00, 130, 210, 'https://images.unsplash.com/photo-1523362628745-0c100150b504?auto=format&fit=crop&w=900&q=80', 1, 0),
  (40, 19, 'Spring Boot 实战手册', '覆盖 REST API、JWT、安全认证和部署实践', 89.00, 100, 180, 'https://images.unsplash.com/photo-1516979187457-637abb4f9353?auto=format&fit=crop&w=900&q=80', 1, 0),
  (41, 19, 'Vue3 组件化开发指南', '组合式 API、状态管理、前端工程化实战', 79.00, 90, 150, 'https://images.unsplash.com/photo-1515879218367-8466d910aaa4?auto=format&fit=crop&w=900&q=80', 1, 0),
  (42, 19, 'AI Agent 应用开发入门', 'LangChain 工具调用、记忆机制和商城助手案例', 99.00, 70, 240, 'https://images.unsplash.com/photo-1456513080510-7bf3a84b82f8?auto=format&fit=crop&w=900&q=80', 1, 0),
  (43, 20, '点阵手账本套装', '含贴纸、索引页和计划页，适合学习计划管理', 59.00, 180, 260, 'https://images.unsplash.com/photo-1517842645767-c639042777db?auto=format&fit=crop&w=900&q=80', 1, 0),
  (44, 20, '低重心中性笔 6 支装', '顺滑速干，适合课堂笔记和办公签字', 29.00, 260, 420, 'https://images.unsplash.com/photo-1583485088034-697b5bc54ccd?auto=format&fit=crop&w=900&q=80', 1, 0),
  (45, 20, '桌面文件收纳盒', '多层文件分区，适合课程资料和发票整理', 49.00, 140, 130, 'https://images.unsplash.com/photo-1456735190827-d1262f71b8a3?auto=format&fit=crop&w=900&q=80', 1, 0),
  (46, 5, '折叠手机支架', '铝合金折叠结构，硅胶防滑垫，预留充电口，适合桌面追剧和视频会议', 49.00, 180, 360, 'https://images.unsplash.com/photo-1516245556508-7d60d4ff0f39?auto=format&fit=crop&w=900&q=80', 1, 0),
  (47, 5, '无线充电板 15W', 'Qi 磁吸定位，15W 无线快充，防滑布面，支持手机和耳机快速补电', 99.00, 120, 260, 'https://images.unsplash.com/photo-1545235616-db3cd822ad8c?auto=format&fit=crop&w=900&q=80', 1, 0),
  (48, 2, '2K 自动对焦摄像头', '2K 自动对焦，内置降噪麦克风，会议直播两用，夹式支架适配显示器', 299.00, 75, 190, 'https://images.unsplash.com/photo-1715869618915-a7bf6608d4c3?auto=format&fit=crop&w=900&q=80', 1, 0),
  (49, 3, '人体工学脚踏', '防滑按摩凸点，三档倾角调节，缓解久坐腿部压力', 139.00, 95, 145, 'https://www.mount-it.com/cdn/shop/files/under-desk-ergonomic-footrest-black-mount-it-mi-7803-39688144879771.jpg?v=1763595989&width=900', 1, 0),
  (50, 4, '无线双模鼠标 M3', '蓝牙和2.4G双模连接，低噪微动，轻办公长续航', 129.00, 150, 330, 'https://images.unsplash.com/photo-1527864550417-7fd91fc51a46?auto=format&fit=crop&w=900&q=80', 1, 0),
  (51, 6, '智能门锁 S1', '指纹、密码、手机 App 多方式解锁，支持临时访客密码与门锁状态查看', 899.00, 35, 88, 'https://images.unsplash.com/photo-1558002038-1055907df827?auto=format&fit=crop&w=900&q=80', 1, 0),
  (52, 6, '桌面香薰加湿器', '细雾补水，低噪运行，桌面香薰扩散，适合卧室和工位', 159.00, 110, 240, 'https://images.unsplash.com/photo-1768471569643-717e823b5f9a?auto=format&fit=crop&w=900&q=80', 1, 0),
  (53, 3, '亚麻抱枕套两只装', '亚麻混纺面料，隐藏拉链，可拆洗，客厅卧室都好搭配', 69.00, 160, 210, 'https://images.unsplash.com/photo-1531877025030-f7696a50770f?auto=format&fit=crop&w=900&q=80', 1, 0),
  (54, 13, '速干训练短袖', '速干排汗面料，轻薄透气，跑步和健身房训练适用', 99.00, 140, 300, 'https://images.unsplash.com/photo-1581655353564-df123a1eb820?auto=format&fit=crop&w=900&q=80', 1, 0),
  (55, 14, '轻量越野背包 18L', '18L 轻量容量，外置水壶位，多口袋分区，短途徒步和城市通勤两用', 239.00, 70, 170, 'https://images.unsplash.com/photo-1509762774605-f07235a08f1f?auto=format&fit=crop&w=900&q=80', 1, 0),
  (56, 15, '维C亮肤面膜 10片', '维C亮肤配方，服帖膜布，清爽补水，适合熬夜后的基础护理', 79.00, 180, 380, 'https://images.unsplash.com/photo-1670201203270-7bc9b329d2eb?auto=format&fit=crop&w=900&q=80', 1, 0),
  (57, 16, '便携冲牙器', '三档水压，可拆水箱，5 个替换喷嘴，旅行收纳友好', 199.00, 85, 230, 'https://uwhitening.ca/cdn/shop/files/The_Best_Waterflosser_In_Canada.jpg?v=1742413110&width=900', 1, 0),
  (58, 17, '计数跳绳 Pro', '电子计数，防滑手柄，PVC 包胶绳体，适合居家燃脂', 59.00, 220, 420, 'https://images.unsplash.com/photo-1516876345887-6dd74f80787a?auto=format&fit=crop&w=900&q=80', 1, 0),
  (59, 18, '折叠野餐垫', '防潮底层，大尺寸可机洗，折叠收纳，露营野餐适用', 129.00, 100, 160, 'https://images.unsplash.com/photo-1731186622228-38f68d3f64ad?auto=format&fit=crop&w=900&q=80', 1, 0),
  (60, 20, '线圈计划本', '周计划和月计划页面，线圈装订，可平摊书写，适合课程与项目管理', 39.00, 240, 310, 'https://images.unsplash.com/photo-1632772998001-cc9bf6f7c852?auto=format&fit=crop&w=900&q=80', 1, 0)
ON DUPLICATE KEY UPDATE
  category_id = VALUES(category_id),
  name = VALUES(name),
  description = VALUES(description),
  price = VALUES(price),
  stock = VALUES(stock),
  sales_count = VALUES(sales_count),
  image_url = VALUES(image_url),
  status = VALUES(status),
  version = VALUES(version);

INSERT INTO cart (user_id, product_id, quantity)
VALUES
  (2, 1, 1),
  (2, 8, 2)
ON DUPLICATE KEY UPDATE
  quantity = VALUES(quantity);

INSERT INTO `order` (id, order_no, user_id, total_amount, status, shipping_address, created_at, paid_at)
VALUES
  (1, 'SM202606300001', 2, 399.00, 'PAID', '上海市浦东新区软件园 1 号楼', '2026-06-30 10:00:00', '2026-06-30 10:05:00')
ON DUPLICATE KEY UPDATE
  order_no = VALUES(order_no),
  user_id = VALUES(user_id),
  total_amount = VALUES(total_amount),
  status = VALUES(status),
  shipping_address = VALUES(shipping_address),
  created_at = VALUES(created_at),
  paid_at = VALUES(paid_at);

INSERT INTO order_item (id, order_id, product_id, product_name_snapshot, price_snapshot, quantity)
VALUES
  (1, 1, 1, '极客机械键盘 K87', 399.00, 1)
ON DUPLICATE KEY UPDATE
  order_id = VALUES(order_id),
  product_id = VALUES(product_id),
  product_name_snapshot = VALUES(product_name_snapshot),
  price_snapshot = VALUES(price_snapshot),
  quantity = VALUES(quantity);

INSERT INTO product_favorite (id, user_id, product_id, created_at)
VALUES
  (1, 2, 1, '2026-06-30 09:20:00'),
  (2, 2, 14, '2026-06-30 09:25:00'),
  (3, 2, 42, '2026-06-30 09:40:00')
ON DUPLICATE KEY UPDATE
  user_id = VALUES(user_id),
  product_id = VALUES(product_id),
  created_at = VALUES(created_at);

INSERT INTO product_view_history (id, user_id, product_id, view_count, created_at, last_viewed_at)
VALUES
  (1, 2, 1, 5, '2026-06-30 09:10:00', '2026-06-30 10:30:00'),
  (2, 2, 14, 3, '2026-06-30 09:18:00', '2026-06-30 10:25:00'),
  (3, 2, 6, 2, '2026-06-30 09:35:00', '2026-06-30 10:10:00'),
  (4, 2, 42, 1, '2026-06-30 09:45:00', '2026-06-30 09:45:00')
ON DUPLICATE KEY UPDATE
  view_count = VALUES(view_count),
  last_viewed_at = VALUES(last_viewed_at);

INSERT INTO user_preference (user_id, preference_tag, weight)
VALUES
  (2, '机械键盘', 1.20)
ON DUPLICATE KEY UPDATE
  weight = GREATEST(weight, VALUES(weight)),
  updated_at = CURRENT_TIMESTAMP;

-- Batch 2: coupons and points
SET @user_points_exists := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'user'
    AND column_name = 'points'
);
SET @user_points_sql := IF(
  @user_points_exists = 0,
  'ALTER TABLE `user` ADD COLUMN points INT NOT NULL DEFAULT 0',
  'SELECT 1'
);
PREPARE stmt FROM @user_points_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @order_discount_exists := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'order'
    AND column_name = 'discount_amount'
);
SET @order_discount_sql := IF(
  @order_discount_exists = 0,
  'ALTER TABLE `order` ADD COLUMN discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00',
  'SELECT 1'
);
PREPARE stmt FROM @order_discount_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @order_points_exists := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'order'
    AND column_name = 'points_used'
);
SET @order_points_sql := IF(
  @order_points_exists = 0,
  'ALTER TABLE `order` ADD COLUMN points_used INT NOT NULL DEFAULT 0',
  'SELECT 1'
);
PREPARE stmt FROM @order_points_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS coupon (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL COMMENT '优惠券名称',
  type TINYINT NOT NULL COMMENT '1=满减 2=折扣',
  threshold DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '使用门槛（满X元）',
  discount DECIMAL(10,2) NOT NULL COMMENT '满减金额 或 折扣率(0.8=8折)',
  total_count INT NOT NULL COMMENT '发行总量',
  remain_count INT NOT NULL COMMENT '剩余数量',
  valid_days INT NOT NULL DEFAULT 30 COMMENT '领取后有效天数',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_coupon (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  coupon_id BIGINT NOT NULL,
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0=未使用 1=已使用 2=已过期',
  expire_at DATE NOT NULL,
  used_order_id BIGINT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_coupon (user_id, coupon_id),
  INDEX idx_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS point_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  delta INT NOT NULL COMMENT '正=增加 负=扣减',
  reason VARCHAR(128) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO coupon (id, name, type, threshold, discount, total_count, remain_count, valid_days)
VALUES
  (1, '新人专享券', 1, 50.00, 10.00, 1000, 999, 30),
  (2, '满100减20', 1, 100.00, 20.00, 500, 500, 15),
  (3, '九折优惠', 2, 0.00, 0.90, 200, 200, 7)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  type = VALUES(type),
  threshold = VALUES(threshold),
  discount = VALUES(discount),
  total_count = VALUES(total_count),
  remain_count = VALUES(remain_count),
  valid_days = VALUES(valid_days);

UPDATE `user` SET points = 300 WHERE id = 2;

INSERT INTO user_coupon (id, user_id, coupon_id, status, expire_at, used_order_id)
VALUES
  (1, 2, 1, 0, DATE_ADD(CURRENT_DATE, INTERVAL 30 DAY), NULL)
ON DUPLICATE KEY UPDATE
  status = VALUES(status),
  expire_at = VALUES(expire_at),
  used_order_id = VALUES(used_order_id);

INSERT INTO point_record (id, user_id, delta, reason)
VALUES
  (1, 2, 300, '演示初始积分')
ON DUPLICATE KEY UPDATE
  delta = VALUES(delta),
  reason = VALUES(reason);

-- Batch 3: product reviews and dashboard/export demo data
CREATE TABLE IF NOT EXISTS product_review (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  rating TINYINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
  content VARCHAR(500),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_product(product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `order` (id, order_no, user_id, total_amount, discount_amount, points_used, status, shipping_address, created_at, paid_at)
VALUES
  (2, 'SM202607020001', 2, 599.00, 0.00, 0, 'COMPLETED', '上海市浦东新区软件园 1 号楼', '2026-07-02 11:00:00', '2026-07-02 11:05:00'),
  (3, 'SM202607020002', 2, 29.00, 0.00, 0, 'COMPLETED', '上海市浦东新区软件园 1 号楼', '2026-07-02 13:20:00', '2026-07-02 13:25:00'),
  (4, 'SM202607020003', 3, 39.00, 0.00, 0, 'COMPLETED', '杭州市西湖区文三路 88 号', '2026-07-02 15:10:00', '2026-07-02 15:15:00'),
  (5, 'SM202607020004', 3, 399.00, 20.00, 0, 'COMPLETED', '杭州市西湖区文三路 88 号', '2026-07-02 16:40:00', '2026-07-02 16:45:00'),
  (6, 'SM202607020005', 2, 69.00, 0.00, 0, 'COMPLETED', '上海市浦东新区软件园 1 号楼', '2026-07-03 10:30:00', '2026-07-03 10:34:00')
ON DUPLICATE KEY UPDATE
  order_no = VALUES(order_no),
  user_id = VALUES(user_id),
  total_amount = VALUES(total_amount),
  discount_amount = VALUES(discount_amount),
  points_used = VALUES(points_used),
  status = VALUES(status),
  shipping_address = VALUES(shipping_address),
  created_at = VALUES(created_at),
  paid_at = VALUES(paid_at);

INSERT INTO order_item (id, order_id, product_id, product_name_snapshot, price_snapshot, quantity)
VALUES
  (2, 2, 14, '热插拔客制化键盘', 599.00, 1),
  (3, 3, 44, '低重心中性笔 6 支装', 29.00, 1),
  (4, 4, 9, '编织数据线套装', 39.00, 1),
  (5, 5, 1, '极客机械键盘 K87', 399.00, 1),
  (6, 6, 15, '入门薄膜键盘', 69.00, 1)
ON DUPLICATE KEY UPDATE
  order_id = VALUES(order_id),
  product_id = VALUES(product_id),
  product_name_snapshot = VALUES(product_name_snapshot),
  price_snapshot = VALUES(price_snapshot),
  quantity = VALUES(quantity);

INSERT INTO product_review (id, product_id, user_id, order_id, rating, content, created_at)
VALUES
  (1, 14, 2, 2, 5, 'Gasket 手感扎实，热插拔换轴很方便，适合键盘爱好者。', '2026-07-03 09:30:00'),
  (2, 44, 2, 3, 5, '笔身重心稳定，长时间记笔记不累，黑色外观也比较耐看。', '2026-07-03 11:15:00'),
  (3, 9, 3, 4, 4, '线材比普通数据线厚实，充电和传输都稳定，放包里也不容易打结。', '2026-07-03 13:40:00'),
  (4, 1, 3, 5, 5, '三模连接切换很顺，PBT 键帽手感干爽，敲代码很舒服。', '2026-07-03 16:05:00'),
  (5, 15, 2, 6, 4, '预算有限时很合适，声音比机械键盘轻，宿舍晚上用不会太吵。', '2026-07-04 10:20:00')
ON DUPLICATE KEY UPDATE
  product_id = VALUES(product_id),
  user_id = VALUES(user_id),
  order_id = VALUES(order_id),
  rating = VALUES(rating),
  content = VALUES(content),
  created_at = VALUES(created_at);

-- Batch 4: user feedback
CREATE TABLE IF NOT EXISTS user_feedback (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  type TINYINT NOT NULL COMMENT '1=建议 2=投诉 3=BUG',
  content VARCHAR(1000) NOT NULL,
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0=待处理 1=已处理',
  reply VARCHAR(500),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO user_feedback (id, user_id, type, content, status, reply, created_at)
VALUES
  (1, 2, 1, '希望结算页能直接看到优惠券和积分抵扣明细。', 1, '已上线优惠券与积分抵扣展示。', '2026-07-04 14:20:00'),
  (2, 3, 3, '移动端商品卡片描述偶尔换行过长。', 0, NULL, '2026-07-05 16:10:00')
ON DUPLICATE KEY UPDATE
  type = VALUES(type),
  content = VALUES(content),
  status = VALUES(status),
  reply = VALUES(reply),
  created_at = VALUES(created_at);
