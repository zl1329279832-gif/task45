-- 短信发送任务表
CREATE TABLE `x_message_service_task` (
  `task_id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `app_id` int NOT NULL COMMENT '应用ID',
  `request_no` varchar(200) NOT NULL COMMENT '业务请求号',
  `template_code` varchar(200) DEFAULT NULL COMMENT '模板编码',
  `template_id` int DEFAULT NULL COMMENT '模板ID',
  `service_id` int DEFAULT NULL COMMENT '短信服务ID',
  `mobile` varchar(20) NOT NULL COMMENT '手机号',
  `template_params` varchar(1000) DEFAULT NULL COMMENT '模板变量JSON',
  `send_content` varchar(500) DEFAULT NULL COMMENT '发送内容',
  `task_status` int NOT NULL DEFAULT 0 COMMENT '任务状态：0待发送 1发送中 2成功 3失败 4已取消',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '已重试次数',
  `max_retry` int NOT NULL DEFAULT 3 COMMENT '最大重试次数',
  `error_message` varchar(500) DEFAULT NULL COMMENT '错误信息',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `tenancy_id` varchar(64) DEFAULT NULL COMMENT '租户ID',
  PRIMARY KEY (`task_id`),
  UNIQUE KEY `uk_app_request` (`app_id`, `request_no`),
  KEY `idx_tenancy_status` (`tenancy_id`, `task_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短信发送任务';
