package cn.welsione.ascoder.agent.extension.config;

import cn.welsione.ascoder.agent.infrastructure.prompt.TaskPromptContext;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务模板渲染预览请求体，传入样例上下文用于 testRender。
 */
@Data
@NoArgsConstructor
public class TestRenderRequest {
    private TaskPromptContext sampleContext;
}
