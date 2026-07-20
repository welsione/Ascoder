package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.agent.domain.AgentRequest;
import cn.welsione.ascoder.loganalysis.application.LogExploreContext;
import cn.welsione.ascoder.loganalysis.application.LogExploreTools;
import cn.welsione.ascoder.loganalysis.application.LogMaskingService;
import cn.welsione.ascoder.loganalysis.application.LogUploadService;
import cn.welsione.ascoder.loganalysis.domain.LogFile;
import cn.welsione.ascoder.loganalysis.domain.LogUpload;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Toolkit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 日志探索工具注册器，封装日志上传加载、脱敏与 LogExploreTools 装配，
 * 使工具装配器不直接持有日志相关依赖。
 */
@Slf4j
@Component
@RequiredArgsConstructor
class LogExploreToolRegistrar {

    private final LogUploadService logUploadService;
    private final LogMaskingService logMaskingService;
    private final ObjectMapper objectMapper;

    /**
     * 按请求中的 logUploadIds 注册日志探索工具。每个上传 ID 对应一组只读日志工具。
     * 单个上传加载失败时记录警告并跳过，不影响其它上传。
     */
    void register(Toolkit toolkit, AgentRequest request, Set<String> enabledTools) {
        List<Long> logUploadIds = request.getLogUploadIds();
        if (logUploadIds == null || logUploadIds.isEmpty()) {
            return;
        }
        for (Long logUploadId : logUploadIds) {
            try {
                LogUpload upload = logUploadService.get(logUploadId);
                List<LogFile> files = logUploadService.listFiles(logUploadId);
                LogExploreContext logContext = new LogExploreContext(upload, files);
                if (enabledTools.contains("log")) {
                    toolkit.registration().tool(
                            new LogExploreTools(logContext, objectMapper, logMaskingService)).apply();
                    log.info("注入日志探索工具，logUploadId={}, fileCount={}", logUploadId, files.size());
                }
            } catch (RuntimeException ex) {
                log.warn("注入日志探索工具失败，logUploadId={}, 错误={}", logUploadId, ex.getMessage());
            }
        }
    }
}
