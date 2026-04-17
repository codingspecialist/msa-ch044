-- =====================================================
-- chap044 교육용 DB 초기 스크립트 (슬림 버전)
-- 단순화: 주문 1건 = 상품 1개 (order_item_tb 제거)
-- 추가: processed_message_tb (멱등성 테이블)
-- =====================================================

-- User 테이블
CREATE TABLE user_tb (
  id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) UNIQUE NOT NULL,
  email VARCHAR(50),
  password VARCHAR(50),
  roles VARCHAR(50),
  created_at DATETIME,
  updated_at DATETIME
);

-- Product 테이블
CREATE TABLE product_tb (
  id INT AUTO_INCREMENT PRIMARY KEY,
  product_name VARCHAR(50),
  quantity INT,
  price BIGINT,
  created_at DATETIME,
  updated_at DATETIME
);

-- Order 테이블 (product_id, quantity 포함 - 1주문 1상품)
CREATE TABLE order_tb (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT,
  product_id INT,
  quantity INT,
  status VARCHAR(50),
  created_at DATETIME,
  updated_at DATETIME
);

-- Delivery 테이블
CREATE TABLE delivery_tb (
  id INT AUTO_INCREMENT PRIMARY KEY,
  order_id INT,
  address VARCHAR(100),
  status VARCHAR(50),
  created_at DATETIME,
  updated_at DATETIME
);

-- 멱등성 처리 테이블 (중복 메시지 방지)
-- 각 서비스(product/order/delivery)가 공용 DB에서 공유
-- messageId는 orchestrator가 결정적으로 생성: saga-{orderId}-{action}
CREATE TABLE processed_message_tb (
  message_id VARCHAR(100) PRIMARY KEY,
  service_name VARCHAR(30),
  processed_at DATETIME
);

-- =====================================================
-- 테스트 데이터
-- =====================================================

-- User 3명 (비밀번호 전부 1234)
INSERT INTO user_tb (username, email, password, roles, created_at, updated_at)
VALUES ('ssar','ssar@metacoding.com','1234','USER',now(),now());
INSERT INTO user_tb (username, email, password, roles, created_at, updated_at)
VALUES ('cos','cos@metacoding.com','1234','USER',now(),now());
INSERT INTO user_tb (username, email, password, roles, created_at, updated_at)
VALUES ('love','love@metacoding.com','1234','USER',now(),now());

-- Product 3종
-- id=1 MacBook Pro: 재고 10  → 정상 시나리오
-- id=2 iPhone 15  : 재고 0   → 재고 부족 실패 + 보상 시나리오
-- id=3 AirPods    : 재고 10  → 정상 시나리오
INSERT INTO product_tb (product_name, quantity, price, created_at, updated_at)
VALUES ('MacBook Pro', 10, 2500000, now(), now());
INSERT INTO product_tb (product_name, quantity, price, created_at, updated_at)
VALUES ('iPhone 15', 0, 1300000, now(), now());
INSERT INTO product_tb (product_name, quantity, price, created_at, updated_at)
VALUES ('AirPods', 10, 300000, now(), now());
