package org.ruoyi.chat.service.knowledge;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.constant.KnowledgeProviderType;
import org.ruoyi.domain.dto.KnowledgeRetrievalRequestDTO;
import org.ruoyi.domain.dto.KnowledgeRetrievalResponseDTO;
import org.ruoyi.domain.vo.KnowledgeInfoVo;
import org.ruoyi.service.IKnowledgeInfoService;
import org.ruoyi.service.KnowledgeRetrievalStrategy;
import org.springframework.stereotype.Service;

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

    private final List<KnowledgeRetrievalStrategy> strategies;
    private final IKnowledgeInfoService knowledgeInfoService;

    /**
     * 策略映射缓存
     */
    private Map<KnowledgeProviderType, KnowledgeRetrievalStrategy> strategyMap;

    /**
     * 初始化策略映射
     */
    private void initStrategyMap() {
        if (strategyMap == null) {
            strategyMap = strategies.stream()
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
            initStrategyMap();

            KnowledgeRetrievalStrategy strategy = strategyMap.get(providerType);
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
            initStrategyMap();

            KnowledgeRetrievalStrategy strategy = strategyMap.get(providerType);
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
        initStrategyMap();
        return strategyMap.get(providerType);
    }

    /**
     * 获取支持的知识库提供商类型列表
     *
     * @return 支持的类型列表
     */
    public List<KnowledgeProviderType> getSupportedTypes() {
        initStrategyMap();
        return List.copyOf(strategyMap.keySet());
    }
}