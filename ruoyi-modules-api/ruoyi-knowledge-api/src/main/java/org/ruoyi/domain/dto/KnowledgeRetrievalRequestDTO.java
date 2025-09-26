package org.ruoyi.domain.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 知识库检索请求DTO
 *
 * @author ruoyi
 */
@Data
public class KnowledgeRetrievalRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 查询内容
     */
    private String query;

    /**
     * 知识库ID
     */
    private String knowledgeId;

    /**
     * 检索配置参数
     */
    private Map<String, Object> retrievalSettings;

    /**
     * 元数据条件
     */
    private Map<String, Object> metadataCondition;

    /**
     * Top-K 数量
     */
    private Integer topK = 5;

    /**
     * 相似度阈值
     */
    private Double scoreThreshold = 0.0;

    /**
     * 是否启用相似度阈值
     */
    private Boolean scoreThresholdEnabled = false;
}