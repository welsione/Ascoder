package cn.welsione.ascoder.common.task;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
 * 异步任务通用查询和取消端点。
 *
 * <p>前端可通过这些端点查询任务状态、进度和取消任务。</p>
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class AsyncTaskController {

    private final TaskEngine taskEngine;

    /**
     * 分页查询任务列表，支持按类型和状态筛选。
     */
    @GetMapping
    public Page<AsyncTask> list(
            @RequestParam(required = false) TaskKind kind,
            @RequestParam(required = false) List<TaskStatus> status,
            @PageableDefault(size = 20, sort = "queuedAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return taskEngine.list(kind, status, pageable);
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
}
