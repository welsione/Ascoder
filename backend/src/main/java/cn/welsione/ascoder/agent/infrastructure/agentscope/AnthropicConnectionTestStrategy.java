package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.agent.domain.ConnectionTestResult;
import cn.welsione.ascoder.agent.domain.LlmProviderType;
import cn.welsione.ascoder.agent.domain.ResolvedModelConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Anthropic 兼容协议的连接测试策略。
 *
 * <p>向 {baseUrl}/v1/messages 发送最小化请求，验证 apiKey 和 baseUrl 是否正确。</p>
 */
@Slf4j
@Component
public class AnthropicConnectionTestStrategy implements ConnectionTestStrategy {

    private static final int TIMEOUT_SECONDS = 10;

    @Override
    public boolean supports(String providerType) {
        return LlmProviderType.ANTHROPIC_COMPATIBLE.name().equals(providerType);
    }

    @Override
    public ConnectionTestResult test(ResolvedModelConfig config) {
        String url = config.getBaseUrl().replaceAll("/+$", "") + "/v1/messages";
        long start = System.currentTimeMillis();
        try {
            String body = """
                    {"model":"%s","max_tokens":1,"messages":[{"role":"user","content":"hi"}]}
                    """.formatted(config.getModelId());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", config.getApiKey())
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .build();
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;
            int code = response.statusCode();
            if (code == 200 || code == 201) {
                return new ConnectionTestResult(true, "连接成功", latency);
            }
            // Anthropic API 对无效请求返回 400，但 apiKey 正确时 400 也算连接成功
            if (code == 400) {
                return new ConnectionTestResult(true, "连接成功（API 返回 400，密钥有效）", latency);
            }
            if (code == 401 || code == 403) {
                return new ConnectionTestResult(false, "认证失败（HTTP " + code + "）", latency);
            }
            return new ConnectionTestResult(false, "API 返回 HTTP " + code, latency);
        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - start;
            String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            return new ConnectionTestResult(false, "连接失败：" + msg, latency);
        }
    }
}
