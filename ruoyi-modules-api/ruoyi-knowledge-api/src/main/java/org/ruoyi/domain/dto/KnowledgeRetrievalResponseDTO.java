package org.ruoyi.domain.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 知识库检索响应DTO
 *
 * @author ruoyi
 */
@Data
public class KnowledgeRetrievalResponseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文档内容
     */
    private String content;

    /**
     * 相似度分数
     */
    private Double score;

    /**
     * 文档来源
     */
    private String source;

    /**
     * 文档元数据
     */
    private Map<String, Object> metadata;

    /**
     * 文档片段ID
     */
    private String fragmentId;

    /**
     * 知识库ID
     */
    private String knowledgeId;
}