-- ----------------------------
-- Custom External Knowledge API Schema
-- This file contains custom extensions for external knowledge API support
-- Run this script after ruoyi-ai.sql to add external knowledge functionality
-- ----------------------------

-- ----------------------------
-- 添加 provider 字段到 knowledge_info 表
-- ----------------------------
ALTER TABLE `knowledge_info`
ADD COLUMN `provider` varchar(20) NOT NULL DEFAULT 'LOCAL' COMMENT '知识库提供商类型' AFTER `kname`;

-- ----------------------------
-- Table structure for external_knowledge_bindings
-- ----------------------------
DROP TABLE IF EXISTS `external_knowledge_bindings`;
-- ----------------------------
-- Table structure for external_knowledge_apis
-- ----------------------------
DROP TABLE IF EXISTS `external_knowledge_apis`;

CREATE TABLE `external_knowledge_apis`  (
                                            `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                            `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000' COMMENT '租户编号',
                                            `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'API名称',
                                            `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT 'API描述',
                                            `settings` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'API配置设置(JSON格式)',
                                            `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
                                            `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
                                            `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                            `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
                                            `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                            `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
                                            PRIMARY KEY (`id`) USING BTREE,
                                            UNIQUE INDEX `uk_tenant_name`(`tenant_id`, `name`) USING BTREE COMMENT '租户内API名称唯一',
                                            INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '外部知识库API配置表' ROW_FORMAT = DYNAMIC;

CREATE TABLE `external_knowledge_bindings`  (
                                                `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                                `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000' COMMENT '租户编号',
                                                `dataset_id` bigint(20) NOT NULL COMMENT '数据集ID(对应知识库ID)',
                                                `external_knowledge_api_id` bigint(20) NOT NULL COMMENT '外部知识库API配置ID',
                                                `external_knowledge_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '外部知识库ID',
                                                `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
                                                `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
                                                `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                                `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
                                                `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                                `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
                                                PRIMARY KEY (`id`) USING BTREE,
                                                UNIQUE INDEX `uk_dataset_id`(`dataset_id`) USING BTREE COMMENT '数据集ID唯一',
                                                INDEX `idx_external_api_id`(`external_knowledge_api_id`) USING BTREE,
                                                INDEX `idx_tenant_id`(`tenant_id`) USING BTREE,
                                                CONSTRAINT `fk_external_knowledge_api` FOREIGN KEY (`external_knowledge_api_id`) REFERENCES `external_knowledge_apis` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '外部知识库绑定关系表' ROW_FORMAT = DYNAMIC;
