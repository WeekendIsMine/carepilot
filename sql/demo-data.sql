-- ============================================================================
-- CarePilot 演示数据（可选）
-- ============================================================================
-- 一次灌入一套能直接演示的数据：
--   · 6 个商品（图片已上传到 OSS）
--   · 1 个顾客 zhangwei / 1 个客服 lijing（密码都是 123456）
--   · 1 个收货地址、钱包余额与 3 条流水
--   · 3 笔订单：待付款、已发货、售后中（含订单明细、物流轨迹、支付单）
--
-- 前置：先执行 sql/init.sql（建表 + 创建 admin / admin123）
-- 执行：
--   mysql -h127.0.0.1 -uroot -proot --default-character-set=utf8mb4 < sql/demo-data.sql
--
-- 说明：商品主图引用的是演示环境里已经上传到 OSS 的图片。没有配置 OSS 时图片会裂开，
--       但不影响任何功能，在「管理 → 商品管理」里重新上传主图即可。
-- ============================================================================

SET NAMES utf8mb4;
USE carepilot;

-- 幂等：重复执行前先清掉本文件负责的那部分数据（不动 admin 与知识库）
DELETE FROM pay_record      WHERE user_id = 2;
DELETE FROM logistics_trace WHERE order_no IN ('CP20261101312739','CP20261101077186','CP20261101542775');
DELETE FROM order_item      WHERE order_no IN ('CP20261101312739','CP20261101077186','CP20261101542775');
DELETE FROM order_main      WHERE user_id = 2;
DELETE FROM wallet_ledger   WHERE user_id = 2;
DELETE FROM wallet          WHERE user_id = 2;
DELETE FROM mall_address    WHERE user_id = 2;
DELETE FROM catalog_product WHERE id BETWEEN 1 AND 6;
DELETE FROM sys_user        WHERE id IN (2, 3);

-- ---- 演示账号：顾客 zhangwei、客服 lijing，密码都是 123456 ----
INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `role`, `phone`, `avatar`, `status`, `agent_status`, `max_concurrent`, `last_login_at`, `deleted`, `created_at`, `updated_at`) VALUES (2,'zhangwei','$2a$10$f.szxLzIrVvOjnDdW5i5KOnlezyfMr7BrEfIzrNuwvrYpdYW01sSC','张伟','USER','13900001111',NULL,1,'OFFLINE',5,'2026-11-01 18:54:39',0,'2026-11-01 18:46:37','2026-11-01 18:54:39');
INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `role`, `phone`, `avatar`, `status`, `agent_status`, `max_concurrent`, `last_login_at`, `deleted`, `created_at`, `updated_at`) VALUES (3,'lijing','$2a$10$.iO6BTN0eW.LdxGO/B7iye8mFtyQxFymV58ruNk6zOxigQBfd/Tka','李静','AGENT','13800000002',NULL,1,'ONLINE',5,'2026-11-01 18:55:09',0,'2026-11-01 18:46:37','2026-11-01 18:55:09');

