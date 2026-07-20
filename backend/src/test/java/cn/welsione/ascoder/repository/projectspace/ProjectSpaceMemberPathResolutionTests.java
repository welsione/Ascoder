package cn.welsione.ascoder.repository.projectspace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 验证 {@link ProjectSpaceMember#resolveLinkPath(String)} 在跨环境迁移场景下的路径重定位。
 *
 * <p>BUG 现场：本地开发时 linkPath 存为宿主机绝对路径，切到 Docker 后解析出的工作区路径
 * 指向容器内不存在的宿主机路径，导致 git 命令 chdir 失败。</p>
 */
class ProjectSpaceMemberPathResolutionTests {

    @Test
    void resolveLinkPathRedirectsLegacyAbsolutePathToCurrentRoot() {
        ProjectSpaceMember member = new ProjectSpaceMember();
        member.setLinkPath("/Users/someone/Ascoder/data/project-spaces/space-x/backend");

        String resolved = member.resolveLinkPath("/app/data/project-spaces");

        assertThat(resolved).isEqualTo("/app/data/project-spaces/space-x/backend");
    }

    @Test
    void resolveLinkPathKeepsAbsolutePathUnderCurrentRoot() {
        ProjectSpaceMember member = new ProjectSpaceMember();
        member.setLinkPath("/app/data/project-spaces/space-x/backend");

        String resolved = member.resolveLinkPath("/app/data/project-spaces");

        assertThat(resolved).isEqualTo("/app/data/project-spaces/space-x/backend");
    }

    @Test
    void resolveLinkPathResolvesRelativePathAgainstCurrentRoot() {
        ProjectSpaceMember member = new ProjectSpaceMember();
        member.setLinkPath("space-x/backend");

        String resolved = member.resolveLinkPath("/app/data/project-spaces");

        assertThat(resolved).isEqualTo("/app/data/project-spaces/space-x/backend");
    }

    @Test
    void resolveLinkPathReturnsNullWhenBlank() {
        ProjectSpaceMember member = new ProjectSpaceMember();

        assertThat(member.resolveLinkPath("/app/data/project-spaces")).isNull();
    }
}
