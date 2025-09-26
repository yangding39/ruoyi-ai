package org.ruoyi.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.ruoyi.domain.ExternalKnowledgeBinding;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 外部知识库绑定视图对象
 *
 * @author ruoyi
 */
@Data
@AutoMapper(target = ExternalKnowledgeBinding.class)
public class ExternalKnowledgeBindingVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private String id;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 数据集ID
     */
    private String datasetId;

    /**
     * 外部知识库API ID
     */
    private String externalKnowledgeApiId;

    /**
     * 外部知识库ID
     */
    private String externalKnowledgeId;

    /**
     * 创建人ID
     */
    private String createBy;

    /**
     * 更新人ID
     */
    private String updateBy;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}