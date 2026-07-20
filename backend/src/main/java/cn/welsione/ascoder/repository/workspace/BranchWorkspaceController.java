package cn.welsione.ascoder.repository.workspace;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分支工作区 REST 控制器，所有响应均通过 DTO 序列化以避免暴露实体内部结构。
 */
@RestController
@RequiredArgsConstructor
public class BranchWorkspaceController {

    private final BranchWorkspaceService branchWorkspaceService;

    @GetMapping("/api/branch-workspaces")
    public List<BranchWorkspaceResponse> list(@RequestParam(required = false) Long repositoryId) {
        return branchWorkspaceService.list(repositoryId).stream().map(BranchWorkspaceResponse::from).toList();
    }

    @GetMapping("/api/repositories/{repositoryId}/git-branches")
    public List<GitBranchResponse> listBranches(@PathVariable Long repositoryId) {
        return branchWorkspaceService.listBranches(repositoryId);
    }

    @PostMapping("/api/repositories/{repositoryId}/branch-workspaces")
    @ResponseStatus(HttpStatus.CREATED)
    public BranchWorkspaceResponse prepare(
            @PathVariable Long repositoryId,
            @Valid @RequestBody CreateBranchWorkspaceRequest request
    ) {
        return BranchWorkspaceResponse.from(branchWorkspaceService.prepare(repositoryId, request));
    }

    @GetMapping("/api/branch-workspaces/{id}")
    public BranchWorkspaceResponse get(@PathVariable Long id) {
        return BranchWorkspaceResponse.from(branchWorkspaceService.get(id));
    }

    @PostMapping("/api/branch-workspaces/{id}/index")
    public BranchWorkspaceResponse index(@PathVariable Long id) {
        return BranchWorkspaceResponse.from(branchWorkspaceService.index(id));
    }

    @PostMapping("/api/branch-workspaces/{id}/refresh")
    public BranchWorkspaceResponse refresh(@PathVariable Long id) {
        return BranchWorkspaceResponse.from(branchWorkspaceService.refresh(id));
    }

    @DeleteMapping("/api/branch-workspaces/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        branchWorkspaceService.delete(id);
    }
}
