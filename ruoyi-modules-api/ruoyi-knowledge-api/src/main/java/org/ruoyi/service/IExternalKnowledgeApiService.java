package org.ruoyi.service;

import org.ruoyi.core.page.PageQuery;
import org.ruoyi.core.page.TableDataInfo;
import org.ruoyi.domain.bo.ExternalKnowledgeApiBo;
import org.ruoyi.domain.vo.ExternalKnowledgeApiVo;

import java.util.Collection;
import java.util.List;

/**
 * 外部知识库APIService接口
 *
 * @author ruoyi
 */
public interface IExternalKnowledgeApiService {

    /**
     * 查询外部知识库API
     */
    ExternalKnowledgeApiVo queryById(String id);

    /**
     * 查询外部知识库API列表
     */
    TableDataInfo<ExternalKnowledgeApiVo> queryPageList(ExternalKnowledgeApiBo bo, PageQuery pageQuery);

    /**
     * 查询外部知识库API列表
     */
    List<ExternalKnowledgeApiVo> queryList(ExternalKnowledgeApiBo bo);

    /**
     * 新增外部知识库API
     */
    Boolean insertByBo(ExternalKnowledgeApiBo bo);

    /**
     * 修改外部知识库API
     */
    Boolean updateByBo(ExternalKnowledgeApiBo bo);

    /**
     * 校验并批量删除外部知识库API信息
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

    /**
     * 验证API配置
     */
    Boolean validateApiConfiguration(ExternalKnowledgeApiBo bo);

    /**
     * 检查API是否被使用
     */
    Boolean isApiInUse(Long id);

    /**
     * 根据租户ID查询API列表
     */
    List<ExternalKnowledgeApiVo> queryByTenantId(String tenantId);
}