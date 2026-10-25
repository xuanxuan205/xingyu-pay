CREATE TABLE IF NOT EXISTS t_order (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  create_time TIMESTAMP,
  custom BOOLEAN,
  device VARCHAR(1000),
  email VARCHAR(255),
  description VARCHAR(255),
  mobile BOOLEAN,
  money DECIMAL(19, 2),
  nick_name VARCHAR(255),
  pay_num VARCHAR(255),
  pay_type VARCHAR(255),
  state INT,
  update_time TIMESTAMP,
  user_id VARCHAR(10),
  product_id BIGINT,
  pay_qr_num INT,
  order_source VARCHAR(20) DEFAULT 'PRODUCT',
  notify_url VARCHAR(1000),
  expire_time TIMESTAMP,
  match_mode VARCHAR(20) DEFAULT 'REMARK',
  actual_amount DECIMAL(19, 2),
  decrement_index INT
);

CREATE INDEX IF NOT EXISTS idx_actual_amount_state ON t_order (actual_amount, state);
CREATE INDEX IF NOT EXISTS idx_pay_num_create_time ON t_order (pay_num, create_time);
