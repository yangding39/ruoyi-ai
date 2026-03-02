package org.ruoyi.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.core.domain.BaseEntity;

import java.io.Serial;
import java.util.HashMap;
import java.util.Map;

/**
 * 外部知识库API配置实体
 *
 * @author ruoyi
 */
@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("external_knowledge_apis")
public class ExternalKnowledgeApi extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 主键ID
     */
    @TableId
    private Long id;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * API名称
     */
    private String name;

    /**
     * API描述
     */
    private String description;

    /**
     * API配置设置(JSON格式)
     */
    private String settings;


    /**
     * 获取设置的字典形式
     */
    public Map<String, Object> getSettingsDict() {
        if (settings == null || settings.trim().isEmpty()) {
            return new HashMap<>();
        }

        try {
            return objectMapper.readValue(settings, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("解析外部知识库API设置失败: {}", e.getMessage(), e);
            return new HashMap<>();
        }
    }

    /**
     * 设置配置字典
     */
    public void setSettingsDict(Map<String, Object> settingsDict) {
        if (settingsDict == null) {
            this.settings = null;
            return;
        }

        try {
            this.settings = objectMapper.writeValueAsString(settingsDict);
        } catch (Exception e) {
            log.error("序列化外部知识库API设置失败: {}", e.getMessage(), e);
            this.settings = "{}";
        }
    }
}