-- ---- 演示商品（图片存放在阿里云 OSS） ----
INSERT INTO `catalog_product` (`id`, `category_id`, `name`, `subtitle`, `description`, `cover_url`, `gallery`, `price`, `original_price`, `stock`, `return_days`, `status`, `deleted`, `created_at`, `updated_at`) VALUES (1,1,'无线降噪耳机 Pro','主动降噪 · 40h 续航 · 双设备连接','支持主动降噪与通透模式，单次续航 10 小时，配合充电盒可达 40 小时。支持双设备无缝切换，通话四麦克风降噪。\n\n包装清单：耳机 ×1、充电盒 ×1、Type-C 线 ×1、耳塞 ×3 对。\n\n保修：整机一年质保，非人为损坏免费维修。','https://carepilot.oss-cn-shenzhen.aliyuncs.com/files/products/cover-2475eece.png',NULL,899.00,1099.00,119,7,1,0,'2026-11-01 18:46:29','2026-11-01 18:46:37');
INSERT INTO `catalog_product` (`id`, `category_id`, `name`, `subtitle`, `description`, `cover_url`, `gallery`, `price`, `original_price`, `stock`, `return_days`, `status`, `deleted`, `created_at`, `updated_at`) VALUES (2,1,'智能音箱 Mini','360° 环绕音 · 语音助手 · 智能家居中控','内置 3 麦克风阵列，5 米内唤醒率 95%。支持蓝牙 5.3 与 Wi-Fi 双连接，可作智能家居中控。\n\n包装清单：音箱 ×1、电源适配器 ×1、说明书 ×1。','https://carepilot.oss-cn-shenzhen.aliyuncs.com/files/products/cover-bbe1c14c.png',NULL,349.00,399.00,88,7,1,0,'2026-11-01 18:46:29','2026-11-01 18:46:29');
INSERT INTO `catalog_product` (`id`, `category_id`, `name`, `subtitle`, `description`, `cover_url`, `gallery`, `price`, `original_price`, `stock`, `return_days`, `status`, `deleted`, `created_at`, `updated_at`) VALUES (3,1,'扫地机器人 S5','激光导航 · 自动集尘 · 扫拖一体','LDS 激光导航，支持地图分区与禁区设置。5200mAh 电池，单次清扫 180㎡。扫拖一体，支持自动集尘与自动回充。\n\n保修：整机两年质保，电池一年。','https://carepilot.oss-cn-shenzhen.aliyuncs.com/files/products/cover-08134068.png',NULL,2499.00,2899.00,35,15,1,0,'2026-11-01 18:46:29','2026-11-01 18:46:37');
INSERT INTO `catalog_product` (`id`, `category_id`, `name`, `subtitle`, `description`, `cover_url`, `gallery`, `price`, `original_price`, `stock`, `return_days`, `status`, `deleted`, `created_at`, `updated_at`) VALUES (4,3,'摇粒绒外套','双面摇粒绒 · 加厚保暖 · 四色可选','280g 双面摇粒绒面料，立领设计，两侧拉链口袋。适合 5~15℃ 环境穿着。\n\n洗涤建议：30℃ 以下机洗，不可漂白。','https://carepilot.oss-cn-shenzhen.aliyuncs.com/files/products/cover-96d06cd4.png',NULL,199.00,299.00,240,7,1,0,'2026-11-01 18:46:30','2026-11-01 18:46:30');
INSERT INTO `catalog_product` (`id`, `category_id`, `name`, `subtitle`, `description`, `cover_url`, `gallery`, `price`, `original_price`, `stock`, `return_days`, `status`, `deleted`, `created_at`, `updated_at`) VALUES (5,2,'磁吸手机壳','MagSafe 磁吸 · PC 硬背板 · 防摔边框','内置磁吸环，兼容 MagSafe 系列配件。PC 硬背板 + TPU 软边框，四角加高防摔。\n\n适用机型请在订单备注中注明。','https://carepilot.oss-cn-shenzhen.aliyuncs.com/files/products/cover-6cf4ddca.png',NULL,79.00,99.00,498,7,1,0,'2026-11-01 18:46:30','2026-11-01 18:46:37');
INSERT INTO `catalog_product` (`id`, `category_id`, `name`, `subtitle`, `description`, `cover_url`, `gallery`, `price`, `original_price`, `stock`, `return_days`, `status`, `deleted`, `created_at`, `updated_at`) VALUES (6,2,'65W 氮化镓充电器','GaN 氮化镓 · 双口输出 · 折叠插脚','单口最高 65W，双口同时输出 45W+20W。支持 PD3.0 / QC4+ 协议，可给笔记本、手机、平板充电。\n\n包装清单：充电器 ×1、说明书 ×1（不含数据线）。','https://carepilot.oss-cn-shenzhen.aliyuncs.com/files/products/cover-a68c0222.png',NULL,129.00,159.00,299,7,1,0,'2026-11-01 18:46:30','2026-11-01 18:46:37');

-- ---- 演示收货地址 ----
INSERT INTO `mall_address` (`id`, `user_id`, `receiver_name`, `receiver_phone`, `province`, `city`, `district`, `detail_address`, `is_default`, `deleted`, `created_at`, `updated_at`) VALUES (1,2,'张伟','13900001111','广西壮族自治区','桂林市','七星区','金鸡路 1 号 桂电花江校区 3 栋 502',1,0,'2026-11-01 18:46:37','2026-11-01 18:46:37');

-- ---- 演示钱包余额 ----
INSERT INTO `wallet` (`id`, `user_id`, `balance`, `created_at`, `updated_at`) VALUES (24,2,1444.00,'2026-11-01 18:46:37','2026-11-01 18:46:37');

-- ---- 钱包流水 ----
INSERT INTO `wallet_ledger` (`id`, `user_id`, `change_type`, `amount`, `balance_after`, `biz_no`, `remark`, `created_at`) VALUES (95,2,'RECHARGE',5000.00,5000.00,NULL,'余额充值','2026-11-01 18:46:37');
INSERT INTO `wallet_ledger` (`id`, `user_id`, `change_type`, `amount`, `balance_after`, `biz_no`, `remark`, `created_at`) VALUES (96,2,'PAY',-1057.00,3943.00,'PY20261101079895','支付订单 CP20261101312739','2026-11-01 18:46:37');
INSERT INTO `wallet_ledger` (`id`, `user_id`, `change_type`, `amount`, `balance_after`, `biz_no`, `remark`, `created_at`) VALUES (97,2,'PAY',-2499.00,1444.00,'PY20261101815951','支付订单 CP20261101077186','2026-11-01 18:46:37');

