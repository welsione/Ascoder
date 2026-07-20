package cn.welsione.ascoder.agent.extension.tool;

import java.util.List;

/**
 * 内置 Agent 工具目录，集中声明可在工具管理页启停的工具。
 */
public final class AgentToolCatalog {

    private AgentToolCatalog() {
    }

    public static List<AgentToolDefinition> all() {
        return List.of(
                tool("codegraph", "CodeGraph 工具组", "Built-in", "low", "代码索引、符号搜索、调用关系、影响面和受影响测试分析。"),
                tool("file", "文件检查工具组", "Built-in", "medium", "读取文件、列目录、查看文件信息和 glob 找文件。"),
                tool("text", "文本搜索工具组", "Built-in", "medium", "跨文件搜索、命中计数和单文件 grep。"),
                tool("git", "Git 只读工具组", "Built-in", "medium", "分支、提交、日志、diff、blame 和文件历史查询。"),
                tool("self-learning", "自学习知识工具组", "Built-in", "low", "检索项目空间正式知识、待审核洞察和原始学习记录。"),
                tool("command", "安全命令工具组", "Built-in", "high", "执行白名单只读命令和安全管道。"),
                tool("log", "日志探索工具组", "Built-in", "medium", "日志摘要、异常分组、搜索和片段读取。")
        );
    }

    private static AgentToolDefinition tool(String toolKey, String displayName, String groupName,
                                            String riskLevel, String description) {
        return new AgentToolDefinition(toolKey, displayName, groupName, riskLevel, description, true);
    }
}
