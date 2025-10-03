package org.ruoyi.chat.controller.knowledge;

import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.ruoyi.chat.config.KnowledgeRoleConfig;
import org.ruoyi.chat.service.knowledge.UnifiedKnowledgeRetrievalService;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.core.validate.AddGroup;
import org.ruoyi.common.excel.utils.ExcelUtil;
import org.ruoyi.common.log.annotation.Log;
import org.ruoyi.common.log.enums.BusinessType;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.web.core.BaseController;
import org.ruoyi.core.page.PageQuery;
import org.ruoyi.core.page.TableDataInfo;
import org.ruoyi.domain.bo.KnowledgeAttachBo;
import org.ruoyi.domain.bo.KnowledgeFragmentBo;
import org.ruoyi.domain.bo.KnowledgeInfoBo;
import org.ruoyi.domain.vo.KnowledgeAttachVo;
import org.ruoyi.domain.vo.KnowledgeFragmentVo;
import org.ruoyi.domain.vo.KnowledgeInfoVo;
import org.ruoyi.service.IKnowledgeAttachService;
import org.ruoyi.service.IKnowledgeFragmentService;
import org.ruoyi.service.IKnowledgeInfoService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 知识库管理
 *
 * @author ageerle
 * @date 2025-05-03
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/knowledge")
public class KnowledgeController extends BaseController {

    private final IKnowledgeInfoService knowledgeInfoService;

    private final IKnowledgeAttachService attachService;

    private final IKnowledgeFragmentService fragmentService;

    private final KnowledgeRoleConfig knowledgeRoleConfig;

    private final UnifiedKnowledgeRetrievalService unifiedKnowledgeRetrievalService;

    /**
     * 根据用户信息查询本地知识库
     */
    @GetMapping("/list")
    public TableDataInfo<KnowledgeInfoVo> list(KnowledgeInfoBo bo, PageQuery pageQuery) {
        if (!StpUtil.isLogin()) {
            throw new SecurityException("请先去登录!");
        }
        if (!Objects.equals(LoginHelper.getUserId(), 1L)) {
            bo.setUid(LoginHelper.getUserId());
        }
        return knowledgeInfoService.queryPageList(bo, pageQuery);
    }

    /**
     * 根据用户信息及知识库角色查询本地知识库
     */
    @GetMapping("/listByRole")
    public TableDataInfo<KnowledgeInfoVo> listByRole(KnowledgeInfoBo bo, PageQuery pageQuery) {
        if (!StpUtil.isLogin()) {
            throw new SecurityException("请先去登录!");
        }

        // 管理员跳过权限
        if (Objects.equals(LoginHelper.getUserId(), 1L)) {
            return knowledgeInfoService.queryPageList(bo, pageQuery);
        } else if (!knowledgeRoleConfig.getEnable()) {
            bo.setUid(LoginHelper.getUserId());
            return knowledgeInfoService.queryPageList(bo, pageQuery);
        } else {
            bo.setUid(LoginHelper.getUserId());
            return knowledgeInfoService.queryPageListByRole(bo, pageQuery);
        }
    }

    /**
     * 新增知识库
     */
    @Log(title = "知识库", businessType = BusinessType.INSERT)
    @PostMapping("/save")
    public R<Void> save(@Validated(AddGroup.class) @RequestBody KnowledgeInfoBo bo) {
        knowledgeInfoService.saveOne(bo);
        return R.ok();
    }

    /**
     * 删除知识库
     */
    @PostMapping("/remove/{kid}")
    public R<String> remove(@PathVariable String kid) {
        knowledgeInfoService.removeKnowledge(kid);
        return R.ok("删除知识库成功!");
    }

    /**
     * 修改知识库
     */
    @Log(title = "知识库", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    public R<Void> edit(@RequestBody KnowledgeInfoBo bo) {
        return toAjax(knowledgeInfoService.updateByBo(bo));
    }

    /**
     * 导出知识库列表
     */
    @Log(title = "知识库", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(KnowledgeInfoBo bo, HttpServletResponse response) {
        List<KnowledgeInfoVo> list = knowledgeInfoService.queryList(bo);
        ExcelUtil.exportExcel(list, "知识库", KnowledgeInfoVo.class, response);
    }

    /**
     * 查询知识附件信息
     */
    @GetMapping("/detail/{kid}")
    public TableDataInfo<KnowledgeAttachVo> attach(KnowledgeAttachBo bo, PageQuery pageQuery,
                                                   @PathVariable String kid) {
        // 使用统一知识库服务查询文档列表，支持本地和外部知识库
        return unifiedKnowledgeRetrievalService.listDocuments(
            kid,
            pageQuery.getPageNum(),
            pageQuery.getPageSize(),
            pageQuery.getOrderByColumn(),
            "desc".equalsIgnoreCase(pageQuery.getIsAsc()),
            bo.getDocName()
        );
    }

    /**
     * 上传知识库附件
     */
    @PostMapping(value = "/attach/upload")
    public R<Map<String, Object>> upload(@RequestParam("kid") String kid,
                                          @RequestParam("file") MultipartFile file) throws Exception {
        // 使用统一知识库上传服务
        Map<String, Object> result = unifiedKnowledgeRetrievalService.uploadFile(kid, file);

        Boolean success = (Boolean) result.get("success");
        if (success != null && success) {
            return R.ok(result);
        } else {
            String message = (String) result.getOrDefault("message", "上传失败");
            return R.fail(message);
        }
    }

    /**
     * 获取知识库附件详细信息
     *
     * @param id 主键
     */
    @GetMapping("attach/info/{id}")
    public R<KnowledgeAttachVo> getAttachInfo(@NotNull(message = "主键不能为空")
                                              @PathVariable Long id) {
        return R.ok(attachService.queryById(id));
    }

    /**
     * 删除知识库附件（旧版本，保留兼容性）
     */
    @PostMapping("attach/remove/{kid}")
    public R<Void> removeAttach(@NotEmpty(message = "主键不能为空")
                                @PathVariable String kid) {
        attachService.removeKnowledgeAttach(kid);
        return R.ok();
    }

    /**
     * 删除知识库文档（统一接口，支持本地和外部知识库）
     */
    @PostMapping("attach/delete/{knowledgeId}/{docId}")
    public R<Void> deleteAttach(@NotEmpty(message = "知识库ID不能为空") @PathVariable String knowledgeId,
                                @NotEmpty(message = "文档ID不能为空") @PathVariable String docId) {
        // 使用统一知识库服务删除文档，支持本地和外部知识库
        Map<String, Object> result = unifiedKnowledgeRetrievalService.deleteDocument(knowledgeId, docId);

        Boolean success = (Boolean) result.get("success");
        if (success != null && success) {
            return R.ok();
        } else {
            String message = (String) result.getOrDefault("message", "删除失败");
            return R.fail(message);
        }
    }


    /**
     * 查询知识片段
     */
    @GetMapping("/fragment/list/{docId}")
    public TableDataInfo<KnowledgeFragmentVo> fragmentList(KnowledgeFragmentBo bo,
                                                           PageQuery pageQuery, @PathVariable String docId) {
        bo.setDocId(docId);
        return fragmentService.queryPageList(bo, pageQuery);
    }

    /**
     * 上传文件翻译
     */
    @PostMapping("/translationByFile")
    @ResponseBody
    public String translationByFile(@RequestParam("file") MultipartFile file, String targetLanguage) {
        return attachService.translationByFile(file, targetLanguage);
    }

}
