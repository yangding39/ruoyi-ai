package org.ruoyi.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.ruoyi.domain.ExternalKnowledgeApi;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 外部知识库API视图对象
 *
 * @author ruoyi
 */
@Data
@AutoMapper(target = ExternalKnowledgeApi.class)
public class ExternalKnowledgeApiVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
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