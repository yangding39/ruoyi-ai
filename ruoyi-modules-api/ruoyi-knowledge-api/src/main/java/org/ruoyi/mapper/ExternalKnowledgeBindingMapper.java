package org.ruoyi.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.ruoyi.core.mapper.BaseMapperPlus;
import org.ruoyi.domain.ExternalKnowledgeBinding;
import org.ruoyi.domain.vo.ExternalKnowledgeBindingVo;

import java.util.List;

/**
 * 外部知识库绑定Mapper接口
 *
 * @author ruoyi
 */
@Mapper
public interface ExternalKnowledgeBindingMapper extends BaseMapperPlus<ExternalKnowledgeBinding, ExternalKnowledgeBindingVo> {

    /**
     * 根据数据集ID查询绑定信息
     *
     * @param datasetId 数据集ID
     * @return 绑定信息
     */
    default ExternalKnowledgeBinding selectByDatasetId(Long datasetId) {
        LambdaQueryWrapper<ExternalKnowledgeBinding> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExternalKnowledgeBinding::getDatasetId, datasetId);
        return selectOne(wrapper);
    }

    /**
     * 根据外部知识库API ID查询绑定数量
     *
     * @param externalKnowledgeApiId 外部知识库API ID
     * @return 绑定数量
     */
    default Long countByExternalKnowledgeApiId(Long externalKnowledgeApiId) {
        LambdaQueryWrapper<ExternalKnowledgeBinding> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExternalKnowledgeBinding::getExternalKnowledgeApiId, externalKnowledgeApiId);
        return selectCount(wrapper);
    }

    /**
     * 根据租户ID查询绑定列表
     *
     * @param tenantId 租户ID
     * @return 绑定列表
     */
    default List<ExternalKnowledgeBinding> selectByTenantId(String tenantId) {
        LambdaQueryWrapper<ExternalKnowledgeBinding> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExternalKnowledgeBinding::getTenantId, tenantId)
                .orderByDesc(ExternalKnowledgeBinding::getCreateTime);
        return selectList(wrapper);
    }
}