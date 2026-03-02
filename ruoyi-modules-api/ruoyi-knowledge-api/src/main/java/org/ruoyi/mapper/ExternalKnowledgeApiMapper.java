package org.ruoyi.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.ruoyi.core.mapper.BaseMapperPlus;
import org.ruoyi.domain.ExternalKnowledgeApi;
import org.ruoyi.domain.vo.ExternalKnowledgeApiVo;

import java.util.List;

/**
 * 外部知识库API配置Mapper接口
 *
 * @author ruoyi
 */
@Mapper
public interface ExternalKnowledgeApiMapper extends BaseMapperPlus<ExternalKnowledgeApi, ExternalKnowledgeApiVo> {

    /**
     * 根据租户ID查询API列表
     *
     * @param tenantId 租户ID
     * @return API列表
     */
    default List<ExternalKnowledgeApi> selectByTenantId(String tenantId) {
        LambdaQueryWrapper<ExternalKnowledgeApi> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExternalKnowledgeApi::getTenantId, tenantId)
                .orderByDesc(ExternalKnowledgeApi::getCreateTime);
        return selectList(wrapper);
    }

    /**
     * 根据名称和租户ID查询
     *
     * @param name     API名称
     * @param tenantId 租户ID
     * @return API配置
     */
    default ExternalKnowledgeApi selectByNameAndTenantId(String name, String tenantId) {
        LambdaQueryWrapper<ExternalKnowledgeApi> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExternalKnowledgeApi::getName, name)
                .eq(ExternalKnowledgeApi::getTenantId, tenantId);
        return selectOne(wrapper);
    }
}