package org.ruoyi.chat.service.knowledge.strategy.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.constant.KnowledgeProviderType;
import org.ruoyi.domain.bo.QueryVectorBo;
import org.ruoyi.domain.dto.KnowledgeRetrievalRequestDTO;
import org.ruoyi.domain.dto.KnowledgeRetrievalResponseDTO;
import org.ruoyi.domain.vo.ChatModelVo;
import org.ruoyi.domain.vo.KnowledgeInfoVo;
import org.ruoyi.service.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地知识库检索策略实现
 *
 * @author ruoyi
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalKnowledgeRetrievalStrategy implements KnowledgeRetrievalStrategy {

    private final IKnowledgeInfoService knowledgeInfoService;
    private final IChatModelService chatModelService;
    private final VectorStoreService vectorStoreService;

    @Override
    public KnowledgeProviderType getSupportedType() {
        return KnowledgeProviderType.LOCAL;
    }

    @Override
    public List<KnowledgeRetrievalResponseDTO> retrieve(KnowledgeRetrievalRequestDTO request) {
        try {
            // 查询知识库信息
            KnowledgeInfoVo knowledgeInfoVo = knowledgeInfoService.queryById(Long.valueOf(request.getKnowledgeId()));
            if (knowledgeInfoVo == null) {
                log.warn("本地知识库信息不存在，knowledgeId: {}", request.getKnowledgeId());
                return new ArrayList<>();
            }

            // 查询向量模型配置信息
            ChatModelVo chatModel = chatModelService.selectModelByName(knowledgeInfoVo.getEmbeddingModelName());
            if (chatModel == null) {
                log.warn("向量模型配置不存在，模型名称: {}", knowledgeInfoVo.getEmbeddingModelName());
                return new ArrayList<>();
            }

            // 构建向量查询参数
            QueryVectorBo queryVectorBo = buildQueryVectorBo(request, knowledgeInfoVo, chatModel);

            // 获取向量查询结果
            List<String> contentList = vectorStoreService.getQueryVector(queryVectorBo);

            // 转换为响应DTO列表
            return convertToResponseDTOs(contentList, request.getKnowledgeId());

        } catch (Exception e) {
            log.error("本地知识库检索失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public boolean validateConfiguration(String knowledgeId) {
        try {
            if (knowledgeId == null || knowledgeId.trim().isEmpty()) {
                return false;
            }

            // 验证知识库是否存在
            KnowledgeInfoVo knowledgeInfoVo = knowledgeInfoService.queryById(Long.valueOf(knowledgeId));
            if (knowledgeInfoVo == null) {
                return false;
            }

            // 验证向量模型配置是否存在
            ChatModelVo chatModel = chatModelService.selectModelByName(knowledgeInfoVo.getEmbeddingModelName());
            return chatModel != null;

        } catch (Exception e) {
            log.error("验证本地知识库配置失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 构建向量查询参数
     */
    private QueryVectorBo buildQueryVectorBo(KnowledgeRetrievalRequestDTO request,
                                             KnowledgeInfoVo knowledgeInfoVo,
                                             ChatModelVo chatModel) {
        QueryVectorBo queryVectorBo = new QueryVectorBo();
        queryVectorBo.setQuery(request.getQuery());
        queryVectorBo.setKid(request.getKnowledgeId());
        queryVectorBo.setApiKey(chatModel.getApiKey());
        queryVectorBo.setBaseUrl(chatModel.getApiHost());
        queryVectorBo.setVectorModelName(knowledgeInfoVo.getVectorModelName());
        queryVectorBo.setEmbeddingModelName(knowledgeInfoVo.getEmbeddingModelName());

        // 设置返回结果数量，优先使用请求中的topK，否则使用知识库配置的限制
        Integer maxResults = request.getTopK();
        if (maxResults == null || maxResults <= 0) {
            maxResults = knowledgeInfoVo.getRetrieveLimit();
        }
        queryVectorBo.setMaxResults(maxResults);

        return queryVectorBo;
    }

    /**
     * 转换为响应DTO列表
     */
    private List<KnowledgeRetrievalResponseDTO> convertToResponseDTOs(List<String> contentList, String knowledgeId) {
        List<KnowledgeRetrievalResponseDTO> responses = new ArrayList<>();

        for (int i = 0; i < contentList.size(); i++) {
            String content = contentList.get(i);
            if (content != null && !content.trim().isEmpty()) {
                KnowledgeRetrievalResponseDTO response = new KnowledgeRetrievalResponseDTO();
                response.setContent(content);
                response.setKnowledgeId(knowledgeId);
                response.setSource("本地知识库");

                // 由于向量库返回的是内容字符串，这里设置一个基于索引的分数
                // 实际项目中可能需要根据向量库的实际返回调整
                response.setScore(1.0 - (i * 0.1)); // 简单的分数计算，第一个结果分数最高

                // 设置元数据
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("index", i);
                metadata.put("knowledgeId", knowledgeId);
                metadata.put("retrievalType", "vector");
                response.setMetadata(metadata);

                responses.add(response);
            }
        }

        return responses;
    }
}