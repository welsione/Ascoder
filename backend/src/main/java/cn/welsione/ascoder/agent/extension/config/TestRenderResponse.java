package cn.welsione.ascoder.agent.extension.config;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 任务模板渲染预览响应，含渲染后文本与未解析占位符告警。
 */
@Data
@AllArgsConstructor
public class TestRenderResponse {
    private String renderedText;
    private List<String> warnings;
}
