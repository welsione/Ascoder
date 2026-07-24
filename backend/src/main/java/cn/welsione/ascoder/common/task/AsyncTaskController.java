package cn.welsione.ascoder.common.task;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 异步任务通用查询、取消、重试和清理端点。
 *
 * <p>前端可通过这些端点查询任务状态、进度、取消任务、重试失败任务和清理僵尸任务。</p>
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class AsyncTaskController {

    private static final long DEFAULT_STALE_THRESHOLD_MS = 24 * 60 * 60 * 1000L;

    private final TaskEngine taskEngine;

    /**
     * 分页查询任务列表，支持按类型和状态筛选。
     *
     * <p>返回的 AsyncTaskView 包含 businessLabel 字段，将 businessId 解析为可读标签。</p>
     */
    @GetMapping
    public Page<AsyncTaskView> list(
            @RequestParam(required = false) TaskKind kind,
            @RequestParam(required = false) List<TaskStatus> status,
            @PageableDefault(size = 20, sort = "queuedAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        Page<AsyncTask> page = taskEngine.list(kind, status, pageable);
        List<AsyncTaskView> views = page.getContent().stream()
                .map(task -> AsyncTaskView.from(task, taskEngine.resolveBusinessLabel(task.getKind(), task.getBusinessId())))
                .toList();
        return new PageImpl<>(views, page.getPageable(), page.getTotalElements());
    }

    /**
     * 按 kind + businessId 查询活跃任务。
     */
    @GetMapping("/active")
    public TaskHandle findByKindAndBusinessId(
            @RequestParam TaskKind kind,
            @RequestParam Long businessId) {
        return taskEngine.findByKindAndBusinessId(kind, businessId);
    }

    /**
     * 查询单个任务。
     */
    @GetMapping("/{taskId}")
    public TaskHandle get(@PathVariable Long taskId) {
        TaskHandle handle = taskEngine.getHandle(taskId);
        if (handle == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }
        return handle;
    }

    /**
     * 取消任务。
     */
    @PostMapping("/{taskId}/cancel")
    public TaskHandle cancel(@PathVariable Long taskId) {
        return taskEngine.cancel(taskId);
    }

    /**
     * 重试失败或已取消的任务。
     */
    @PostMapping("/{taskId}/retry")
    public TaskHandle retry(@PathVariable Long taskId) {
        return taskEngine.retry(taskId);
    }

    /**
     * 清理僵尸任务：将超过 24 小时仍在 QUEUED/RUNNING 状态的任务标记为 FAILED。
     *
     * @param staleThresholdHours 超时阈值（小时），默认 24
     * @return 被清理的任务数量
     */
    @PostMapping("/cleanup")
    public int cleanupStaleTasks(
            @RequestParam(required = false, defaultValue = "24") int staleThresholdHours) {
        return taskEngine.cleanupStaleTasks(staleThresholdHours * DEFAULT_STALE_THRESHOLD_MS / 24);
    }
}
