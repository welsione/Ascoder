package cn.welsione.ascoder.repository.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 验证 {@link BranchWorkspace#resolveWorktreePath(String)} 在跨环境迁移场景下的路径重定位。
 *
 * <p>BUG 现场：本地开发时 worktreePath 存为宿主机绝对路径，切到 Docker 后软链接 target
 * 指向容器内不存在的宿主机路径，导致 broken symlink。</p>
 */
class BranchWorkspacePathResolutionTests {

    @Test
    void resolveWorktreePathRedirectsLegacyAbsolutePathToCurrentRoot() {
        BranchWorkspace workspace = new BranchWorkspace();
        // 旧数据：本地开发时存的宿主机绝对路径，Docker 容器内不可达
        workspace.setWorktreePath("/Users/someone/Ascoder/data/worktrees/demo/main");

        String resolved = workspace.resolveWorktreePath("/app/data/worktrees");

        assertThat(resolved).isEqualTo("/app/data/worktrees/demo/main");
    }

    @Test
    void resolveWorktreePathKeepsAbsolutePathUnderCurrentRoot() {
        BranchWorkspace workspace = new BranchWorkspace();
        workspace.setWorktreePath("/app/data/worktrees/demo/main");

        String resolved = workspace.resolveWorktreePath("/app/data/worktrees");

        assertThat(resolved).isEqualTo("/app/data/worktrees/demo/main");
    }

    @Test
    void resolveWorktreePathResolvesRelativePathAgainstCurrentRoot() {
        BranchWorkspace workspace = new BranchWorkspace();
        workspace.setWorktreePath("demo/main");

        String resolved = workspace.resolveWorktreePath("/app/data/worktrees");

        assertThat(resolved).isEqualTo("/app/data/worktrees/demo/main");
    }

    @Test
    void resolveWorktreePathReturnsNullWhenBlank() {
        BranchWorkspace workspace = new BranchWorkspace();

        assertThat(workspace.resolveWorktreePath("/app/data/worktrees")).isNull();
    }
}
