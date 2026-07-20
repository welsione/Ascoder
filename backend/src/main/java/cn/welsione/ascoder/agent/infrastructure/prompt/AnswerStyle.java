package cn.welsione.ascoder.agent.infrastructure.prompt;

import cn.welsione.jprompt.JPrompt;

/**
 * 回答风格枚举，根据用户角色差异化输出格式。
 *
 * <p>每个风格的指令内容从 prompts/answer-style-{roleKey}.md 模板一次性加载，
 * 在 static 初始化块中完成，后续不再调用 JPrompt。修改回答要求时只需编辑对应 .md 文件，无需改 Java 代码。</p>
 */
public enum AnswerStyle {

    DEVELOPER("developer"),
    PRODUCT_MANAGER("product_manager"),
    TESTER("tester");

    private final String roleKey;
    private String instruction;

    static {
        // 静态初始化块中一次性加载所有指令内容
        for (AnswerStyle style : values()) {
            style.instruction = JPrompt.get("prompts/answer-style-" + style.roleKey + ".md").get();
        }
    }

    AnswerStyle(String roleKey) {
        this.roleKey = roleKey;
    }

    public String getRoleKey() {
        return roleKey;
    }

    public String getInstruction() {
        return instruction;
    }

    /**
     * 根据角色字符串映射到对应的回答风格。
     *
     * @param role 角色字符串，如 "developer"、"product_manager"、"tester"
     * @return 对应的 AnswerStyle，默认返回 DEVELOPER
     */
    public static AnswerStyle fromRole(String role) {
        if (role == null || role.isBlank()) {
            return DEVELOPER;
        }
        String normalized = role.trim().toLowerCase().replace("-", "_").replace(" ", "_");
        for (AnswerStyle style : values()) {
            if (style.roleKey.equals(normalized)) {
                return style;
            }
        }
        return DEVELOPER;
    }
}
