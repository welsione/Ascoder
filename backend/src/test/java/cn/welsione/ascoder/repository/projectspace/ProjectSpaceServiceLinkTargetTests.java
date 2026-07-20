package cn.welsione.ascoder.repository.projectspace;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * 验证项目空间软链接 target 使用相对路径，使其在本地开发与 Docker 部署间可移植。
 *
 * <p>BUG 根因：软链接 target 存为绝对路径，本地创建后切到 Docker 即 broken。
 * 相对路径以 linkPath 所在目录为基准，因 project-spaces 与 worktrees 同在 data/ 下，
 * 相对关系在两种环境一致。</p>
 */
class ProjectSpaceServiceLinkTargetTests {

    @Test
    void relativeLinkTargetCrossesFromProjectSpacesToWorktreesInContainer() {
        // Docker 内布局：/app/data/{project-spaces,worktrees}
        Path linkPath = Path.of("/app/data/project-spaces/space-x/backend");
        Path target = Path.of("/app/data/worktrees/repo/branch");

        Path relative = ProjectSpaceService.relativeLinkTarget(linkPath, target);

        assertThat(relative.toString()).isEqualTo("../../worktrees/repo/branch");
        // 关键：从 linkPath 所在目录解析回 target，与 target 等价
        assertThat(linkPath.getParent().resolve(relative).normalize()).isEqualTo(target);
    }

    @Test
    void relativeLinkTargetCrossesFromProjectSpacesToWorktreesOnHost() {
        // 本地布局：<project>/data/{project-spaces,worktrees}，相对关系与容器内一致
        Path linkPath = Path.of("/Users/x/Ascoder/data/project-spaces/space-x/backend");
        Path target = Path.of("/Users/x/Ascoder/data/worktrees/repo/branch");

        Path relative = ProjectSpaceService.relativeLinkTarget(linkPath, target);

        assertThat(relative.toString()).isEqualTo("../../worktrees/repo/branch");
        assertThat(linkPath.getParent().resolve(relative).normalize()).isEqualTo(target);
    }
}
