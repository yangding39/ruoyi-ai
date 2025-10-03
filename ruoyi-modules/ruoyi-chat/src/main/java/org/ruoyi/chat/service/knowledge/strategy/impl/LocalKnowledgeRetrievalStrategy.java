package org.ruoyi.chat.service.knowledge.strategy.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.constant.KnowledgeProviderType;
import org.ruoyi.core.page.PageQuery;
import org.ruoyi.core.page.TableDataInfo;
import org.ruoyi.domain.bo.KnowledgeAttachBo;
import org.ruoyi.domain.bo.KnowledgeFragmentBo;
import org.ruoyi.domain.bo.KnowledgeInfoUploadBo;
import org.ruoyi.domain.bo.QueryVectorBo;
import org.ruoyi.domain.dto.KnowledgeRetrievalRequestDTO;
import org.ruoyi.domain.dto.KnowledgeRetrievalResponseDTO;
import org.ruoyi.domain.vo.ChatModelVo;
import org.ruoyi.domain.vo.KnowledgeAttachVo;
import org.ruoyi.domain.vo.KnowledgeFragmentVo;
import org.ruoyi.domain.vo.KnowledgeInfoVo;
import org.ruoyi.service.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

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
    private final IKnowledgeAttachService knowledgeAttachService;
    private final IKnowledgeFragmentService knowledgeFragmentService;

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

    @Override
    public Map<String, Object> uploadFile(String knowledgeId, MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 构建上传BO
            KnowledgeInfoUploadBo uploadBo = new KnowledgeInfoUploadBo();
            uploadBo.setKid(knowledgeId);
            uploadBo.setFile(file);

            // 调用知识库服务的upload方法
            knowledgeInfoService.upload(uploadBo);

            log.info("本地知识库文件上传成功: knowledgeId={}, fileName={}", knowledgeId, file.getOriginalFilename());

            result.put("success", true);
            result.put("message", "文件上传成功");
            result.put("fileName", file.getOriginalFilename());
            result.put("fileSize", file.getSize());

        } catch (Exception e) {
            log.error("本地知识库文件上传失败: knowledgeId={}, fileName={}, error={}",
                knowledgeId, file.getOriginalFilename(), e.getMessage(), e);
            result.put("success", false);
            result.put("message", "文件上传失败: " + e.getMessage());
        }

        return result;
    }

    @Override
    public Map<String, Object> listDocuments(String knowledgeId, Integer pageNum, Integer pageSize,
                                             String orderBy, Boolean desc, String keywords) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 构建查询BO
            KnowledgeAttachBo bo = new KnowledgeAttachBo();
            bo.setKid(knowledgeId);

            // 构建分页查询 - 使用带参数的构造器
            PageQuery pageQuery = new PageQuery(
                pageSize != null && pageSize > 0 ? pageSize : 10,
                pageNum != null && pageNum > 0 ? pageNum : 1
            );

            // 处理排序
            if (orderBy != null && !orderBy.trim().isEmpty()) {
                String isAsc = (desc != null && desc) ? "desc" : "asc";
                pageQuery.setOrderByColumn(orderBy);
                pageQuery.setIsAsc(isAsc);
            }

            // 调用本地服务查询
            TableDataInfo<KnowledgeAttachVo> tableData = knowledgeAttachService.queryPageList(bo, pageQuery);

            log.info("本地知识库文档列表查询成功: knowledgeId={}, total={}", knowledgeId, tableData.getTotal());

            result.put("success", true);
            result.put("rows", tableData.getRows());
            result.put("total", tableData.getTotal());

        } catch (Exception e) {
            log.error("本地知识库文档列表查询失败: knowledgeId={}, error={}", knowledgeId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "文档列表查询失败: " + e.getMessage());
        }

        return result;
    }

    @Override
    public Map<String, Object> deleteDocument(String knowledgeId, String documentId) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 调用本地服务删除文档
            knowledgeAttachService.removeKnowledgeAttach(documentId);

            log.info("本地知识库文档删除成功: knowledgeId={}, documentId={}", knowledgeId, documentId);

            result.put("success", true);
            result.put("message", "文档删除成功");

        } catch (Exception e) {
            log.error("本地知识库文档删除失败: knowledgeId={}, documentId={}, error={}",
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
            // 构建查询BO
            KnowledgeFragmentBo bo = new KnowledgeFragmentBo();
            bo.setDocId(documentId);

            // 如果有片段ID，设置查询条件
            if (chunkId != null && !chunkId.trim().isEmpty()) {
                bo.setFid(chunkId);
            }

            // 构建分页查询 - 默认pageSize为10000
            PageQuery pageQuery = new PageQuery(
                pageSize != null && pageSize > 0 ? pageSize : 10000,
                pageNum != null && pageNum > 0 ? pageNum : 1
            );

            // 调用本地服务查询片段列表
            TableDataInfo<KnowledgeFragmentVo> tableData = knowledgeFragmentService.queryPageList(bo, pageQuery);

            log.info("本地知识库片段列表查询成功: knowledgeId={}, documentId={}, total={}",
                knowledgeId, documentId, tableData.getTotal());

            result.put("success", true);
            result.put("rows", tableData.getRows());
            result.put("total", tableData.getTotal());

        } catch (Exception e) {
            log.error("本地知识库片段列表查询失败: knowledgeId={}, documentId={}, error={}",
                knowledgeId, documentId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "片段列表查询失败: " + e.getMessage());
        }

        return result;
    }
}