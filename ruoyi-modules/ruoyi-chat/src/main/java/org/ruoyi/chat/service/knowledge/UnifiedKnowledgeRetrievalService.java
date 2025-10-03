package org.ruoyi.chat.service.knowledge;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.constant.KnowledgeProviderType;
import org.ruoyi.core.page.TableDataInfo;
import org.ruoyi.domain.dto.KnowledgeRetrievalRequestDTO;
import org.ruoyi.domain.dto.KnowledgeRetrievalResponseDTO;
import org.ruoyi.domain.vo.KnowledgeAttachVo;
import org.ruoyi.domain.vo.KnowledgeInfoVo;
import org.ruoyi.service.IKnowledgeAttachService;
import org.ruoyi.service.IKnowledgeInfoService;
import org.ruoyi.service.KnowledgeRetrievalStrategy;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 统一知识库检索服务
 * 使用策略模式统一处理不同类型的知识库检索
 *
 * @author ruoyi
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnifiedKnowledgeRetrievalService {

    private final List<KnowledgeRetrievalStrategy> retrievalStrategies;
    private final IKnowledgeInfoService knowledgeInfoService;
    private final IKnowledgeAttachService knowledgeAttachService;

    /**
     * 检索策略映射缓存
     */
    private Map<KnowledgeProviderType, KnowledgeRetrievalStrategy> retrievalStrategyMap;

    /**
     * 初始化检索策略映射
     */
    private void initRetrievalStrategyMap() {
        if (retrievalStrategyMap == null) {
            retrievalStrategyMap = retrievalStrategies.stream()
                    .collect(Collectors.toMap(
                            KnowledgeRetrievalStrategy::getSupportedType,
                            Function.identity()
                    ));
        }
    }

    /**
     * 自动检测知识库类型并检索
     * 根据知识库ID自动识别使用哪种策略
     *
     * @param request 检索请求
     * @return 检索结果列表
     */
    public List<KnowledgeRetrievalResponseDTO> autoRetrieve(KnowledgeRetrievalRequestDTO request) {
        try {
            // 首先尝试根据数据库中的provider字段确定知识库类型
            KnowledgeProviderType providerType = detectKnowledgeProviderType(request.getKnowledgeId());

            // 获取对应的检索策略
            KnowledgeRetrievalStrategy strategy = getRetrievalStrategy(providerType);
            if (strategy == null) {
                log.warn("未找到知识库类型 {} 对应的检索策略", providerType);
                return Collections.emptyList();
            }

            // 验证配置
            if (!strategy.validateConfiguration(request.getKnowledgeId())) {
                log.warn("知识库配置验证失败: type={}, knowledgeId={}", providerType, request.getKnowledgeId());
                return Collections.emptyList();
            }

            // 执行检索
            List<KnowledgeRetrievalResponseDTO> results = strategy.retrieve(request);
            log.info("知识库检索完成: type={}, knowledgeId={}, resultCount={}",
                    providerType, request.getKnowledgeId(), results.size());

            return results;

        } catch (Exception e) {
            log.error("自动知识库检索失败: knowledgeId={}, error={}", request.getKnowledgeId(), e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 检索知识库内容
     *
     * @param providerType 知识库提供商类型
     * @param request      检索请求
     * @return 检索结果列表
     */
    public List<KnowledgeRetrievalResponseDTO> retrieve(KnowledgeProviderType providerType, KnowledgeRetrievalRequestDTO request) {
        try {
            initRetrievalStrategyMap();

            KnowledgeRetrievalStrategy strategy = retrievalStrategyMap.get(providerType);
            if (strategy == null) {
                log.warn("不支持的知识库提供商类型: {}", providerType);
                return Collections.emptyList();
            }

            // 验证配置
            if (!strategy.validateConfiguration(request.getKnowledgeId())) {
                log.warn("知识库配置验证失败: type={}, knowledgeId={}", providerType, request.getKnowledgeId());
                return Collections.emptyList();
            }

            // 执行检索
            List<KnowledgeRetrievalResponseDTO> results = strategy.retrieve(request);
            log.info("知识库检索完成: type={}, knowledgeId={}, resultCount={}",
                    providerType, request.getKnowledgeId(), results.size());

            return results;

        } catch (Exception e) {
            log.error("知识库检索失败: type={}, knowledgeId={}, error={}",
                    providerType, request.getKnowledgeId(), e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 检查知识库配置是否有效
     *
     * @param providerType 知识库提供商类型
     * @param knowledgeId  知识库ID
     * @return 是否有效
     */
    public boolean validateKnowledgeConfiguration(KnowledgeProviderType providerType, String knowledgeId) {
        try {
            initRetrievalStrategyMap();

            KnowledgeRetrievalStrategy strategy = retrievalStrategyMap.get(providerType);
            if (strategy == null) {
                return false;
            }

            return strategy.validateConfiguration(knowledgeId);
        } catch (Exception e) {
            log.error("验证知识库配置失败: type={}, knowledgeId={}, error={}",
                    providerType, knowledgeId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 检测知识库提供商类型
     */
    private KnowledgeProviderType detectKnowledgeProviderType(String knowledgeId) {
        try {
            // 尝试通过知识库ID查询本地知识库信息
            KnowledgeInfoVo knowledgeInfo = knowledgeInfoService.queryById(Long.valueOf(knowledgeId));
            if (knowledgeInfo != null) {
                // 如果查到了，返回其provider类型
                return knowledgeInfo.getProvider() != null ? knowledgeInfo.getProvider() : KnowledgeProviderType.LOCAL;
            }
        } catch (NumberFormatException e) {
            // 如果knowledgeId无法转换为Long，可能是外部知识库的字符串ID
            log.debug("知识库ID不是数字格式，可能是外部知识库: {}", knowledgeId);
        } catch (Exception e) {
            log.debug("查询本地知识库信息失败，尝试作为外部知识库处理: {}", e.getMessage());
        }

        // 查不到本地知识库信息，默认作为外部知识库处理
        return KnowledgeProviderType.EXTERNAL;
    }

    /**
     * 获取检索策略
     */
    private KnowledgeRetrievalStrategy getRetrievalStrategy(KnowledgeProviderType providerType) {
        initRetrievalStrategyMap();
        return retrievalStrategyMap.get(providerType);
    }

    /**
     * 获取支持的知识库提供商类型列表
     *
     * @return 支持的类型列表
     */
    public List<KnowledgeProviderType> getSupportedTypes() {
        initRetrievalStrategyMap();
        return List.copyOf(retrievalStrategyMap.keySet());
    }

    /**
     * 统一文件上传接口
     * 根据知识库类型自动选择上传策略
     *
     * @param knowledgeId 知识库ID
     * @param file 要上传的文件
     * @return 上传结果
     */
    public Map<String, Object> uploadFile(String knowledgeId, MultipartFile file) {
        try {
            // 检测知识库类型
            KnowledgeProviderType providerType = detectKnowledgeProviderType(knowledgeId);

            // 获取对应的检索策略（包含上传功能）
            KnowledgeRetrievalStrategy strategy = getRetrievalStrategy(providerType);
            if (strategy == null) {
                log.warn("未找到知识库类型 {} 对应的策略", providerType);
                return Map.of("success", false, "message", "不支持的知识库类型");
            }

            // 验证配置
            if (!strategy.validateConfiguration(knowledgeId)) {
                log.warn("知识库配置验证失败: type={}, knowledgeId={}", providerType, knowledgeId);
                return Map.of("success", false, "message", "知识库配置验证失败");
            }

            // 执行上传
            Map<String, Object> result = strategy.uploadFile(knowledgeId, file);
            log.info("文件上传完成: type={}, knowledgeId={}, fileName={}, success={}",
                    providerType, knowledgeId, file.getOriginalFilename(), result.get("success"));

            return result;

        } catch (Exception e) {
            log.error("文件上传失败: knowledgeId={}, error={}", knowledgeId, e.getMessage(), e);
            return Map.of("success", false, "message", "文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 统一文档列表查询接口
     * 根据知识库类型自动选择查询策略
     *
     * @param knowledgeId 知识库ID
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @param orderBy 排序字段
     * @param desc 是否降序
     * @param keywords 关键词
     * @return 文档列表结果（TableDataInfo格式）
     */
    public TableDataInfo<KnowledgeAttachVo> listDocuments(String knowledgeId, Integer pageNum, Integer pageSize,
                                             String orderBy, Boolean desc, String keywords) {
        try {
            // 检测知识库类型
            KnowledgeProviderType providerType = detectKnowledgeProviderType(knowledgeId);

            // 获取对应的检索策略
            KnowledgeRetrievalStrategy strategy = getRetrievalStrategy(providerType);
            if (strategy == null) {
                log.warn("未找到知识库类型 {} 对应的策略", providerType);
                return new TableDataInfo<>(new ArrayList<>(), 0L);
            }

            // 验证配置
            if (!strategy.validateConfiguration(knowledgeId)) {
                log.warn("知识库配置验证失败: type={}, knowledgeId={}", providerType, knowledgeId);
                return new TableDataInfo<>(new ArrayList<>(), 0L);
            }

            // 执行查询
            Map<String, Object> result = strategy.listDocuments(knowledgeId, pageNum, pageSize, orderBy, desc, keywords);

            Boolean success = (Boolean) result.get("success");
            if (success != null && success) {
                // 成功获取数据
                Object rowsObj = result.get("rows");
                List<KnowledgeAttachVo> rows = new ArrayList<>();
                if (rowsObj instanceof List<?>) {
                    @SuppressWarnings("unchecked")
                    List<KnowledgeAttachVo> list = (List<KnowledgeAttachVo>) rowsObj;
                    rows = list;
                }

                Object totalObj = result.get("total");
                Long total = 0L;
                if (totalObj instanceof Integer) {
                    total = ((Integer) totalObj).longValue();
                } else if (totalObj instanceof Long) {
                    total = (Long) totalObj;
                }

                log.info("文档列表查询完成: type={}, knowledgeId={}, total={}", providerType, knowledgeId, total);
                return new TableDataInfo<>(rows, total);
            } else {
                // 查询失败
                String message = (String) result.getOrDefault("message", "查询失败");
                log.warn("文档列表查询失败: type={}, knowledgeId={}, message={}", providerType, knowledgeId, message);
                return new TableDataInfo<>(new ArrayList<>(), 0L);
            }

        } catch (Exception e) {
            log.error("文档列表查询失败: knowledgeId={}, error={}", knowledgeId, e.getMessage(), e);
            return new TableDataInfo<>(new ArrayList<>(), 0L);
        }
    }

    /**
     * 统一文档删除接口
     * 根据知识库类型自动选择删除策略
     *
     * @param knowledgeId 知识库ID
     * @param documentId 文档ID
     * @return 删除结果
     */
    public Map<String, Object> deleteDocument(String knowledgeId, String documentId) {
        try {
            // 检测知识库类型
            KnowledgeProviderType providerType = detectKnowledgeProviderType(knowledgeId);

            // 获取对应的检索策略
            KnowledgeRetrievalStrategy strategy = getRetrievalStrategy(providerType);
            if (strategy == null) {
                log.warn("未找到知识库类型 {} 对应的策略", providerType);
                return Map.of("success", false, "message", "不支持的知识库类型");
            }

            // 验证配置
            if (!strategy.validateConfiguration(knowledgeId)) {
                log.warn("知识库配置验证失败: type={}, knowledgeId={}", providerType, knowledgeId);
                return Map.of("success", false, "message", "知识库配置验证失败");
            }

            // 执行删除
            Map<String, Object> result = strategy.deleteDocument(knowledgeId, documentId);
            log.info("文档删除完成: type={}, knowledgeId={}, documentId={}, success={}",
                    providerType, knowledgeId, documentId, result.get("success"));

            return result;

        } catch (Exception e) {
            log.error("文档删除失败: knowledgeId={}, documentId={}, error={}", knowledgeId, documentId, e.getMessage(), e);
            return Map.of("success", false, "message", "文档删除失败: " + e.getMessage());
        }
    }

    /**
     * 通过文档ID删除文档（自动识别知识库）
     * 先查询文档所属的知识库ID，再调用删除方法
     *
     * @param documentId 文档ID
     * @return 删除结果
     */
    public Map<String, Object> deleteDocumentByDocId(String documentId) {
        try {
            // 通过文档ID查询文档信息以获取知识库ID
            KnowledgeAttachVo attachVo = knowledgeAttachService.queryByDocId(documentId);

            if (attachVo == null) {
                log.warn("未找到文档信息: documentId={}", documentId);
                return Map.of("success", false, "message", "未找到文档信息");
            }

            String knowledgeId = attachVo.getKid();
            if (knowledgeId == null || knowledgeId.trim().isEmpty()) {
                log.warn("文档所属知识库ID为空: documentId={}", documentId);
                return Map.of("success", false, "message", "文档所属知识库ID为空");
            }

            // 调用删除方法
            return deleteDocument(knowledgeId, documentId);

        } catch (Exception e) {
            log.error("通过文档ID删除失败: documentId={}, error={}", documentId, e.getMessage(), e);
            return Map.of("success", false, "message", "删除失败: " + e.getMessage());
        }
    }
}