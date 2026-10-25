CREATE TABLE IF NOT EXISTS `t_order` (
  `id` varchar(255) NOT NULL,
  `create_time` datetime DEFAULT NULL,
  `custom` bit(1) DEFAULT NULL,
  `device` varchar(1000) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `mobile` bit(1) DEFAULT NULL,
  `money` decimal(19,2) DEFAULT NULL,
  `nick_name` varchar(255) DEFAULT NULL,
  `pay_num` varchar(255) DEFAULT NULL,
  `pay_type` varchar(255) DEFAULT NULL,
  `state` int(11) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `user_id` varchar(10) DEFAULT NULL,
  `product_id` bigint(20) DEFAULT NULL,
  `pay_qr_num` int(11) DEFAULT NULL,
  `order_source` varchar(20) DEFAULT 'PRODUCT',
  `notify_url` varchar(1000) DEFAULT NULL,
  `expire_time` datetime DEFAULT NULL,
  `match_mode` varchar(20) NOT NULL DEFAULT 'REMARK',
  `actual_amount` decimal(19,2) DEFAULT NULL,
  `decrement_index` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`)
);

CREATE INDEX `idx_actual_amount_state`
  ON `t_order` (`actual_amount`, `state`);

CREATE INDEX `idx_pay_num_create_time`
  ON `t_order` (`pay_num`, `create_time`);
