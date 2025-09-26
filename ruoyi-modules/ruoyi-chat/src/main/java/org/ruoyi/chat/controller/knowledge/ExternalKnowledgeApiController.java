package org.ruoyi.chat.controller.knowledge;

import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.core.validate.AddGroup;
import org.ruoyi.common.core.validate.EditGroup;
import org.ruoyi.common.log.annotation.Log;
import org.ruoyi.common.log.enums.BusinessType;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.web.core.BaseController;
import org.ruoyi.core.page.PageQuery;
import org.ruoyi.core.page.TableDataInfo;
import org.ruoyi.domain.bo.ExternalKnowledgeApiBo;
import org.ruoyi.domain.vo.ExternalKnowledgeApiVo;
import org.ruoyi.service.IExternalKnowledgeApiService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 外部知识库API管理
 *
 * @author ruoyi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/external-knowledge-api")
public class ExternalKnowledgeApiController extends BaseController {

    private final IExternalKnowledgeApiService externalKnowledgeApiService;

    /**
     * 查询外部知识库API列表
     */
    @Operation(summary = "查询外部知识库API列表")
    @GetMapping("/list")
    public TableDataInfo<ExternalKnowledgeApiVo> list(ExternalKnowledgeApiBo bo, PageQuery pageQuery) {
        if (!StpUtil.isLogin()) {
            throw new SecurityException("请先去登录!");
        }

        // 非管理员只能查看自己创建的API
        if (!Objects.equals(LoginHelper.getUserId(), 1L)) {
            bo.setCreateBy(LoginHelper.getUserId().toString());
        }

        return externalKnowledgeApiService.queryPageList(bo, pageQuery);
    }

    /**
     * 获取外部知识库API详细信息
     */
    @Operation(summary = "获取外部知识库API详细信息")
    @GetMapping("/{id}")
    public R<ExternalKnowledgeApiVo> getInfo(@PathVariable String id) {
        if (!StpUtil.isLogin()) {
            throw new SecurityException("请先去登录!");
        }
        return R.ok(externalKnowledgeApiService.queryById(id));
    }

    /**
     * 新增外部知识库API
     */
    @Operation(summary = "新增外部知识库API")
    @Log(title = "外部知识库API", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated(AddGroup.class) @RequestBody ExternalKnowledgeApiBo bo) {
        if (!StpUtil.isLogin()) {
            throw new SecurityException("请先去登录!");
        }
        return toAjax(externalKnowledgeApiService.insertByBo(bo));
    }

    /**
     * 修改外部知识库API
     */
    @Operation(summary = "修改外部知识库API")
    @Log(title = "外部知识库API", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody ExternalKnowledgeApiBo bo) {
        if (!StpUtil.isLogin()) {
            throw new SecurityException("请先去登录!");
        }
        return toAjax(externalKnowledgeApiService.updateByBo(bo));
    }

    /**
     * 删除外部知识库API
     */
    @Operation(summary = "删除外部知识库API")
    @Log(title = "外部知识库API", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable String[] ids) {
        if (!StpUtil.isLogin()) {
            throw new SecurityException("请先去登录!");
        }
        return toAjax(externalKnowledgeApiService.deleteWithValidByIds(
            Arrays.stream(ids).map(Long::valueOf).toList(), true));
    }

    /**
     * 验证外部知识库API配置
     */
    @Operation(summary = "验证外部知识库API配置")
    @PostMapping("/validate")
    public R<Boolean> validateConfiguration(@RequestBody ExternalKnowledgeApiBo bo) {
        if (!StpUtil.isLogin()) {
            throw new SecurityException("请先去登录!");
        }
        try {
            Boolean result = externalKnowledgeApiService.validateApiConfiguration(bo);
            return R.ok(result);
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 根据租户ID获取API列表
     */
    @Operation(summary = "根据租户ID获取API列表")
    @GetMapping("/tenant/{tenantId}")
    public R<List<ExternalKnowledgeApiVo>> getByTenantId(@PathVariable String tenantId) {
        if (!StpUtil.isLogin()) {
            throw new SecurityException("请先去登录!");
        }
        return R.ok(externalKnowledgeApiService.queryByTenantId(tenantId));
    }
}