-- ---- 演示订单：回退后重下的待付款 / 已发货 / 售后中 ----
INSERT INTO `order_main` (`id`, `order_no`, `user_id`, `total_amount`, `pay_amount`, `status`, `receiver_name`, `receiver_phone`, `receiver_address`, `pay_time`, `ship_time`, `receive_time`, `remark`, `deleted`, `created_at`, `updated_at`) VALUES (1,'CP20261101312739',2,1057.00,1057.00,5,'张伟','13900001111','广西壮族自治区桂林市七星区金鸡路 1 号 桂电花江校区 3 栋 502','2026-10-27 18:46:53','2026-10-28 18:46:53','2026-10-30 18:46:53',NULL,0,'2026-10-27 18:46:53','2026-11-01 18:54:50');
INSERT INTO `order_main` (`id`, `order_no`, `user_id`, `total_amount`, `pay_amount`, `status`, `receiver_name`, `receiver_phone`, `receiver_address`, `pay_time`, `ship_time`, `receive_time`, `remark`, `deleted`, `created_at`, `updated_at`) VALUES (2,'CP20261101077186',2,2499.00,2499.00,2,'张伟','13900001111','广西壮族自治区桂林市七星区金鸡路 1 号 桂电花江校区 3 栋 502','2026-10-30 18:46:53','2026-10-31 18:46:53',NULL,NULL,0,'2026-10-30 18:46:53','2026-10-31 18:46:53');
INSERT INTO `order_main` (`id`, `order_no`, `user_id`, `total_amount`, `pay_amount`, `status`, `receiver_name`, `receiver_phone`, `receiver_address`, `pay_time`, `ship_time`, `receive_time`, `remark`, `deleted`, `created_at`, `updated_at`) VALUES (3,'CP20261101542775',2,129.00,129.00,0,'张伟','13900001111','广西壮族自治区桂林市七星区金鸡路 1 号 桂电花江校区 3 栋 502',NULL,NULL,NULL,NULL,0,'2026-11-01 18:46:37','2026-11-01 18:46:37');

-- ---- 订单明细 ----
INSERT INTO `order_item` (`id`, `order_id`, `order_no`, `product_id`, `product_name`, `product_cover`, `unit_price`, `quantity`, `return_days`, `amount`) VALUES (1,1,'CP20261101312739',1,'无线降噪耳机 Pro','https://carepilot.oss-cn-shenzhen.aliyuncs.com/files/products/cover-2475eece.png',899.00,1,7,899.00);
INSERT INTO `order_item` (`id`, `order_id`, `order_no`, `product_id`, `product_name`, `product_cover`, `unit_price`, `quantity`, `return_days`, `amount`) VALUES (2,1,'CP20261101312739',5,'磁吸手机壳','https://carepilot.oss-cn-shenzhen.aliyuncs.com/files/products/cover-6cf4ddca.png',79.00,2,7,158.00);
INSERT INTO `order_item` (`id`, `order_id`, `order_no`, `product_id`, `product_name`, `product_cover`, `unit_price`, `quantity`, `return_days`, `amount`) VALUES (3,2,'CP20261101077186',3,'扫地机器人 S5','https://carepilot.oss-cn-shenzhen.aliyuncs.com/files/products/cover-08134068.png',2499.00,1,15,2499.00);
INSERT INTO `order_item` (`id`, `order_id`, `order_no`, `product_id`, `product_name`, `product_cover`, `unit_price`, `quantity`, `return_days`, `amount`) VALUES (4,3,'CP20261101542775',6,'65W 氮化镓充电器','https://carepilot.oss-cn-shenzhen.aliyuncs.com/files/products/cover-a68c0222.png',129.00,1,7,129.00);

-- ---- 物流轨迹 ----
INSERT INTO `logistics_trace` (`id`, `order_no`, `carrier`, `tracking_no`, `status`, `current_info`, `traces`, `signed_time`, `updated_at`) VALUES (2,'CP20261101077186','京东物流','JD9911223344',1,'快件已到达【桂林集散中心】，正在派送中','[]',NULL,'2026-11-01 18:48:33');
INSERT INTO `logistics_trace` (`id`, `order_no`, `carrier`, `tracking_no`, `status`, `current_info`, `traces`, `signed_time`, `updated_at`) VALUES (1,'CP20261101312739','顺丰速运','SF7788990011',3,'已签收，签收人：本人。感谢使用顺丰速运','[]','2026-10-30 18:46:53','2026-11-01 18:48:33');

-- ---- 支付单 ----
INSERT INTO `pay_record` (`id`, `pay_no`, `order_no`, `user_id`, `channel`, `amount`, `status`, `paid_at`, `created_at`, `updated_at`) VALUES (50,'PY20261101079895','CP20261101312739',2,'BALANCE',1057.00,'SUCCESS','2026-11-01 18:46:38','2026-11-01 18:46:37','2026-11-01 18:46:37');
INSERT INTO `pay_record` (`id`, `pay_no`, `order_no`, `user_id`, `channel`, `amount`, `status`, `paid_at`, `created_at`, `updated_at`) VALUES (51,'PY20261101815951','CP20261101077186',2,'BALANCE',2499.00,'SUCCESS','2026-11-01 18:46:38','2026-11-01 18:46:37','2026-11-01 18:46:37');

