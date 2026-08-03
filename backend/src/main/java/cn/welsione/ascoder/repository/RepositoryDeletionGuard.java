package cn.welsione.ascoder.repository;

import cn.welsione.ascoder.repository.project.ProjectRepositoryJpaRepository;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceMemberJpaRepository;
import cn.welsione.ascoder.repository.workspace.BranchWorkspaceJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 仓库删除前的引用检查守卫。
 *
 * <p>仓库被项目仓库成员、项目空间成员、分支工作区、分支引用等强关联（NOT NULL 外键）引用时，
 * 删除会破坏数据完整性，故删除前必须先解除这些引用。本守卫集中检查强关联引用，
 * 返回首个阻断原因供 Service 抛出 {@link cn.welsione.ascoder.common.exception.InvalidStateException}。</p>
 *
 * <p>questions / conversations / selflearning 等通过 nullable 外键引用仓库的聚合，
 * 由 {@link RepositoryDeletedEvent} 监听器在删除时 detach，不在此处阻断。</p>
 */
@Component
@RequiredArgsConstructor
public class RepositoryDeletionGuard {

    private final ProjectRepositoryJpaRepository projectRepositoryMemberRepository;
    private final ProjectSpaceMemberJpaRepository projectSpaceMemberRepository;
    private final BranchWorkspaceJpaRepository branchWorkspaceRepository;
    private final RepositoryBranchJpaRepository branchRepository;

    /**
     * 检查仓库是否被强关联引用，返回阻断原因；无引用时返回 null。
     *
     * @param repositoryId 仓库 ID
     * @return 阻断原因描述，null 表示可安全删除
     */
    public String checkBlocking(Long repositoryId) {
        if (projectRepositoryMemberRepository.existsByRepository_Id(repositoryId)) {
            return "仓库已被项目引用，请先从相关项目中移除该仓库";
        }
        if (projectSpaceMemberRepository.existsByRepository_Id(repositoryId)) {
            return "仓库已被项目空间引用，请先删除相关项目空间成员";
        }
        if (branchWorkspaceRepository.existsByRepository_Id(repositoryId)) {
            return "仓库存在分支工作区，请先删除相关分支工作区";
        }
        if (branchRepository.existsByRepository_Id(repositoryId)) {
            return "仓库存在分支引用，请先刷新或清理分支";
        }
        return null;
    }
}
