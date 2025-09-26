package org.ruoyi.service;

import org.ruoyi.constant.KnowledgeProviderType;
import org.ruoyi.domain.dto.KnowledgeRetrievalRequestDTO;
import org.ruoyi.domain.dto.KnowledgeRetrievalResponseDTO;

import java.util.List;

/**
 * 知识库检索策略接口
 *
 * @author ruoyi
 */
public interface KnowledgeRetrievalStrategy {

    /**
     * 获取支持的知识库提供商类型
     *
     * @return 知识库提供商类型
     */
    KnowledgeProviderType getSupportedType();

    /**
     * 检索知识库内容
     *
     * @param request 检索请求
     * @return 检索结果列表
     */
    List<KnowledgeRetrievalResponseDTO> retrieve(KnowledgeRetrievalRequestDTO request);

    /**
     * 验证知识库配置
     *
     * @param knowledgeId 知识库ID
     * @return 是否有效
     */
    boolean validateConfiguration(String knowledgeId);
}