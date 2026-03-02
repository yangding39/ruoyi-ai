package org.ruoyi.chat.service.knowledge.strategy.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rometools.utils.Strings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.ruoyi.common.core.utils.OkHttpUtil;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.constant.KnowledgeProviderType;
import org.ruoyi.core.page.TableDataInfo;
import org.ruoyi.domain.ExternalKnowledgeApi;
import org.ruoyi.domain.ExternalKnowledgeBinding;
import org.ruoyi.domain.dto.KnowledgeRetrievalRequestDTO;
import org.ruoyi.domain.dto.KnowledgeRetrievalResponseDTO;
import org.ruoyi.domain.vo.KnowledgeAttachVo;
import org.ruoyi.domain.vo.KnowledgeFragmentVo;
import org.ruoyi.mapper.ExternalKnowledgeApiMapper;
import org.ruoyi.mapper.ExternalKnowledgeBindingMapper;
import org.ruoyi.service.KnowledgeRetrievalStrategy;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
    private final OkHttpClient okHttpClient = new OkHttpClient();

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
        if(request.getTopK() == null || request.getTopK() <=0) {
            retrievalSettings.put("top_k", 5);
        }
        retrievalSettings.put("top_k", request.getTopK());
        if (request.getScoreThresholdEnabled() != null && request.getScoreThresholdEnabled()) {
            retrievalSettings.put("score_threshold", request.getScoreThreshold());
        } else {
            retrievalSettings.put("score_threshold", 0.5);
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

    @Override
    public Map<String, Object> uploadFile(String knowledgeId, MultipartFile file) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 获取外部知识库绑定信息
            ExternalKnowledgeBinding binding = externalKnowledgeBindingMapper.selectByDatasetId(Long.valueOf(knowledgeId));
            if (binding == null) {
                log.warn("未找到外部知识库绑定信息: knowledgeId={}", knowledgeId);
                result.put("success", false);
                result.put("message", "未找到外部知识库绑定信息");
                return result;
            }

            // 获取API配置
            ExternalKnowledgeApi apiConfig = externalKnowledgeApiMapper.selectById(binding.getExternalKnowledgeApiId());
            if (apiConfig == null) {
                log.warn("未找到外部知识库API配置: apiId={}", binding.getExternalKnowledgeApiId());
                result.put("success", false);
                result.put("message", "未找到外部知识库API配置");
                return result;
            }

            // 验证API配置
            Map<String, Object> settings = apiConfig.getSettingsDict();
            String endpoint = (String) settings.get("endpoint");
            String apiKey = (String) settings.get("api_key");

            if (StringUtils.isEmpty(endpoint) || StringUtils.isEmpty(apiKey)) {
                log.warn("外部知识库API配置不完整: endpoint={}, apiKey={}", endpoint, apiKey != null);
                result.put("success", false);
                result.put("message", "外部知识库API配置不完整");
                return result;
            }

            // 使用RAGFlow Upload documents API上传文档
            // POST /api/v1/datasets/{dataset_id}/documents
            String uploadUrl = endpoint + "/datasets/" + binding.getExternalKnowledgeId() + "/documents";

            // 构建multipart请求
            RequestBody fileBody = RequestBody.create(
                file.getBytes(),
                MediaType.parse(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
            );

            MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getOriginalFilename(), fileBody)
                .build();

            Request request = new Request.Builder()
                .url(uploadUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "multipart/form-data")
                .post(requestBody)
                .build();

            // 发送请求
            try (Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("外部知识库API调用失败: code={}, message={}",
                        response.code(), response.message());
                    result.put("success", false);
                    result.put("message", "外部知识库API调用失败: " + response.message());
                    return result;
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                if (StringUtils.isNotEmpty(responseBody)) {
                    // 解析响应
                    @SuppressWarnings("unchecked")
                    Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);

                    Integer code = (Integer) responseMap.get("code");
                    if (code != null && code == 0) {
                        log.info("外部知识库文件上传成功: knowledgeId={}, fileName={}",
                            knowledgeId, file.getOriginalFilename());

                        // 获取上传的文档ID并触发解析
                        Object dataObj = responseMap.get("data");
                        if (dataObj instanceof List<?> dataList && !dataList.isEmpty()) {
                            List<String> documentIds = new ArrayList<>();
                            for (Object item : dataList) {
                                if (item instanceof Map<?, ?> docMap) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> doc = (Map<String, Object>) docMap;
                                    String documentId = (String) doc.get("id");
                                    if (StringUtils.isNotEmpty(documentId)) {
                                        documentIds.add(documentId);
                                    }
                                }
                            }

                            // 如果有文档ID，调用解析API
                            if (!documentIds.isEmpty()) {
                                parseDocuments(endpoint, apiKey, binding.getExternalKnowledgeId(), documentIds);
                            }
                        }

                        result.put("success", true);
                        result.put("message", "文件上传成功");
                        result.put("fileName", file.getOriginalFilename());
                        result.put("fileSize", file.getSize());
                        result.put("data", responseMap.get("data"));
                    } else {
                        String errorMsg = (String) responseMap.getOrDefault("message", "上传失败");
                        log.error("外部知识库API返回错误: code={}, message={}", code, errorMsg);
                        result.put("success", false);
                        result.put("message", "外部知识库API返回错误: " + errorMsg);
                    }
                } else {
                    log.warn("外部知识库API响应为空");
                    result.put("success", false);
                    result.put("message", "外部知识库API响应为空");
                }
            }

        } catch (IOException e) {
            log.error("外部知识库文件上传IO异常: knowledgeId={}, fileName={}, error={}",
                knowledgeId, file.getOriginalFilename(), e.getMessage(), e);
            result.put("success", false);
            result.put("message", "文件上传IO异常: " + e.getMessage());
        } catch (Exception e) {
            log.error("外部知识库文件上传失败: knowledgeId={}, fileName={}, error={}",
                knowledgeId, file.getOriginalFilename(), e.getMessage(), e);
            result.put("success", false);
            result.put("message", "文件上传失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 解析上传的文档
     * 调用RAGFlow Parse documents API
     */
    private void parseDocuments(String endpoint, String apiKey, String datasetId, List<String> documentIds) {
        try {
            // POST /api/v1/datasets/{dataset_id}/chunks
            String parseUrl = endpoint + "/datasets/" + datasetId + "/chunks";

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("document_ids", documentIds);
            String requestJson = objectMapper.writeValueAsString(requestBody);

            RequestBody body = RequestBody.create(
                requestJson,
                MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                .url(parseUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(body)
                .build();

            // 发送解析请求
            try (Response response = okHttpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    if (StringUtils.isNotEmpty(responseBody)) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
                        Integer code = (Integer) responseMap.get("code");
                        if (code != null && code == 0) {
                            log.info("外部知识库文档解析已触发: datasetId={}, documentIds={}", datasetId, documentIds);
                        } else {
                            log.warn("外部知识库文档解析触发失败: datasetId={}, code={}, message={}",
                                datasetId, code, responseMap.get("message"));
                        }
                    }
                } else {
                    log.warn("外部知识库文档解析请求失败: datasetId={}, httpCode={}, message={}",
                        datasetId, response.code(), response.message());
                }
            }
        } catch (Exception e) {
            log.error("触发外部知识库文档解析异常: datasetId={}, documentIds={}, error={}",
                datasetId, documentIds, e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> listDocuments(String knowledgeId, Integer pageNum, Integer pageSize,
                                             String orderBy, Boolean desc, String keywords) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 获取外部知识库绑定信息
            ExternalKnowledgeBinding binding = externalKnowledgeBindingMapper.selectByDatasetId(Long.valueOf(knowledgeId));
            if (binding == null) {
                log.warn("未找到外部知识库绑定信息: knowledgeId={}", knowledgeId);
                result.put("success", false);
                result.put("message", "未找到外部知识库绑定信息");
                return result;
            }

            // 获取API配置
            ExternalKnowledgeApi apiConfig = externalKnowledgeApiMapper.selectById(binding.getExternalKnowledgeApiId());
            if (apiConfig == null) {
                log.warn("未找到外部知识库API配置: apiId={}", binding.getExternalKnowledgeApiId());
                result.put("success", false);
                result.put("message", "未找到外部知识库API配置");
                return result;
            }

            // 验证API配置
            Map<String, Object> settings = apiConfig.getSettingsDict();
            String endpoint = (String) settings.get("endpoint");
            String apiKey = (String) settings.get("api_key");

            if (StringUtils.isEmpty(endpoint) || StringUtils.isEmpty(apiKey)) {
                log.warn("外部知识库API配置不完整: endpoint={}, apiKey={}", endpoint, apiKey != null);
                result.put("success", false);
                result.put("message", "外部知识库API配置不完整");
                return result;
            }

            // 构建查询URL - 使用RAGFlow的List documents API
            // GET /api/v1/datasets/{dataset_id}/documents
            StringBuilder urlBuilder = new StringBuilder(endpoint)
                .append("/datasets/")
                .append(binding.getExternalKnowledgeId())
                .append("/documents");

            // 添加查询参数
            List<String> queryParams = new ArrayList<>();
            if (pageNum != null && pageNum > 0) {
                queryParams.add("page=" + pageNum);
            }
            if (pageSize != null && pageSize > 0) {
                queryParams.add("page_size=" + pageSize);
            }
            if (orderBy != null && !orderBy.trim().isEmpty()) {
                queryParams.add("orderby=" + orderBy);
            }
            if (desc != null) {
                queryParams.add("desc=" + desc);
            }
            if (keywords != null && !keywords.trim().isEmpty()) {
                queryParams.add("keywords=" + keywords);
            }

            if (!queryParams.isEmpty()) {
                urlBuilder.append("?").append(String.join("&", queryParams));
            }

            String listUrl = urlBuilder.toString();

            // 构建请求
            Request request = new Request.Builder()
                .url(listUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .get()
                .build();

            // 发送请求
            try (Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("外部知识库文档列表API调用失败: code={}, message={}",
                        response.code(), response.message());
                    result.put("success", false);
                    result.put("message", "外部知识库API调用失败: " + response.message());
                    return result;
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                if (StringUtils.isNotEmpty(responseBody)) {
                    // 解析响应
                    @SuppressWarnings("unchecked")
                    Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);

                    Integer code = (Integer) responseMap.get("code");
                    if (code != null && code == 0) {
                        // 获取数据部分
                        Object dataObj = responseMap.get("data");
                        if (dataObj instanceof Map<?, ?> dataMap) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> data = (Map<String, Object>) dataMap;

                            // 获取文档列表和总数
                            Object docsObj = data.get("docs");
                            Object totalObj = data.get("total");

                            // 将外部知识库的文档列表转换为KnowledgeAttachVo列表
                            List<KnowledgeAttachVo> attachVoList = convertExternalDocsToAttachVo(docsObj, knowledgeId);

                            log.info("外部知识库文档列表查询成功: knowledgeId={}, total={}",
                                knowledgeId, totalObj);

                            result.put("success", true);
                            result.put("rows", attachVoList);
                            result.put("total", totalObj != null ? totalObj : 0);
                        } else {
                            result.put("success", false);
                            result.put("message", "外部知识库API返回数据格式错误");
                        }
                    } else {
                        String errorMsg = (String) responseMap.getOrDefault("message", "查询失败");
                        log.error("外部知识库API返回错误: code={}, message={}", code, errorMsg);
                        result.put("success", false);
                        result.put("message", "外部知识库API返回错误: " + errorMsg);
                    }
                } else {
                    log.warn("外部知识库API响应为空");
                    result.put("success", false);
                    result.put("message", "外部知识库API响应为空");
                }
            }

        } catch (IOException e) {
            log.error("外部知识库文档列表查询IO异常: knowledgeId={}, error={}",
                knowledgeId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "文档列表查询IO异常: " + e.getMessage());
        } catch (Exception e) {
            log.error("外部知识库文档列表查询失败: knowledgeId={}, error={}",
                knowledgeId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "文档列表查询失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 将外部知识库文档数据转换为KnowledgeAttachVo列表
     * 使用适配器模式统一不同数据源的字段映射
     *
     * @param docsObj 外部知识库返回的文档列表对象
     * @param knowledgeId 知识库ID
     * @return KnowledgeAttachVo列表
     */
    private List<KnowledgeAttachVo> convertExternalDocsToAttachVo(Object docsObj, String knowledgeId) {
        List<KnowledgeAttachVo> result = new ArrayList<>();

        if (!(docsObj instanceof List<?>)) {
            return result;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> docs = (List<Map<String, Object>>) docsObj;

        for (Map<String, Object> doc : docs) {
            KnowledgeAttachVo vo = new KnowledgeAttachVo();

            // 映射字段：外部知识库 -> KnowledgeAttachVo
            // id -> docId (外部知识库的文档ID作为docId)
            vo.setDocId(String.valueOf(doc.getOrDefault("id", "")));

            // name -> docName (文档名称)
            vo.setDocName(String.valueOf(doc.getOrDefault("name", "")));

            // 设置知识库ID
            vo.setKid(knowledgeId);

            // type -> docType (文档类型)
            Object typeObj = doc.get("type");
            if (typeObj != null) {
                vo.setDocType(String.valueOf(typeObj));
            }

            // 处理文档内容：优先使用location，其次使用name
            String location = String.valueOf(doc.getOrDefault("location", ""));
            vo.setContent(StringUtils.isNotEmpty(location) ? location : vo.getDocName());

            // 构建备注信息：包含外部知识库的元数据
            StringBuilder remark = new StringBuilder("外部知识库文档");
            Object statusObj = doc.get("status");
            if (statusObj != null) {
                remark.append(" | 状态: ").append(statusObj);
            }
            Object progressObj = doc.get("progress");
            if (progressObj != null) {
                remark.append(" | 进度: ").append(progressObj);
            }
            vo.setRemark(remark.toString());

            // 设置状态字段：基于外部知识库的状态映射
            // status: "0"未解析, "1"已解析, "2"解析失败
            String status = String.valueOf(doc.getOrDefault("status", "0"));
            if ("1".equals(status)) {
                // 已解析：所有状态设为已完成(30)
                vo.setPicStatus(30);
                vo.setPicAnysStatus(30);
                vo.setVectorStatus(30);
            } else if ("2".equals(status)) {
                // 解析失败：设为未开始(10)
                vo.setPicStatus(10);
                vo.setPicAnysStatus(10);
                vo.setVectorStatus(10);
            } else {
                // 未解析或处理中：设为进行中(20)
                vo.setPicStatus(20);
                vo.setPicAnysStatus(20);
                vo.setVectorStatus(20);
            }

            result.add(vo);
        }

        return result;
    }

    @Override
    public Map<String, Object> deleteDocument(String knowledgeId, String documentId) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 获取外部知识库绑定信息
            ExternalKnowledgeBinding binding = externalKnowledgeBindingMapper.selectByDatasetId(Long.valueOf(knowledgeId));
            if (binding == null) {
                log.warn("未找到外部知识库绑定信息: knowledgeId={}", knowledgeId);
                result.put("success", false);
                result.put("message", "未找到外部知识库绑定信息");
                return result;
            }

            // 获取API配置
            ExternalKnowledgeApi apiConfig = externalKnowledgeApiMapper.selectById(binding.getExternalKnowledgeApiId());
            if (apiConfig == null) {
                log.warn("未找到外部知识库API配置: apiId={}", binding.getExternalKnowledgeApiId());
                result.put("success", false);
                result.put("message", "未找到外部知识库API配置");
                return result;
            }

            // 验证API配置
            Map<String, Object> settings = apiConfig.getSettingsDict();
            String endpoint = (String) settings.get("endpoint");
            String apiKey = (String) settings.get("api_key");

            if (StringUtils.isEmpty(endpoint) || StringUtils.isEmpty(apiKey)) {
                log.warn("外部知识库API配置不完整: endpoint={}, apiKey={}", endpoint, apiKey != null);
                result.put("success", false);
                result.put("message", "外部知识库API配置不完整");
                return result;
            }

            // 使用RAGFlow Delete documents API
            // DELETE /api/v1/datasets/{dataset_id}/documents
            String deleteUrl = endpoint + "/datasets/" + binding.getExternalKnowledgeId() + "/documents";

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("ids", Collections.singletonList(documentId));
            String requestJson = objectMapper.writeValueAsString(requestBody);

            RequestBody body = RequestBody.create(
                requestJson,
                MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                .url(deleteUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .delete(body)
                .build();

            // 发送请求
            try (Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("外部知识库文档删除API调用失败: code={}, message={}",
                        response.code(), response.message());
                    result.put("success", false);
                    result.put("message", "外部知识库API调用失败: " + response.message());
                    return result;
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                if (StringUtils.isNotEmpty(responseBody)) {
                    // 解析响应
                    @SuppressWarnings("unchecked")
                    Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);

                    Integer code = (Integer) responseMap.get("code");
                    if (code != null && code == 0) {
                        log.info("外部知识库文档删除成功: knowledgeId={}, documentId={}", knowledgeId, documentId);
                        result.put("success", true);
                        result.put("message", "文档删除成功");
                    } else {
                        String errorMsg = (String) responseMap.getOrDefault("message", "删除失败");
                        log.error("外部知识库API返回错误: code={}, message={}", code, errorMsg);
                        result.put("success", false);
                        result.put("message", "外部知识库API返回错误: " + errorMsg);
                    }
                } else {
                    log.warn("外部知识库API响应为空");
                    result.put("success", false);
                    result.put("message", "外部知识库API响应为空");
                }
            }

        } catch (IOException e) {
            log.error("外部知识库文档删除IO异常: knowledgeId={}, documentId={}, error={}",
                knowledgeId, documentId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "文档删除IO异常: " + e.getMessage());
        } catch (Exception e) {
            log.error("外部知识库文档删除失败: knowledgeId={}, documentId={}, error={}",
                knowledgeId, documentId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "文档删除失败: " + e.getMessage());
        }

        return result;
    }

    @Override
    public Map<String, Object> listChunks(String knowledgeId, String documentId, Integer pageNum,
                                          Integer pageSize, String keywords, String chunkId) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 获取外部知识库绑定信息
            ExternalKnowledgeBinding binding = externalKnowledgeBindingMapper.selectByDatasetId(Long.valueOf(knowledgeId));
            if (binding == null) {
                log.warn("未找到外部知识库绑定信息: knowledgeId={}", knowledgeId);
                result.put("success", false);
                result.put("message", "未找到外部知识库绑定信息");
                return result;
            }

            // 获取API配置
            ExternalKnowledgeApi apiConfig = externalKnowledgeApiMapper.selectById(binding.getExternalKnowledgeApiId());
            if (apiConfig == null) {
                log.warn("未找到外部知识库API配置: apiId={}", binding.getExternalKnowledgeApiId());
                result.put("success", false);
                result.put("message", "未找到外部知识库API配置");
                return result;
            }

            // 验证API配置
            Map<String, Object> settings = apiConfig.getSettingsDict();
            String endpoint = (String) settings.get("endpoint");
            String apiKey = (String) settings.get("api_key");

            if (StringUtils.isEmpty(endpoint) || StringUtils.isEmpty(apiKey)) {
                log.warn("外部知识库API配置不完整: endpoint={}, apiKey={}", endpoint, apiKey != null);
                result.put("success", false);
                result.put("message", "外部知识库API配置不完整");
                return result;
            }

            // 构建查询URL - 使用RAGFlow的List chunks API
            // GET /api/v1/datasets/{dataset_id}/documents/{document_id}/chunks
            StringBuilder urlBuilder = new StringBuilder(endpoint)
                .append("/datasets/")
                .append(binding.getExternalKnowledgeId())
                .append("/documents/")
                .append(documentId)
                .append("/chunks");

            // 添加查询参数
            List<String> queryParams = new ArrayList<>();
            if (pageNum != null && pageNum > 0) {
                queryParams.add("page=" + pageNum);
            }
            // 如果前端没传page_size，设置默认值为10000
            int effectivePageSize = (pageSize != null && pageSize > 0) ? pageSize : 10000;
            queryParams.add("page_size=" + effectivePageSize);

            if (keywords != null && !keywords.trim().isEmpty()) {
                queryParams.add("keywords=" + keywords);
            }
            if (chunkId != null && !chunkId.trim().isEmpty()) {
                queryParams.add("id=" + chunkId);
            }

            if (!queryParams.isEmpty()) {
                urlBuilder.append("?").append(String.join("&", queryParams));
            }

            String listUrl = urlBuilder.toString();

            // 构建请求
            Request request = new Request.Builder()
                .url(listUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .get()
                .build();

            // 发送请求
            try (Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("外部知识库片段列表API调用失败: code={}, message={}",
                        response.code(), response.message());
                    result.put("success", false);
                    result.put("message", "外部知识库API调用失败: " + response.message());
                    return result;
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                if (StringUtils.isNotEmpty(responseBody)) {
                    // 解析响应
                    @SuppressWarnings("unchecked")
                    Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);

                    Integer code = (Integer) responseMap.get("code");
                    if (code != null && code == 0) {
                        // 获取数据部分
                        Object dataObj = responseMap.get("data");
                        if (dataObj instanceof Map<?, ?> dataMap) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> data = (Map<String, Object>) dataMap;

                            // 获取片段列表和总数
                            Object chunksObj = data.get("chunks");
                            Object totalObj = data.get("total");

                            // 将外部知识库的片段列表转换为KnowledgeFragmentVo列表（适配器模式）
                            List<KnowledgeFragmentVo> fragmentVoList = convertExternalChunksToFragmentVo(
                                chunksObj, knowledgeId, documentId);

                            // 使用TableDataInfo的带参数构造方法构建分页对象
                            Long total = 0L;
                            if (totalObj instanceof Integer) {
                                total = ((Integer) totalObj).longValue();
                            } else if (totalObj instanceof Long) {
                                total = (Long) totalObj;
                            }

                            TableDataInfo<KnowledgeFragmentVo> tableData = new TableDataInfo<>(fragmentVoList, total);

                            log.info("外部知识库片段列表查询成功: knowledgeId={}, documentId={}, total={}",
                                knowledgeId, documentId, tableData.getTotal());

                            result.put("success", true);
                            result.put("rows", tableData.getRows());
                            result.put("total", tableData.getTotal());
                        } else {
                            result.put("success", false);
                            result.put("message", "外部知识库API返回数据格式错误");
                        }
                    } else {
                        String errorMsg = (String) responseMap.getOrDefault("message", "查询失败");
                        log.error("外部知识库API返回错误: code={}, message={}", code, errorMsg);
                        result.put("success", false);
                        result.put("message", "外部知识库API返回错误: " + errorMsg);
                    }
                } else {
                    log.warn("外部知识库API响应为空");
                    result.put("success", false);
                    result.put("message", "外部知识库API响应为空");
                }
            }

        } catch (IOException e) {
            log.error("外部知识库片段列表查询IO异常: knowledgeId={}, documentId={}, error={}",
                knowledgeId, documentId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "片段列表查询IO异常: " + e.getMessage());
        } catch (Exception e) {
            log.error("外部知识库片段列表查询失败: knowledgeId={}, documentId={}, error={}",
                knowledgeId, documentId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "片段列表查询失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 将外部知识库片段数据转换为KnowledgeFragmentVo列表
     * 使用适配器模式统一不同数据源的字段映射
     *
     * @param chunksObj 外部知识库返回的片段列表对象
     * @param knowledgeId 知识库ID
     * @param documentId 文档ID
     * @return KnowledgeFragmentVo列表
     */
    private List<KnowledgeFragmentVo> convertExternalChunksToFragmentVo(Object chunksObj, String knowledgeId, String documentId) {
        List<KnowledgeFragmentVo> result = new ArrayList<>();

        if (!(chunksObj instanceof List<?>)) {
            return result;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> chunks = (List<Map<String, Object>>) chunksObj;

        int index = 0;
        for (Map<String, Object> chunk : chunks) {
            KnowledgeFragmentVo vo = new KnowledgeFragmentVo();

            // 映射字段：外部知识库 -> KnowledgeFragmentVo
            // id -> fid (外部知识库的片段ID作为fid)
            vo.setFid(String.valueOf(chunk.getOrDefault("id", "")));

            // 设置知识库ID和文档ID
            vo.setKid(knowledgeId);
            vo.setDocId(documentId);

            // content -> content (片段内容)
            vo.setContent(String.valueOf(chunk.getOrDefault("content", "")));

            // 设置片段索引
            vo.setIdx((long) index++);

            // 构建备注信息：包含外部知识库的元数据
            StringBuilder remark = new StringBuilder("外部知识库片段");
            Object docnmKwd = chunk.get("docnm_kwd");
            if (docnmKwd != null) {
                remark.append(" | 文档: ").append(docnmKwd);
            }
            Object available = chunk.get("available");
            if (available != null) {
                remark.append(" | 可用: ").append(available);
            }
            vo.setRemark(remark.toString());

            result.add(vo);
        }

        return result;
    }
}