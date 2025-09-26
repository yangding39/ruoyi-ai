package org.ruoyi.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 外部知识库绑定实体
 *
 * @author ruoyi
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("external_knowledge_bindings")
public class ExternalKnowledgeBinding extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

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
     * 数据集ID（关联到本地知识库）
     */
    private Long datasetId;

    /**
     * 外部知识库API ID
     */
    private Long externalKnowledgeApiId;

    /**
     * 外部知识库ID
     */
    private String externalKnowledgeId;

}