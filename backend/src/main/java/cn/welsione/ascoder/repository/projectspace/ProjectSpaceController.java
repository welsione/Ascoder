package cn.welsione.ascoder.repository.projectspace;

import cn.welsione.ascoder.codegraph.infrastructure.cli.IndexProgressTracker;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 项目空间 REST 控制器，所有响应均通过 DTO 序列化以避免暴露实体内部结构。
 */
@RestController
@RequestMapping("/api/project-spaces")
@RequiredArgsConstructor
public class ProjectSpaceController {

    private final ProjectSpaceService projectSpaceService;
    private final IndexProgressTracker indexProgressTracker;

    @GetMapping
    public List<ProjectSpaceResponse> list() {
        return projectSpaceService.list().stream().map(ProjectSpaceResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectSpaceResponse create(@Valid @RequestBody CreateProjectSpaceRequest request) {
        return ProjectSpaceResponse.from(projectSpaceService.create(request));
    }

    @GetMapping("/{id}")
    public ProjectSpaceResponse get(@PathVariable Long id) {
        return ProjectSpaceResponse.from(projectSpaceService.get(id));
    }

    @GetMapping("/{id}/members")
    public List<ProjectSpaceMemberResponse> members(@PathVariable Long id) {
        return projectSpaceService.memberResponses(id);
    }

    @PostMapping("/{id}/prepare")
    public ProjectSpaceResponse prepare(@PathVariable Long id) {
        return ProjectSpaceResponse.from(projectSpaceService.prepare(id));
    }

    @PostMapping("/{id}/index")
    public ProjectSpaceResponse index(@PathVariable Long id) {
        return ProjectSpaceResponse.from(projectSpaceService.index(id));
    }

    @PostMapping("/{id}/reindex")
    public ProjectSpaceResponse reindex(@PathVariable Long id) {
        return ProjectSpaceResponse.from(projectSpaceService.reindex(id));
    }

    @PostMapping("/{id}/refresh")
    public ProjectSpaceResponse refresh(@PathVariable Long id) {
        return ProjectSpaceResponse.from(projectSpaceService.refresh(id));
    }

    @PostMapping("/{id}/pull")
    public ProjectSpaceResponse pull(@PathVariable Long id) {
        return ProjectSpaceResponse.from(projectSpaceService.pullRemote(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        projectSpaceService.delete(id);
    }

    @GetMapping("/{id}/index-progress")
    public Map<String, Object> indexProgress(@PathVariable Long id) {
        IndexProgressTracker.IndexProgress progress = indexProgressTracker.get(id);
        return Map.of(
                "percent", progress.getPercent(),
                "message", progress.getMessage(),
                "completed", progress.isCompleted()
        );
    }
}
