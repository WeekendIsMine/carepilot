-- 固定客户端字符集：否则用 mysql CLI 导入时中文会变成乱码（分类名、昵称等种子数据先中招）
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS carepilot DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE carepilot;

DROP TABLE IF EXISTS mall_cart_item;
DROP TABLE IF EXISTS mall_address;
DROP TABLE IF EXISTS agent_tool_log;
DROP TABLE IF EXISTS kb_chunk;
DROP TABLE IF EXISTS kb_document;
DROP TABLE IF EXISTS after_sale_ticket_log;
DROP TABLE IF EXISTS after_sale_ticket;
DROP TABLE IF EXISTS chat_approval;
DROP TABLE IF EXISTS chat_message;
DROP TABLE IF EXISTS chat_session;
DROP TABLE IF EXISTS logistics_trace;
DROP TABLE IF EXISTS order_item;
DROP TABLE IF EXISTS order_main;
DROP TABLE IF EXISTS catalog_product;
DROP TABLE IF EXISTS catalog_category;
DROP TABLE IF EXISTS sys_user;

-- 1. 用户（顾客 / 客服 / 管理员）
CREATE TABLE sys_user (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  username      VARCHAR(64)  NOT NULL,
  password      VARCHAR(100) NOT NULL COMMENT 'BCrypt',
  nickname      VARCHAR(64)  NOT NULL,
  role          VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT 'USER/AGENT/ADMIN',
  phone         VARCHAR(32)           DEFAULT NULL,
  avatar        VARCHAR(255)          DEFAULT NULL,
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  agent_status  VARCHAR(16)  NOT NULL DEFAULT 'OFFLINE' COMMENT 'ONLINE/BUSY/OFFLINE',
  max_concurrent INT         NOT NULL DEFAULT 5,
  last_login_at DATETIME              DEFAULT NULL,
  deleted       TINYINT      NOT NULL DEFAULT 0,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB COMMENT='用户';

-- 2. 商品类目
CREATE TABLE catalog_category (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  name       VARCHAR(64) NOT NULL,
  parent_id  BIGINT      NOT NULL DEFAULT 0,
  sort       INT         NOT NULL DEFAULT 0,
  deleted    TINYINT     NOT NULL DEFAULT 0,
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB COMMENT='商品类目';

-- 3. 商品（商城展示 + 为 Agent 提供售后上下文）
CREATE TABLE catalog_product (
  id             BIGINT        NOT NULL AUTO_INCREMENT,
  category_id    BIGINT        NOT NULL,
  name           VARCHAR(128)  NOT NULL,
  subtitle       VARCHAR(255)           DEFAULT NULL,
  description    TEXT                            COMMENT '商品详情',
  cover_url      VARCHAR(512)           DEFAULT NULL,
  gallery        JSON                            COMMENT '详情图 URL 数组',
  price          DECIMAL(10,2) NOT NULL DEFAULT 0,
  original_price DECIMAL(10,2)          DEFAULT NULL,
  stock          INT           NOT NULL DEFAULT 0,
  return_days    INT           NOT NULL DEFAULT 7 COMMENT '无理由退货天数',
  status         TINYINT       NOT NULL DEFAULT 1,
  deleted        TINYINT       NOT NULL DEFAULT 0,
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_category (category_id)
) ENGINE=InnoDB COMMENT='商品';

-- 4. 订单
CREATE TABLE order_main (
  id               BIGINT        NOT NULL AUTO_INCREMENT,
  order_no         VARCHAR(32)   NOT NULL,
  user_id          BIGINT        NOT NULL,
  total_amount     DECIMAL(10,2) NOT NULL DEFAULT 0,
  pay_amount       DECIMAL(10,2) NOT NULL DEFAULT 0,
  status           TINYINT       NOT NULL DEFAULT 0 COMMENT '0待付款 1待发货 2已发货 3已完成 4已取消 5售后中',
  receiver_name    VARCHAR(64)            DEFAULT NULL,
  receiver_phone   VARCHAR(32)            DEFAULT NULL,
  receiver_address VARCHAR(255)           DEFAULT NULL,
  pay_time         DATETIME               DEFAULT NULL,
  ship_time        DATETIME               DEFAULT NULL,
  receive_time     DATETIME               DEFAULT NULL,
  remark           VARCHAR(500)           DEFAULT NULL,
  deleted          TINYINT       NOT NULL DEFAULT 0,
  created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='订单';

-- 5. 订单明细
CREATE TABLE order_item (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  order_id      BIGINT        NOT NULL,
  order_no      VARCHAR(32)   NOT NULL,
  product_id    BIGINT        NOT NULL,
  product_name  VARCHAR(128)  NOT NULL,
  product_cover VARCHAR(512)           DEFAULT NULL,
  unit_price    DECIMAL(10,2) NOT NULL DEFAULT 0,
  quantity      INT           NOT NULL DEFAULT 1,
  return_days   INT                    DEFAULT NULL COMMENT '下单时的无理由退货天数快照',
  amount        DECIMAL(10,2) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_order (order_id)
) ENGINE=InnoDB COMMENT='订单明细';

-- 6. 物流轨迹
CREATE TABLE logistics_trace (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  order_no     VARCHAR(32)  NOT NULL,
  carrier      VARCHAR(32)           DEFAULT NULL,
  tracking_no  VARCHAR(64)           DEFAULT NULL,
  status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0待揽收 1运输中 2派送中 3已签收',
  current_info VARCHAR(255)          DEFAULT NULL,
  traces       JSON                  DEFAULT NULL,
  signed_time  DATETIME              DEFAULT NULL,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no)
) ENGINE=InnoDB COMMENT='物流轨迹';

-- 7. 会话
CREATE TABLE chat_session (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  session_no        VARCHAR(32)  NOT NULL,
  user_id           BIGINT       NOT NULL,
  agent_id          BIGINT                DEFAULT NULL,
  status            VARCHAR(24)  NOT NULL DEFAULT 'AI' COMMENT 'AI/WAITING/AGENT/CLOSED',
  transfer_reason   VARCHAR(255)          DEFAULT NULL,
  priority          INT          NOT NULL DEFAULT 0,
  last_message      VARCHAR(255)          DEFAULT NULL,
  last_message_at   DATETIME              DEFAULT NULL,
  unread_for_agent  INT          NOT NULL DEFAULT 0,
  ai_summary        JSON                  DEFAULT NULL,
  ticket_id         BIGINT                DEFAULT NULL,
  version           INT          NOT NULL DEFAULT 0,
  deleted           TINYINT      NOT NULL DEFAULT 0,
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_session_no (session_no),
  KEY idx_user (user_id),
  KEY idx_status (status)
) ENGINE=InnoDB COMMENT='会话';

-- 8. 消息
CREATE TABLE chat_message (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  session_id    BIGINT      NOT NULL,
  role          VARCHAR(16) NOT NULL COMMENT 'USER/AI/AGENT/SYSTEM/TOOL',
  content       MEDIUMTEXT           DEFAULT NULL,
  tool_calls    JSON                 DEFAULT NULL,
  citations     JSON                 DEFAULT NULL,
  client_msg_id VARCHAR(64)          DEFAULT NULL,
  latency_ms    INT                  DEFAULT NULL,
  deleted       TINYINT     NOT NULL DEFAULT 0,
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  prompt_version VARCHAR(16)           DEFAULT NULL COMMENT '生成该条回复的提示词版本',
  PRIMARY KEY (id),
  UNIQUE KEY uk_client_msg (session_id, client_msg_id),
  KEY idx_session (session_id, id)
) ENGINE=InnoDB COMMENT='消息';

-- 9. 人工审批（Human-in-the-loop）：写操作必须用户/坐席确认才执行
CREATE TABLE chat_approval (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  approval_no VARCHAR(32)  NOT NULL,
  session_id  BIGINT       NOT NULL,
  user_id     BIGINT       NOT NULL,
  tool_name   VARCHAR(64)  NOT NULL,
  tool_args   JSON                  DEFAULT NULL,
  status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/EXPIRED',
  expire_at   DATETIME     NOT NULL,
  decided_at  DATETIME              DEFAULT NULL,
  result      JSON                  DEFAULT NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_approval_no (approval_no),
  KEY idx_session (session_id),
  KEY idx_status (status, expire_at)
) ENGINE=InnoDB COMMENT='人工审批';

-- 10. 售后工单
CREATE TABLE after_sale_ticket (
  id          BIGINT        NOT NULL AUTO_INCREMENT,
  ticket_no   VARCHAR(32)   NOT NULL,
  order_no    VARCHAR(32)   NOT NULL,
  user_id     BIGINT        NOT NULL,
  session_id  BIGINT                 DEFAULT NULL,
  type        VARCHAR(16)   NOT NULL COMMENT 'REFUND/RETURN/EXCHANGE/COMPLAINT',
  reason      VARCHAR(64)            DEFAULT NULL,
  description VARCHAR(500)           DEFAULT NULL,
  status      VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/REFUNDING/DONE/CLOSED',
  source      VARCHAR(16)   NOT NULL DEFAULT 'USER' COMMENT 'AI/USER/AGENT',
  amount      DECIMAL(10,2)          DEFAULT NULL,
  images      JSON                   DEFAULT NULL,
  version     INT           NOT NULL DEFAULT 0,
  deleted     TINYINT       NOT NULL DEFAULT 0,
  created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ticket_no (ticket_no),
  KEY idx_user (user_id),
  KEY idx_order (order_no),
  KEY idx_status (status)
) ENGINE=InnoDB COMMENT='售后工单';

-- 11. 工单流转日志
CREATE TABLE after_sale_ticket_log (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  ticket_id     BIGINT      NOT NULL,
  from_status   VARCHAR(16)          DEFAULT NULL,
  to_status     VARCHAR(16)          DEFAULT NULL,
  action        VARCHAR(32) NOT NULL,
  operator_id   BIGINT               DEFAULT NULL,
  operator_role VARCHAR(16)          DEFAULT NULL,
  remark        VARCHAR(500)         DEFAULT NULL,
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ticket (ticket_id)
) ENGINE=InnoDB COMMENT='工单流转日志';

-- 12. 知识文档
CREATE TABLE kb_document (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  title          VARCHAR(255) NOT NULL,
  category       VARCHAR(64)           DEFAULT NULL COMMENT '售后类型/商品类目，用于元数据过滤',
  source_type    VARCHAR(16)  NOT NULL DEFAULT 'UPLOAD' COMMENT 'UPLOAD/GENERATED',
  file_url       VARCHAR(512)          DEFAULT NULL COMMENT '本地原件路径（解析用）',
  oss_url        VARCHAR(512)          DEFAULT NULL COMMENT '对象存储地址（在线查看用）',
  file_name      VARCHAR(255)          DEFAULT NULL,
  file_hash      VARCHAR(64)           DEFAULT NULL COMMENT '文件内容 SHA-256，用于去重',
  effective_from DATE                  DEFAULT NULL,
  effective_to   DATE                  DEFAULT NULL,
  status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PARSING/EMBEDDING/READY/FAILED',
  chunk_count    INT          NOT NULL DEFAULT 0,
  parse_error    VARCHAR(500)          DEFAULT NULL,
  created_by     BIGINT                DEFAULT NULL,
  deleted        TINYINT      NOT NULL DEFAULT 0,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_file_hash (file_hash),
  KEY idx_status (status)
) ENGINE=InnoDB COMMENT='知识文档';

-- 13. 知识分块（父子块：子块命中，回填 parent_content）
CREATE TABLE kb_chunk (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  document_id     BIGINT       NOT NULL,
  chunk_no        INT          NOT NULL DEFAULT 0,
  parent_no       INT          NOT NULL DEFAULT 0,
  title_path      VARCHAR(255)          DEFAULT NULL,
  content         MEDIUMTEXT   NOT NULL,
  parent_content  MEDIUMTEXT            DEFAULT NULL,
  token_count     INT          NOT NULL DEFAULT 0,
  embedding_model VARCHAR(64)           DEFAULT NULL,
  deleted         TINYINT      NOT NULL DEFAULT 0,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_document (document_id)
) ENGINE=InnoDB COMMENT='知识分块';

-- 14. Agent 工具调用审计
CREATE TABLE agent_tool_log (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  session_id BIGINT               DEFAULT NULL,
  message_id BIGINT               DEFAULT NULL,
  tool_name  VARCHAR(64) NOT NULL,
  arguments  JSON                DEFAULT NULL,
  result     JSON                DEFAULT NULL,
  success    TINYINT     NOT NULL DEFAULT 1,
  cost_ms    INT                  DEFAULT NULL,
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_session (session_id),
  KEY idx_tool (tool_name)
) ENGINE=InnoDB COMMENT='工具调用审计';

-- 15. 收货地址
CREATE TABLE mall_address (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  user_id        BIGINT       NOT NULL,
  receiver_name  VARCHAR(64)  NOT NULL,
  receiver_phone VARCHAR(32)  NOT NULL,
  province       VARCHAR(32)           DEFAULT NULL,
  city           VARCHAR(32)           DEFAULT NULL,
  district       VARCHAR(32)           DEFAULT NULL,
  detail_address VARCHAR(255) NOT NULL,
  is_default     TINYINT      NOT NULL DEFAULT 0,
  deleted        TINYINT      NOT NULL DEFAULT 0,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='收货地址';

-- 16. 购物车
CREATE TABLE mall_cart_item (
  id         BIGINT   NOT NULL AUTO_INCREMENT,
  user_id    BIGINT   NOT NULL,
  product_id BIGINT   NOT NULL,
  quantity   INT      NOT NULL DEFAULT 1,
  selected   TINYINT  NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_product (user_id, product_id)
) ENGINE=InnoDB COMMENT='购物车';

-- 17. 支付单
CREATE TABLE IF NOT EXISTS pay_record (
  id         BIGINT        NOT NULL AUTO_INCREMENT,
  pay_no     VARCHAR(32)   NOT NULL,
  order_no   VARCHAR(32)   NOT NULL,
  user_id    BIGINT        NOT NULL,
  channel    VARCHAR(16)   NOT NULL COMMENT 'BALANCE余额 / ONLINE在线支付',
  amount     DECIMAL(10,2) NOT NULL,
  status     VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/FAILED/CLOSED',
  paid_at    DATETIME               DEFAULT NULL,
  created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pay_no (pay_no),
  KEY idx_order (order_no),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='支付单';

-- 18. 退款单
CREATE TABLE IF NOT EXISTS refund_record (
  id          BIGINT        NOT NULL AUTO_INCREMENT,
  refund_no   VARCHAR(32)   NOT NULL,
  ticket_no   VARCHAR(32)   NOT NULL,
  order_no    VARCHAR(32)   NOT NULL,
  pay_no      VARCHAR(32)            DEFAULT NULL,
  user_id     BIGINT        NOT NULL,
  channel     VARCHAR(16)   NOT NULL COMMENT '原路退回：BALANCE/ONLINE',
  amount      DECIMAL(10,2) NOT NULL,
  status      VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/FAILED',
  refunded_at DATETIME               DEFAULT NULL,
  created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_refund_no (refund_no),
  KEY idx_ticket (ticket_no),
  KEY idx_order (order_no)
) ENGINE=InnoDB COMMENT='退款单';

-- 19. 余额钱包
CREATE TABLE IF NOT EXISTS wallet (
  id         BIGINT        NOT NULL AUTO_INCREMENT,
  user_id    BIGINT        NOT NULL,
  balance    DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user (user_id)
) ENGINE=InnoDB COMMENT='余额钱包';

-- 20. 余额流水（只追加，不改不删）
CREATE TABLE IF NOT EXISTS wallet_ledger (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  user_id       BIGINT        NOT NULL,
  change_type   VARCHAR(16)   NOT NULL COMMENT 'RECHARGE充值 / PAY支付 / REFUND退款',
  amount        DECIMAL(12,2) NOT NULL COMMENT '正数入账，负数出账',
  balance_after DECIMAL(12,2) NOT NULL COMMENT '变动后余额快照',
  biz_no        VARCHAR(64)            DEFAULT NULL COMMENT '关联单号',
  remark        VARCHAR(255)           DEFAULT NULL,
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id),
  KEY idx_biz (biz_no)
) ENGINE=InnoDB COMMENT='余额流水';

-- 21. 解析失败记录（死信队列的落地，供后台补偿）
CREATE TABLE IF NOT EXISTS kb_ingest_failure (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  document_id    BIGINT       NOT NULL,
  document_title VARCHAR(255)          DEFAULT NULL,
  error          VARCHAR(500)          DEFAULT NULL,
  attempts       INT          NOT NULL DEFAULT 1,
  resolved       TINYINT      NOT NULL DEFAULT 0,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_doc (document_id),
  KEY idx_resolved (resolved)
) ENGINE=InnoDB COMMENT='知识库解析失败（死信）';

-- 商城分类（必需参照数据：商品要挂在分类上，前端筛选用）
INSERT INTO catalog_category (id, name, parent_id, sort) VALUES
 (1, '数码家电', 0, 1),
 (2, '手机配件', 0, 2),
 (3, '服饰鞋包', 0, 3);

-- 唯一初始账号：admin / admin123（顾客请自行注册，客服账号由管理员创建）
INSERT INTO sys_user (id, username, password, nickname, role, phone, status, agent_status, max_concurrent) VALUES
 (1, 'admin', '$2a$10$ByZmV3itP9k.EhuWFNTWq.t6ODVqqZiHJi1pnBQfxLrV90umG5p3i', '系统管理员', 'ADMIN', '13800000001', 1, 'OFFLINE', 5);