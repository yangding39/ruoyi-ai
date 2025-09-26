package org.ruoyi.constant;

/**
 * 知识库类型枚举
 *
 * @author ruoyi
 */
public enum KnowledgeProviderType {

    /**
     * 本地知识库
     */
    LOCAL("local", "本地知识库"),

    /**
     * 外部知识库
     */
    EXTERNAL("external", "外部知识库"),

    /**
     * FastGPT知识库
     */
    FASTGPT("fastgpt", "FastGPT知识库"),

    /**
     * DIFY知识库
     */
    DIFY("dify", "DIFY知识库"),

    /**
     * Coze知识库
     */
    COZE("coze", "Coze知识库");

    private final String code;
    private final String description;

    KnowledgeProviderType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static KnowledgeProviderType fromCode(String code) {
        for (KnowledgeProviderType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown knowledge provider type: " + code);
    }
}