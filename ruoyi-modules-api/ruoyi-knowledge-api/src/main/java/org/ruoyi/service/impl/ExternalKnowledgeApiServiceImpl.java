package org.ruoyi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import org.ruoyi.common.core.utils.MapstructUtils;
import org.ruoyi.common.core.utils.OkHttpUtil;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.core.page.PageQuery;
import org.ruoyi.core.page.TableDataInfo;
import org.ruoyi.domain.ExternalKnowledgeApi;
import org.ruoyi.domain.bo.ExternalKnowledgeApiBo;
import org.ruoyi.domain.vo.ExternalKnowledgeApiVo;
import org.ruoyi.mapper.ExternalKnowledgeApiMapper;
import org.ruoyi.mapper.ExternalKnowledgeBindingMapper;
import org.ruoyi.service.IExternalKnowledgeApiService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 外部知识库APIService业务层处理
 *
 * @author ruoyi
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ExternalKnowledgeApiServiceImpl implements IExternalKnowledgeApiService {

    private final ExternalKnowledgeApiMapper baseMapper;
    private final ExternalKnowledgeBindingMapper bindingMapper;

    @Override
    public ExternalKnowledgeApiVo queryById(String id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public TableDataInfo<ExternalKnowledgeApiVo> queryPageList(ExternalKnowledgeApiBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ExternalKnowledgeApi> lqw = buildQueryWrapper(bo);
        Page<ExternalKnowledgeApiVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    @Override
    public List<ExternalKnowledgeApiVo> queryList(ExternalKnowledgeApiBo bo) {
        LambdaQueryWrapper<ExternalKnowledgeApi> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<ExternalKnowledgeApi> buildQueryWrapper(ExternalKnowledgeApiBo bo) {
        LambdaQueryWrapper<ExternalKnowledgeApi> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getTenantId()), ExternalKnowledgeApi::getTenantId, bo.getTenantId());
        lqw.like(StringUtils.isNotBlank(bo.getName()), ExternalKnowledgeApi::getName, bo.getName());
        lqw.eq(StringUtils.isNotBlank(bo.getCreateBy()), ExternalKnowledgeApi::getCreateBy, bo.getCreateBy());
        lqw.orderByDesc(ExternalKnowledgeApi::getCreateTime);
        return lqw;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(ExternalKnowledgeApiBo bo) {
        try {
            // 验证API配置
            if (!validateApiConfiguration(bo)) {
                throw new RuntimeException("API配置验证失败");
            }

            ExternalKnowledgeApi add = MapstructUtils.convert(bo, ExternalKnowledgeApi.class);

            // 设置创建人信息
            String userId = LoginHelper.getUserId().toString();
            add.setCreateBy(Long.valueOf(userId));
            add.setUpdateBy(Long.valueOf(userId));

            // 设置租户ID
            if (StringUtils.isBlank(add.getTenantId())) {
                // 可以从当前用户上下文获取租户ID
                add.setTenantId("default");
            }

            // 序列化设置
            if (bo.getSettings() != null) {
                add.setSettingsDict(bo.getSettings());
            }

            boolean flag = baseMapper.insert(add) > 0;
            if (flag) {
                bo.setId(add.getId());
            }
            return flag;
        } catch (Exception e) {
            log.error("创建外部知识库API失败: {}", e.getMessage(), e);
            throw new RuntimeException("创建外部知识库API失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(ExternalKnowledgeApiBo bo) {
        try {
            // 验证API配置
            if (!validateApiConfiguration(bo)) {
                throw new RuntimeException("API配置验证失败");
            }

            ExternalKnowledgeApi update = MapstructUtils.convert(bo, ExternalKnowledgeApi.class);

            // 设置更新人信息
            update.setUpdateBy(LoginHelper.getUserId());

            // 处理隐藏的API密钥
            if (bo.getSettings() != null) {
                ExternalKnowledgeApi existing = baseMapper.selectById(bo.getId());
                if (existing != null) {
                    Map<String, Object> existingSettings = existing.getSettingsDict();
                    Map<String, Object> newSettings = bo.getSettings();

                    // 如果新设置中API密钥为隐藏值，则使用现有的密钥
                    if ("HIDDEN_VALUE".equals(newSettings.get("api_key"))) {
                        newSettings.put("api_key", existingSettings.get("api_key"));
                    }
                }
                update.setSettingsDict(bo.getSettings());
            }

            return baseMapper.updateById(update) > 0;
        } catch (Exception e) {
            log.error("更新外部知识库API失败: {}", e.getMessage(), e);
            throw new RuntimeException("更新外部知识库API失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (isValid) {
            // 验证是否有绑定关系
            for (Long id : ids) {
                if (isApiInUse(id)) {
                    throw new RuntimeException("API正在使用中，无法删除");
                }
            }
        }
        return baseMapper.deleteBatchIds(ids) > 0;
    }

    @Override
    public Boolean validateApiConfiguration(ExternalKnowledgeApiBo bo) {
        try {
            if (bo.getSettings() == null || bo.getSettings().isEmpty()) {
                throw new RuntimeException("API配置不能为空");
            }

            Map<String, Object> settings = bo.getSettings();
            String endpoint = (String) settings.get("endpoint");
            String apiKey = (String) settings.get("api_key");

            if (StringUtils.isEmpty(endpoint)) {
                throw new RuntimeException("API端点不能为空");
            }

            if (StringUtils.isEmpty(apiKey)) {
                throw new RuntimeException("API密钥不能为空");
            }

            // 跳过隐藏值的验证
            if ("HIDDEN_VALUE".equals(apiKey)) {
                return true;
            }

            // 验证端点连通性
            return validateEndpointConnectivity(endpoint, apiKey);

        } catch (Exception e) {
            log.error("验证API配置失败: {}", e.getMessage(), e);
            throw new RuntimeException("验证API配置失败: " + e.getMessage());
        }
    }

    @Override
    public Boolean isApiInUse(Long id) {
        Long count = bindingMapper.countByExternalKnowledgeApiId(id);
        return count > 0;
    }

    @Override
    public List<ExternalKnowledgeApiVo> queryByTenantId(String tenantId) {
        return baseMapper.selectByTenantId(tenantId).stream()
                .map(api -> MapstructUtils.convert(api, ExternalKnowledgeApiVo.class))
                .toList();
    }

    /**
     * 验证端点连通性
     */
    private Boolean validateEndpointConnectivity(String endpoint, String apiKey) {
        try {
            // 创建OkHttpUtil实例
            OkHttpUtil okHttpUtil = new OkHttpUtil();
            okHttpUtil.setApiHost(endpoint);
            okHttpUtil.setApiKey("Bearer " + apiKey);

            // 发送测试请求
            Request testRequest = okHttpUtil.createPostRequest("/retrieval", "{}");
            String response = okHttpUtil.executeRequest(testRequest);

            // 如果能执行请求（不管是否成功），说明端点是可达的
            // 实际的API响应错误（如404、400等）在业务使用时再处理
            return true;

        } catch (Exception e) {
            log.error("验证端点连通性失败: endpoint={}, error={}", endpoint, e.getMessage());
            throw new RuntimeException("无法连接到API端点: " + e.getMessage());
        }
    }
}