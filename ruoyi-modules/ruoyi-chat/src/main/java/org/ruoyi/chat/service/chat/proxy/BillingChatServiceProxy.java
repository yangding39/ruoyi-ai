package org.ruoyi.chat.service.chat.proxy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.chat.service.chat.IChatCostService;
import org.ruoyi.chat.service.chat.IChatService;
import org.ruoyi.common.chat.entity.chat.Message;
import org.ruoyi.common.chat.request.ChatRequest;
import org.ruoyi.common.core.service.BaseContext;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 统一计费代理类
 * 自动处理所有ChatService的AI回复保存和计费逻辑
 */
@Slf4j
@RequiredArgsConstructor
public class BillingChatServiceProxy implements IChatService {

    @Autowired
    private final IChatService delegate;
    @Autowired
    private final IChatCostService chatCostService;

    @Override
    public SseEmitter chat(ChatRequest chatRequest, SseEmitter emitter) {
        // 🔥 在AI回复开始前检查余额是否充足
        if (!chatCostService.checkBalanceSufficient(chatRequest)) {
            String errorMsg = "余额不足，无法使用AI服务，请充值后再试";
            log.warn("余额不足阻止AI回复，用户ID: {}, 模型: {}",
                    chatRequest.getUserId(), chatRequest.getModel());
            try {
                emitter.send(errorMsg);
                emitter.complete();
            } catch (IOException e) {
                log.error("推送流异常，用户ID: {}, 模型: {}",
                        chatRequest.getUserId(), chatRequest.getModel());
                emitter.complete();
                throw new RuntimeException(errorMsg);
            }
            return emitter;
        }

        log.debug("余额检查通过，开始AI回复，用户ID: {}, 模型: {}",
                chatRequest.getUserId(), chatRequest.getModel());

        // 创建增强的SseEmitter，自动收集AI回复
        BillingSseEmitter billingEmitter = new BillingSseEmitter(emitter, chatRequest, chatCostService);

        try {
            // 调用实际的聊天服务
            return delegate.chat(chatRequest, billingEmitter);
        } catch (Exception e) {
            log.error("聊天服务执行失败", e);
            throw e;
        }
    }

    @Override
    public String getCategory() {
        return delegate.getCategory();
    }

    /**
     * 增强的SseEmitter，自动处理AI回复的保存和计费
     */
    private static class BillingSseEmitter extends SseEmitter {
        private final SseEmitter delegate;
        private final ChatRequest chatRequest;
        private final IChatCostService chatCostService;
        private final StringBuilder aiResponseBuilder = new StringBuilder();
        private final AtomicBoolean completed = new AtomicBoolean(false);

        public BillingSseEmitter(SseEmitter delegate, ChatRequest chatRequest, IChatCostService chatCostService) {
            super(delegate.getTimeout());
            this.delegate = delegate;
            this.chatRequest = chatRequest;
            this.chatCostService = chatCostService;
        }

        @Override
        public void send(Object object) throws IOException {
            // 先发送给前端
            delegate.send(object);

            // 提取AI回复内容并累积
            String content = extractContentFromSseData(object);
            if (content != null && !content.trim().isEmpty()) {
                aiResponseBuilder.append(content);
                log.debug("收集AI回复片段: {}", content);
            }
        }

        @Override
        public void complete() {
            if (completed.compareAndSet(false, true)) {
                try {
                    // AI回复完成，保存消息和计费
                    saveAiResponseAndBilling();
                    delegate.complete();
                    log.debug("AI回复完成，已保存并计费");
                } catch (Exception e) {
                    log.error("保存AI回复和计费失败", e);
                    delegate.completeWithError(e);
                }
            }
        }

        @Override
        public void completeWithError(Throwable ex) {
            if (completed.compareAndSet(false, true)) {
                log.warn("AI回复出错，跳过计费", ex);
                delegate.completeWithError(ex);
            }
        }

        /**
         * 保存AI回复并进行计费
         */
        private void saveAiResponseAndBilling() {
            String aiResponse = aiResponseBuilder.toString().trim();
            if (aiResponse.isEmpty()) {
                log.warn("AI回复内容为空，跳过保存和计费");
                return;
            }

            try {
                // 创建AI回复的ChatRequest
                ChatRequest aiChatRequest = new ChatRequest();
                aiChatRequest.setUserId(chatRequest.getUserId());
                aiChatRequest.setSessionId(chatRequest.getSessionId());
                aiChatRequest.setRole(Message.Role.ASSISTANT.getName());
                aiChatRequest.setModel(chatRequest.getModel());
                aiChatRequest.setPrompt(aiResponse);

                // 设置会话token供异步线程使用
                if (chatRequest.getToken() != null) {
                    BaseContext.setCurrentToken(chatRequest.getToken());
                }

                // 保存AI回复消息
                chatCostService.saveMessage(aiChatRequest);

                // 发布计费事件
                chatCostService.publishBillingEvent(aiChatRequest);

                log.debug("AI回复保存和计费完成，用户ID: {}, 会话ID: {}, 回复长度: {}",
                        chatRequest.getUserId(), chatRequest.getSessionId(), aiResponse.length());

            } catch (Exception e) {
                log.error("保存AI回复和计费失败，用户ID: {}, 会话ID: {}",
                        chatRequest.getUserId(), chatRequest.getSessionId(), e);
                // 不抛出异常，避免影响用户体验
            }
        }

