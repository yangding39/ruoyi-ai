package org.ruoyi.chat.service.knowledge.strategy.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import org.ruoyi.common.core.utils.OkHttpUtil;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.constant.KnowledgeProviderType;
import org.ruoyi.domain.ExternalKnowledgeApi;
import org.ruoyi.domain.ExternalKnowledgeBinding;
import org.ruoyi.domain.dto.KnowledgeRetrievalRequestDTO;
import org.ruoyi.domain.dto.KnowledgeRetrievalResponseDTO;
import org.ruoyi.mapper.ExternalKnowledgeApiMapper;
import org.ruoyi.mapper.ExternalKnowledgeBindingMapper;
import org.ruoyi.service.KnowledgeRetrievalStrategy;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 外部知识库检索策略实现
 *
 * @author ruoyi
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalKnowledgeRetrievalStrategy implements KnowledgeRetrievalStrategy {

    private final ExternalKnowledgeApiMapper externalKnowledgeApiMapper;
    private final ExternalKnowledgeBindingMapper externalKnowledgeBindingMapper;
    private final ObjectMapper objectMapper;

    @Override
    public KnowledgeProviderType getSupportedType() {
        return KnowledgeProviderType.EXTERNAL;
    }

    @Override
    public List<KnowledgeRetrievalResponseDTO> retrieve(KnowledgeRetrievalRequestDTO request) {
        try {
            // 根据知识库ID获取绑定信息
            ExternalKnowledgeBinding binding = getExternalKnowledgeBinding(request.getKnowledgeId());
            if (binding == null) {
                log.warn("未找到外部知识库绑定信息: {}", request.getKnowledgeId());
                return Collections.emptyList();
            }

            // 获取API配置
            ExternalKnowledgeApi apiConfig = externalKnowledgeApiMapper.selectById(binding.getExternalKnowledgeApiId());
            if (apiConfig == null) {
                log.warn("未找到外部知识库API配置: {}", binding.getExternalKnowledgeApiId());
                return Collections.emptyList();
            }

            // 调用外部API进行检索
            return performExternalRetrieval(apiConfig, binding, request);

        } catch (Exception e) {
            log.error("外部知识库检索失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public boolean validateConfiguration(String knowledgeId) {
        try {
            ExternalKnowledgeBinding binding = getExternalKnowledgeBinding(knowledgeId);
            if (binding == null) {
                return false;
            }

            ExternalKnowledgeApi apiConfig = externalKnowledgeApiMapper.selectById(binding.getExternalKnowledgeApiId());
            if (apiConfig == null) {
                return false;
            }

            Map<String, Object> settings = apiConfig.getSettingsDict();
            return validateApiSettings(settings);

        } catch (Exception e) {
            log.error("验证外部知识库配置失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取外部知识库绑定信息
     */
    private ExternalKnowledgeBinding getExternalKnowledgeBinding(String datasetId) {
        return externalKnowledgeBindingMapper.selectByDatasetId(Long.valueOf(datasetId));
    }

    /**
     * 执行外部检索
     */
    private List<KnowledgeRetrievalResponseDTO> performExternalRetrieval(
            ExternalKnowledgeApi apiConfig,
            ExternalKnowledgeBinding binding,
            KnowledgeRetrievalRequestDTO request) {

        try {
            Map<String, Object> settings = apiConfig.getSettingsDict();
            String endpoint = (String) settings.get("endpoint");
            String apiKey = (String) settings.get("api_key");

            if (StringUtils.isEmpty(endpoint) || StringUtils.isEmpty(apiKey)) {
                log.warn("外部知识库API配置不完整");
                return Collections.emptyList();
            }

            // 创建OkHttpUtil实例
            OkHttpUtil okHttpUtil = new OkHttpUtil();
            okHttpUtil.setApiHost(endpoint);
            okHttpUtil.setApiKey("Bearer " + apiKey);

            // 构建请求体
            Map<String, Object> requestBody = buildRequestBody(binding, request);
            String requestJson = objectMapper.writeValueAsString(requestBody);

            // 发送请求
            Request httpRequest = okHttpUtil.createPostRequest("/retrieval", requestJson);
            String responseStr = okHttpUtil.executeRequest(httpRequest);

            if (StringUtils.isNotEmpty(responseStr)) {
                // 解析响应
                @SuppressWarnings("unchecked")
                Map<String, Object> responseBody = objectMapper.readValue(responseStr, Map.class);
                return parseExternalResponse(responseBody, binding.getExternalKnowledgeId());
            } else {
                log.warn("外部知识库API调用失败，无响应数据");
                return Collections.emptyList();
            }

        } catch (Exception e) {
            log.error("执行外部检索失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 构建请求体
     */
    private Map<String, Object> buildRequestBody(ExternalKnowledgeBinding binding, KnowledgeRetrievalRequestDTO request) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("query", request.getQuery());
        requestBody.put("knowledge_id", binding.getExternalKnowledgeId());

        // 构建检索设置
        Map<String, Object> retrievalSettings = new HashMap<>();
        retrievalSettings.put("top_k", request.getTopK());
        if (request.getScoreThresholdEnabled() != null && request.getScoreThresholdEnabled()) {
            retrievalSettings.put("score_threshold", request.getScoreThreshold());
        } else {
            retrievalSettings.put("score_threshold", 0.0);
        }
        requestBody.put("retrieval_setting", retrievalSettings);

        // 添加元数据条件
        if (request.getMetadataCondition() != null) {
            requestBody.put("metadata_condition", request.getMetadataCondition());
        }

        return requestBody;
    }

    /**
     * 解析外部API响应
     */
    @SuppressWarnings("unchecked")
    private List<KnowledgeRetrievalResponseDTO> parseExternalResponse(Map<String, Object> responseBody, String knowledgeId) {
        List<KnowledgeRetrievalResponseDTO> results = new ArrayList<>();

        Object recordsObj = responseBody.get("records");
        if (recordsObj instanceof List<?> records) {
            for (Object recordObj : records) {
                if (recordObj instanceof Map<?, ?> record) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> recordMap = (Map<String, Object>) record;

                    KnowledgeRetrievalResponseDTO dto = new KnowledgeRetrievalResponseDTO();
                    dto.setContent((String) recordMap.get("content"));

                    Object scoreObj = recordMap.getOrDefault("score", 0.0);
                    if (scoreObj instanceof Number) {
                        dto.setScore(((Number) scoreObj).doubleValue());
                    } else {
                        dto.setScore(0.0);
                    }

                    dto.setSource((String) recordMap.getOrDefault("source", "外部知识库"));
                    dto.setKnowledgeId(knowledgeId);

                    // 设置元数据
                    Object metadataObj = recordMap.get("metadata");
                    if (metadataObj instanceof Map<?, ?> metadata) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> metadataMap = (Map<String, Object>) metadata;
                        dto.setMetadata(metadataMap);
                    }

                    results.add(dto);
                }
            }
        }

        return results;
    }

    /**
     * 验证API设置
     */
    private boolean validateApiSettings(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            return false;
        }

        String endpoint = (String) settings.get("endpoint");
        String apiKey = (String) settings.get("api_key");

        return StringUtils.isNotEmpty(endpoint) && StringUtils.isNotEmpty(apiKey);
    }
}