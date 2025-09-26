package org.ruoyi.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.ruoyi.common.core.validate.AddGroup;
import org.ruoyi.common.core.validate.EditGroup;
import org.ruoyi.domain.ExternalKnowledgeBinding;

import java.io.Serial;
import java.io.Serializable;

/**
 * 外部知识库绑定业务对象
 *
 * @author ruoyi
 */
@Data
@AutoMapper(target = ExternalKnowledgeBinding.class, reverseConvertGenerate = false)
public class ExternalKnowledgeBindingBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @NotBlank(message = "主键ID不能为空", groups = {EditGroup.class})
    private String id;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 数据集ID
     */
    @NotBlank(message = "数据集ID不能为空", groups = {AddGroup.class, EditGroup.class})
    private String datasetId;

    /**
     * 外部知识库API ID
     */
    @NotBlank(message = "外部知识库API ID不能为空", groups = {AddGroup.class, EditGroup.class})
    private String externalKnowledgeApiId;

    /**
     * 外部知识库ID
     */
    @NotBlank(message = "外部知识库ID不能为空", groups = {AddGroup.class, EditGroup.class})
    private String externalKnowledgeId;

    /**
     * 创建人ID
     */
    private String createBy;

    /**
     * 更新人ID
     */
    private String updateBy;
}