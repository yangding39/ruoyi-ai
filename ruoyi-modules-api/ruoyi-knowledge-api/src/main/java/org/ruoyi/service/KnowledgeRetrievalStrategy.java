package org.ruoyi.service;

import org.ruoyi.constant.KnowledgeProviderType;
import org.ruoyi.domain.dto.KnowledgeRetrievalRequestDTO;
import org.ruoyi.domain.dto.KnowledgeRetrievalResponseDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

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

    /**
     * 上传文件到知识库
     *
     * @param knowledgeId 知识库ID
     * @param file 要上传的文件
     * @return 上传结果
     */
    Map<String, Object> uploadFile(String knowledgeId, MultipartFile file);

    /**
     * 查询知识库文档列表
     *
     * @param knowledgeId 知识库ID
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @param orderBy 排序字段
     * @param desc 是否降序
     * @param keywords 关键词
     * @return 文档列表结果
     */
    Map<String, Object> listDocuments(String knowledgeId, Integer pageNum, Integer pageSize,
                                      String orderBy, Boolean desc, String keywords);

    /**
     * 删除知识库文档
     *
     * @param knowledgeId 知识库ID
     * @param documentId 文档ID
     * @return 删除结果
     */
    Map<String, Object> deleteDocument(String knowledgeId, String documentId);
}