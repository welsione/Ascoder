package cn.welsione.ascoder.repository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 代码仓库 REST 控制器，提供仓库 CRUD 和索引触发接口。
 */
@RestController
@RequestMapping("/api/repositories")
@RequiredArgsConstructor
public class RepositoryController {

    private final RepositoryService repositoryService;
    private final RepositoryBranchService repositoryBranchService;

    @GetMapping
    public List<CodeRepository> list() {
        return repositoryService.list();
    }

    @GetMapping("/{id}")
    public CodeRepository get(@PathVariable Long id) {
        return repositoryService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CodeRepository create(@Valid @RequestBody CreateRepositoryRequest request) {
        return repositoryService.create(request);
    }

    @PostMapping("/{id}/index")
    public CodeRepository index(@PathVariable Long id) {
        return repositoryService.index(id);
    }

    @PostMapping("/{id}/fetch")
    public CodeRepository fetch(@PathVariable Long id) {
        return repositoryService.fetch(id);
    }

    @PostMapping("/{id}/pull")
    public CodeRepository pull(@PathVariable Long id) {
        return repositoryService.pull(id);
    }

    @PatchMapping("/{id}/credentials")
    public CodeRepository updateCredentials(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRepositoryCredentialsRequest request) {
        return repositoryService.updateCredentials(id, request);
    }

    @GetMapping("/{id}/index-status")
    public CodeRepository indexStatus(@PathVariable Long id) {
        return repositoryService.indexStatus(id);
    }

    @GetMapping("/{id}/branches")
    public List<RepositoryBranchResponse> branches(@PathVariable Long id) {
        return repositoryBranchService.list(id).stream().map(RepositoryBranchResponse::from).toList();
    }

    @PostMapping("/{id}/branches/refresh")
    public List<RepositoryBranchResponse> refreshBranches(@PathVariable Long id) {
        return repositoryBranchService.refresh(id).stream().map(RepositoryBranchResponse::from).toList();
    }
}
