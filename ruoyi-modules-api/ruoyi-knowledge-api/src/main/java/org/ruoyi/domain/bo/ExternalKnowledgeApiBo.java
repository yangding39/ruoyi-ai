package org.ruoyi.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import io.github.linpeilie.annotations.AutoMapping;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.ruoyi.common.core.validate.AddGroup;
import org.ruoyi.common.core.validate.EditGroup;
import org.ruoyi.domain.ExternalKnowledgeApi;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 外部知识库API业务对象
 *
 * @author ruoyi
 */
@Data
@AutoMapper(target = ExternalKnowledgeApi.class, reverseConvertGenerate = false)
public class ExternalKnowledgeApiBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @NotNull(message = "主键ID不能为空", groups = {EditGroup.class})
    private Long id;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * API名称
     */
    @NotBlank(message = "API名称不能为空", groups = {AddGroup.class, EditGroup.class})
    private String name;

    /**
     * API描述
     */
    private String description;

    /**
     * API配置设置
     */
    @AutoMapping(ignore = true)
    @NotNull(message = "API配置不能为空", groups = {AddGroup.class, EditGroup.class})
    private Map<String, Object> settings;

    /**
     * 创建人ID
     */
    private String createBy;

    /**
     * 更新人ID
     */
    private String updateBy;
}