        /**
         * 从SSE数据中提取AI回复内容
         * 适配不同AI服务的数据格式
         */
        private String extractContentFromSseData(Object sseData) {
            if (sseData == null) {
                return null;
            }

            String dataStr = sseData.toString();

            // 过滤明显的控制信号
            if (isControlSignal(dataStr)) {
                return null;
            }

            // 策略1: 直接字符串内容（DeepSeek等简单格式）
            String directContent = extractDirectContent(dataStr);
            if (directContent != null) {
                return directContent;
            }

            // 策略2: 解析JSON格式（OpenAI兼容格式）
            String jsonContent = extractJsonContent(dataStr);
            if (jsonContent != null) {
                return jsonContent;
            }

            // 策略3: SSE事件格式解析
            String sseContent = extractSseEventContent(dataStr);
            if (sseContent != null) {
                return sseContent;
            }

            // 策略4: 兜底策略 - 如果是纯文本且不是控制信号，直接返回
            if (isPureTextContent(dataStr)) {
                return dataStr;
            }

            log.debug("无法解析的SSE数据格式: {}", dataStr);
            return null;
        }

        /**
         * 判断是否为控制信号
         */
        private boolean isControlSignal(String data) {
            if (data == null || data.trim().isEmpty()) {
                return true;
            }

            String trimmed = data.trim();
            return "[DONE]".equals(trimmed)
                    || "null".equals(trimmed)
                    || trimmed.startsWith("event:")
                    || trimmed.startsWith("id:")
                    || trimmed.startsWith("retry:");
        }

        /**
         * 提取直接文本内容
         */
        private String extractDirectContent(String data) {
            // 如果是纯文本且长度合理，直接返回
            if (data.length() > 0 && data.length() < 1000 && !data.contains("{") && !data.contains("[")) {
                return data;
            }
            return null;
        }

        /**
         * 提取JSON格式内容
         */
        private String extractJsonContent(String data) {
            try {
                // 简化的JSON解析
                if (data.contains("\"content\":")) {
                    return parseContentFromJson(data);
                }
            } catch (Exception e) {
                log.debug("JSON解析失败: {}", e.getMessage());
            }
            return null;
        }

        /**
         * 提取SSE事件格式内容
         */
        private String extractSseEventContent(String data) {
            if (data.startsWith("data:")) {
                String jsonPart = data.substring(5).trim();
                return extractJsonContent(jsonPart);
            }
            return null;
        }

        /**
         * 判断是否为纯文本内容
         */
        private boolean isPureTextContent(String data) {
            return data != null
                    && !data.trim().isEmpty()
                    && !data.contains("{")
                    && !data.contains("[")
                    && !data.contains("data:")
                    && data.length() < 500; // 合理的文本长度
        }

        /**
         * 从事件字符串中解析内容
         */
        private String parseContentFromEventString(String eventString) {
            // 简单的字符串解析逻辑，可以根据实际格式优化
            if (eventString.contains("data:")) {
                int dataIndex = eventString.indexOf("data:");
                String dataContent = eventString.substring(dataIndex + 5).trim();
                return parseContentFromJson(dataContent);
            }
            return null;
        }

        /**
         * 从JSON字符串中解析内容
         */
        private String parseContentFromJson(String jsonStr) {
            // 简化的JSON解析，实际项目中建议使用Jackson
            if (jsonStr.contains("\"content\":\"")) {
                int startIndex = jsonStr.indexOf("\"content\":\"") + 11;
                int endIndex = jsonStr.indexOf("\"", startIndex);
                if (endIndex > startIndex) {
                    return jsonStr.substring(startIndex, endIndex);
                }
            }
            return null;
        }

        // 委托其他方法到原始emitter
        @Override
        public void onCompletion(Runnable callback) {
            delegate.onCompletion(callback);
        }

        @Override
        public void onError(Consumer<Throwable> callback) {
            delegate.onError(callback);
        }

        @Override
        public void onTimeout(Runnable callback) {
            delegate.onTimeout(callback);
        }
    }
}
