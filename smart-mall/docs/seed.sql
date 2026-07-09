-- Demo data only. Table definitions live in docs/schema.sql.
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO `user` (id, username, password_hash, phone, avatar_url, role, status, points)
VALUES
  (1, 'admin', '$2a$10$vOEW0gMvdXGx0o9.OTpcOurxYzW1Blqc4.jgcGNDk9w6UQ08YiNYC', '13800000000', '', 'ADMIN', 1, 0),
  (2, 'alice', '$2a$10$vOEW0gMvdXGx0o9.OTpcOurxYzW1Blqc4.jgcGNDk9w6UQ08YiNYC', '13800000001', '', 'USER', 1, 300),
  (3, 'bob', '$2a$10$vOEW0gMvdXGx0o9.OTpcOurxYzW1Blqc4.jgcGNDk9w6UQ08YiNYC', '13800000002', '', 'USER', 1, 120)
ON DUPLICATE KEY UPDATE
  username = VALUES(username),
  password_hash = VALUES(password_hash),
  phone = VALUES(phone),
  avatar_url = VALUES(avatar_url),
  role = VALUES(role),
  status = VALUES(status),
  points = VALUES(points);

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
  (1, 4, '极客机械键盘 K87', '青轴机械键盘，PBT 键帽，三模连接，适合程序员长时间打字。', 399.00, 80, 320, 'https://images.unsplash.com/photo-1587829741301-dc798b83add3?auto=format&fit=crop&w=900&q=80', 1, 0),
  (2, 4, '静音办公机械键盘', '茶轴手感，低噪音，适合办公室、宿舍和夜间学习。', 459.00, 45, 210, 'https://images.unsplash.com/photo-1595225476474-87563907a212?auto=format&fit=crop&w=900&q=80', 1, 0),
  (3, 4, '电竞鼠标 M9', '轻量化鼠标，26000DPI，适合游戏和设计。', 199.00, 120, 500, 'https://images.unsplash.com/photo-1615663245857-ac93bb7c39e7?auto=format&fit=crop&w=900&q=80', 1, 0),
  (4, 2, '27 英寸 4K 显示器', 'IPS 面板，Type-C 供电，适合编程、设计和多窗口办公。', 1699.00, 35, 96, 'https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?auto=format&fit=crop&w=900&q=80', 1, 0),
  (5, 2, '轻薄办公笔记本', '16GB 内存，1TB SSD，长续航，适合移动办公。', 5299.00, 25, 88, 'https://images.unsplash.com/photo-1496181133206-80ce9b88a853?auto=format&fit=crop&w=900&q=80', 1, 0),
  (6, 1, '降噪蓝牙耳机', '主动降噪，通勤学习都适合，续航稳定。', 699.00, 60, 260, 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=900&q=80', 1, 0),
  (7, 1, '运动智能手表', '心率、睡眠、运动记录，7 天续航。', 899.00, 50, 150, 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=900&q=80', 1, 0),
  (8, 5, '65W 氮化镓充电器', '三口快充，适合手机、平板和轻薄电脑。', 129.00, 150, 410, 'https://images.unsplash.com/photo-1603539444875-76e7684265f6?auto=format&fit=crop&w=900&q=80', 1, 0),
  (9, 5, '编织数据线套装', 'USB-C 双头快充线，编织外被，耐弯折。', 39.00, 300, 800, 'https://images.unsplash.com/photo-1492107376256-4026437926cd?auto=format&fit=crop&w=900&q=80', 1, 0),
  (10, 6, '智能台灯 Pro', '护眼照明，手机 App 控制，学习办公适用。', 259.00, 70, 170, 'https://images.unsplash.com/photo-1507473885765-e6ed057f782c?auto=format&fit=crop&w=900&q=80', 1, 0),
  (11, 6, '智能插座 Mini', '远程开关，电量统计，定时任务。', 59.00, 180, 390, 'https://images.unsplash.com/photo-1558618666-fcd25c85cd64?auto=format&fit=crop&w=900&q=80', 1, 0),
  (12, 3, '人体工学椅', '腰托可调，适合长时间办公和学习。', 799.00, 32, 90, 'https://images.unsplash.com/photo-1580480055273-228ff5388ef8?auto=format&fit=crop&w=900&q=80', 1, 0),
  (13, 3, '升降电脑桌', '电动升降，记忆高度，办公学习适用。', 1299.00, 20, 74, 'https://images.unsplash.com/photo-1518455027359-f3f8164ba6bd?auto=format&fit=crop&w=900&q=80', 1, 0),
  (14, 4, '热插拔客制化键盘', 'Gasket 结构，RGB 灯效，适合键盘爱好者。', 599.00, 40, 230, 'https://images.unsplash.com/photo-1618384887929-16ec33fab9ef?auto=format&fit=crop&w=900&q=80', 1, 0),
  (15, 4, '入门薄膜键盘', '安静耐用，适合预算敏感用户和宿舍场景。', 69.00, 200, 620, 'https://images.unsplash.com/photo-1589578228447-e1a4e481c6c8?auto=format&fit=crop&w=900&q=80', 1, 0),
  (16, 1, '便携蓝牙音箱', '小体积大音量，露营和宿舍适用。', 239.00, 85, 160, 'https://images.unsplash.com/photo-1608043152269-423dbba4e7e1?auto=format&fit=crop&w=900&q=80', 1, 0),
  (17, 2, 'USB-C 扩展坞', 'HDMI、网口、读卡器，多接口扩展。', 189.00, 95, 240, 'https://images.unsplash.com/photo-1625842268584-8f3296236761?auto=format&fit=crop&w=900&q=80', 1, 0),
  (18, 3, '桌面收纳架', '整理显示器和键盘区域，提升桌面空间。', 119.00, 110, 140, 'https://images.unsplash.com/photo-1497366754035-f200968a6e72?auto=format&fit=crop&w=900&q=80', 1, 0),
  (19, 6, '扫地机器人 Lite', '自动清扫，智能避障，适合小户型。', 1099.00, 18, 65, 'https://images.unsplash.com/photo-1563453392212-326f5e854473?auto=format&fit=crop&w=900&q=80', 1, 0),
  (20, 5, '磁吸充电宝', '10000mAh，轻薄便携，支持无线充。', 169.00, 130, 350, 'https://images.unsplash.com/photo-1609091839311-d5365f9ff1c5?auto=format&fit=crop&w=900&q=80', 1, 0),
  (21, 2, '抢购测试商品', '用于 100 并发购买 80 库存验收。', 9.90, 80, 0, 'https://images.unsplash.com/photo-1526947425960-945c6e72858f?auto=format&fit=crop&w=900&q=80', 1, 0),
  (22, 13, '纯棉基础款 T 恤', '重磅纯棉面料，日常通勤和校园穿搭都适合。', 79.00, 180, 260, 'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?auto=format&fit=crop&w=900&q=80', 1, 0),
  (23, 13, '轻薄防晒外套', 'UPF50+ 防晒，轻便可收纳，适合春夏出行。', 199.00, 95, 180, 'https://images.unsplash.com/photo-1543076447-215ad9ba6923?auto=format&fit=crop&w=900&q=80', 1, 0),
  (24, 13, '直筒休闲牛仔裤', '弹力牛仔面料，版型利落，适合日常搭配。', 229.00, 75, 145, 'https://images.unsplash.com/photo-1542272604-787c3835535d?auto=format&fit=crop&w=900&q=80', 1, 0),
  (25, 14, '城市通勤双肩包', '15.6 英寸电脑仓，多隔层收纳，防泼水面料。', 269.00, 68, 210, 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?auto=format&fit=crop&w=900&q=80', 1, 0),
  (26, 14, '缓震跑步鞋 AirRun', '轻量缓震中底，适合慢跑和健身房训练。', 399.00, 88, 170, 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=900&q=80', 1, 0),
  (27, 14, '真皮短款钱包', '头层牛皮，小巧耐用，支持卡位分区。', 159.00, 120, 90, 'https://images.unsplash.com/photo-1627123424574-724758594e93?auto=format&fit=crop&w=900&q=80', 1, 0),
  (28, 15, '氨基酸洁面乳', '温和清洁，适合学生和通勤人群日常使用。', 69.00, 150, 300, 'https://images.unsplash.com/photo-1556228578-8c89e6adf883?auto=format&fit=crop&w=900&q=80', 1, 0),
  (29, 15, '玻尿酸保湿精华', '轻薄易吸收，适合换季干燥和熬夜护理。', 129.00, 90, 220, 'https://images.unsplash.com/photo-1620916566398-39f1143ab7be?auto=format&fit=crop&w=900&q=80', 1, 0),
  (30, 15, '持妆粉底液', '自然雾面妆效，适合通勤和拍照场景。', 189.00, 60, 135, 'https://images.unsplash.com/photo-1596462502278-27bfdc403348?auto=format&fit=crop&w=900&q=80', 1, 0),
  (31, 16, '电动牙刷 CleanPro', '五档清洁模式，智能计时，适合全家使用。', 239.00, 110, 280, 'https://images.unsplash.com/photo-1609840114035-3c981b782dfe?auto=format&fit=crop&w=900&q=80', 1, 0),
  (32, 16, '高速吹风机', '大风量速干，恒温护发，低噪音设计。', 499.00, 45, 155, 'https://images.unsplash.com/photo-1522338140262-f46f5913618a?auto=format&fit=crop&w=900&q=80', 1, 0),
  (33, 16, '筋膜按摩仪 Mini', '便携放松肩颈腿部，适合运动后恢复。', 299.00, 70, 120, 'https://images.unsplash.com/photo-1518611012118-696072aa579a?auto=format&fit=crop&w=900&q=80', 1, 0),
  (34, 17, '可调节哑铃套装', '2.5kg 到 20kg 快速切换，适合家庭训练。', 699.00, 32, 80, 'https://images.unsplash.com/photo-1583454110551-21f2fa2afe61?auto=format&fit=crop&w=900&q=80', 1, 0),
  (35, 17, '防滑瑜伽垫', '加厚 TPE 材质，防滑回弹，适合初学者。', 89.00, 160, 260, 'https://images.unsplash.com/photo-1599901860904-17e6ed7083a0?auto=format&fit=crop&w=900&q=80', 1, 0),
  (36, 17, '运动水壶 700ml', 'Tritan 材质，单手开盖，健身通勤可用。', 49.00, 220, 340, 'https://images.unsplash.com/photo-1602143407151-7111542de6e8?auto=format&fit=crop&w=900&q=80', 1, 0),
  (37, 18, '轻量露营帐篷', '双人三季帐，防水外帐，适合周末露营。', 599.00, 36, 76, 'https://images.unsplash.com/photo-1504280390367-361c6d9f38f4?auto=format&fit=crop&w=900&q=80', 1, 0),
  (38, 18, '便携折叠椅', '铝合金支架，收纳小，露营钓鱼都适合。', 139.00, 95, 190, 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=900&q=80', 1, 0),
  (39, 18, '户外保温杯', '316 不锈钢，长效保温，徒步和通勤可用。', 99.00, 130, 210, 'https://images.unsplash.com/photo-1523362628745-0c100150b504?auto=format&fit=crop&w=900&q=80', 1, 0),
  (40, 19, 'Spring Boot 实战手册', '覆盖 REST API、JWT、安全认证和部署实践。', 89.00, 100, 180, 'https://images.unsplash.com/photo-1516979187457-637abb4f9353?auto=format&fit=crop&w=900&q=80', 1, 0),
  (41, 19, 'Vue3 组件化开发指南', '组合式 API、状态管理、前端工程化实战。', 79.00, 90, 150, 'https://images.unsplash.com/photo-1515879218367-8466d910aaa4?auto=format&fit=crop&w=900&q=80', 1, 0),
  (42, 19, 'AI Agent 应用开发入门', 'LangChain 工具调用、记忆机制和商城助手案例。', 99.00, 70, 240, 'https://images.unsplash.com/photo-1456513080510-7bf3a84b82f8?auto=format&fit=crop&w=900&q=80', 1, 0),
  (43, 20, '点阵手账本套装', '含贴纸、索引页和计划页，适合学习计划管理。', 59.00, 180, 260, 'https://images.unsplash.com/photo-1517842645767-c639042777db?auto=format&fit=crop&w=900&q=80', 1, 0),
  (44, 20, '低重心中性笔 6 支装', '顺滑速干，适合课堂笔记和办公签字。', 29.00, 260, 420, 'https://images.unsplash.com/photo-1583485088034-697b5bc54ccd?auto=format&fit=crop&w=900&q=80', 1, 0),
  (45, 20, '桌面文件收纳盒', '多层文件分区，适合课程资料和发票整理。', 49.00, 140, 130, 'https://images.unsplash.com/photo-1456735190827-d1262f71b8a3?auto=format&fit=crop&w=900&q=80', 1, 0),
  (46, 5, '折叠手机支架', '铝合金折叠结构，桌面追剧和视频会议适合。', 49.00, 180, 360, 'https://images.unsplash.com/photo-1516245556508-7d60d4ff0f39?auto=format&fit=crop&w=900&q=80', 1, 0),
  (47, 5, '无线充电板 15W', 'Qi 磁吸定位，防滑布面，支持手机和耳机补电。', 99.00, 120, 260, 'https://images.unsplash.com/photo-1545235616-db3cd822ad8c?auto=format&fit=crop&w=900&q=80', 1, 0),
  (48, 2, '2K 自动对焦摄像头', '2K 自动对焦，内置降噪麦克风，会议直播两用。', 299.00, 75, 190, 'https://images.unsplash.com/photo-1715869618915-a7bf6608d4c3?auto=format&fit=crop&w=900&q=80', 1, 0),
  (49, 3, '人体工学脚踏', '防滑按摩凸点，三档倾角调节，缓解久坐腿部压力。', 139.00, 95, 145, 'https://www.mount-it.com/cdn/shop/files/under-desk-ergonomic-footrest-black-mount-it-mi-7803-39688144879771.jpg?v=1763595989&width=900', 1, 0),
  (50, 4, '无线双模鼠标 M3', '蓝牙和 2.4G 双模连接，低噪微动，轻办公长续航。', 129.00, 150, 330, 'https://images.unsplash.com/photo-1527864550417-7fd91fc51a46?auto=format&fit=crop&w=900&q=80', 1, 0),
  (51, 6, '智能门锁 S1', '指纹、密码、手机 App 多方式解锁，支持临时访客密码。', 899.00, 35, 88, 'https://images.unsplash.com/photo-1558002038-1055907df827?auto=format&fit=crop&w=900&q=80', 1, 0),
  (52, 6, '桌面香薰加湿器', '细雾补水，低噪运行，适合卧室和工作桌。', 159.00, 110, 240, 'https://images.unsplash.com/photo-1768471569643-717e823b5f9a?auto=format&fit=crop&w=900&q=80', 1, 0),
  (53, 3, '亚麻抱枕套两只装', '亚麻混纺面料，隐藏拉链，客厅卧室都好搭配。', 69.00, 160, 210, 'https://images.unsplash.com/photo-1531877025030-f7696a50770f?auto=format&fit=crop&w=900&q=80', 1, 0),
  (54, 13, '速干训练短袖', '速干排汗面料，跑步和健身房训练适用。', 99.00, 140, 300, 'https://images.unsplash.com/photo-1581655353564-df123a1eb820?auto=format&fit=crop&w=900&q=80', 1, 0),
  (55, 14, '轻量越野背包 18L', '18L 轻量容量，水壶位和多口袋分区，短途徒步可用。', 239.00, 70, 170, 'https://images.unsplash.com/photo-1509762774605-f07235a08f1f?auto=format&fit=crop&w=900&q=80', 1, 0),
  (56, 15, '维C亮肤面膜 10片', '维C亮肤配方，清爽补水，适合熬夜后的基础护理。', 79.00, 180, 380, 'https://images.unsplash.com/photo-1670201203270-7bc9b329d2eb?auto=format&fit=crop&w=900&q=80', 1, 0),
  (57, 16, '便携冲牙器', '三档水压，可拆水箱，旅行收纳友好。', 199.00, 85, 230, 'https://uwhitening.ca/cdn/shop/files/The_Best_Waterflosser_In_Canada.jpg?v=1742413110&width=900', 1, 0),
  (58, 17, '计数跳绳 Pro', '电子计数，防滑手柄，适合居家燃脂。', 59.00, 220, 420, 'https://images.unsplash.com/photo-1516876345887-6dd74f80787a?auto=format&fit=crop&w=900&q=80', 1, 0),
  (59, 18, '折叠野餐垫', '防潮底层，大尺寸可机洗，露营野餐适用。', 129.00, 100, 160, 'https://images.unsplash.com/photo-1731186622228-38f68d3f64ad?auto=format&fit=crop&w=900&q=80', 1, 0),
  (60, 20, '线圈计划本', '周计划和月计划页面，可平摊书写，适合课程与项目管理。', 39.00, 240, 310, 'https://images.unsplash.com/photo-1632772998001-cc9bf6f7c852?auto=format&fit=crop&w=900&q=80', 1, 0)
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

INSERT INTO `order` (id, order_no, user_id, total_amount, discount_amount, points_used, status, shipping_address, created_at, paid_at)
VALUES
  (1, 'SM202606300001', 2, 399.00, 0.00, 0, 'PAID', '上海市浦东新区软件园 1 号楼', '2026-06-30 10:00:00', '2026-06-30 10:05:00'),
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
  (1, 1, 1, '极客机械键盘 K87', 399.00, 1),
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

INSERT INTO product_review (id, product_id, user_id, order_id, rating, content, created_at)
VALUES
  (1, 14, 2, 2, 5, 'Gasket 手感扎实，热插拔换轴很方便，适合键盘爱好者。', '2026-07-03 09:30:00'),
  (2, 44, 2, 3, 5, '笔身重心稳定，长时间记笔记不累，黑色外观也耐看。', '2026-07-03 11:15:00'),
  (3, 9, 3, 4, 4, '线材比普通数据线厚实，充电和传输都稳定。', '2026-07-03 13:40:00'),
  (4, 1, 3, 5, 5, '三模连接切换顺畅，PBT 键帽手感干爽，敲代码很舒服。', '2026-07-03 16:05:00'),
  (5, 15, 2, 6, 4, '预算有限时很合适，声音比机械键盘轻，宿舍晚上用不吵。', '2026-07-04 10:20:00')
ON DUPLICATE KEY UPDATE
  product_id = VALUES(product_id),
  user_id = VALUES(user_id),
  order_id = VALUES(order_id),
  rating = VALUES(rating),
  content = VALUES(content),
  created_at = VALUES(created_at);

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

INSERT INTO shipping_address (id, user_id, receiver_name, phone, province, city, district, detail_address, is_default)
VALUES
  (1, 2, 'Alice', '13800000001', '上海市', '上海市', '浦东新区', '软件园 1 号楼 801', 1),
  (2, 2, 'Alice', '13800000001', '北京市', '北京市', '海淀区', '实训中心 1 号楼', 0),
  (3, 3, 'Bob', '13800000002', '浙江省', '杭州市', '西湖区', '文三路 88 号', 1)
ON DUPLICATE KEY UPDATE
  receiver_name = VALUES(receiver_name),
  phone = VALUES(phone),
  province = VALUES(province),
  city = VALUES(city),
  district = VALUES(district),
  detail_address = VALUES(detail_address),
  is_default = VALUES(is_default);

INSERT INTO product_view_log (id, user_id, product_id, viewed_at)
VALUES
  (1, 2, 1, '2026-07-06 09:00:00'),
  (2, 2, 14, '2026-07-06 09:05:00'),
  (3, 2, 50, '2026-07-06 09:12:00'),
  (4, NULL, 6, '2026-07-06 10:00:00')
ON DUPLICATE KEY UPDATE
  user_id = VALUES(user_id),
  product_id = VALUES(product_id),
  viewed_at = VALUES(viewed_at);

INSERT INTO banner_slot (product_id, sort_order, is_active)
SELECT ranked.id, ranked.rn - 1, 1
FROM (
  SELECT id, ROW_NUMBER() OVER (ORDER BY sales_count DESC, id DESC) AS rn
  FROM product
  WHERE status = 1
  ORDER BY sales_count DESC, id DESC
  LIMIT 3
) ranked
WHERE NOT EXISTS (SELECT 1 FROM banner_slot);

INSERT INTO search_keyword_log (id, keyword, user_id, is_blocked, searched_at)
VALUES
  (1, '机械键盘', 2, 0, '2026-07-06 11:00:00'),
  (2, '键盘', 2, 0, '2026-07-06 11:05:00'),
  (3, '显示器', NULL, 0, '2026-07-06 12:00:00'),
  (4, '机械键盘', NULL, 0, '2026-07-06 12:20:00'),
  (5, '露营', 3, 0, '2026-07-06 13:20:00')
ON DUPLICATE KEY UPDATE
  keyword = VALUES(keyword),
  user_id = VALUES(user_id),
  is_blocked = VALUES(is_blocked),
  searched_at = VALUES(searched_at);

INSERT INTO product_tag (id, name)
VALUES
  (1, '新品'),
  (2, '限量'),
  (3, '爆款')
ON DUPLICATE KEY UPDATE
  name = VALUES(name);

INSERT IGNORE INTO product_tag_relation (product_id, tag_id)
VALUES
  (1, 3),
  (14, 2),
  (42, 1),
  (50, 1),
  (58, 3);

INSERT INTO admin_operation_log (id, admin_id, action, target_type, target_id, detail, created_at)
VALUES
  (1, 1, 'product.update', 'product', 1, '演示数据：调整商品库存和描述', '2026-07-06 15:00:00')
ON DUPLICATE KEY UPDATE
  admin_id = VALUES(admin_id),
  action = VALUES(action),
  target_type = VALUES(target_type),
  target_id = VALUES(target_id),
  detail = VALUES(detail),
  created_at = VALUES(created_at);
