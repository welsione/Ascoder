package cn.welsione.ascoder.repository.project;

import cn.welsione.ascoder.common.exception.DuplicateException;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.common.exception.ValidationException;
import cn.welsione.ascoder.repository.CodeRepository;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceService;
import cn.welsione.ascoder.repository.RepositoryService;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 项目领域服务，负责项目的创建查询及仓库成员的增删管理。 */
@Service
public class ProjectService {

    private final ProjectJpaRepository repository;
    private final ProjectRepositoryJpaRepository memberRepository;
    private final ProjectSpaceService projectSpaceService;
    private final RepositoryService repositoryService;

    public ProjectService(
            ProjectJpaRepository repository,
            ProjectRepositoryJpaRepository memberRepository,
            @Lazy ProjectSpaceService projectSpaceService,
            RepositoryService repositoryService
    ) {
        this.repository = repository;
        this.memberRepository = memberRepository;
        this.projectSpaceService = projectSpaceService;
        this.repositoryService = repositoryService;
    }

    @Transactional(readOnly = true)
    public List<Project> list() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Project get(Long id) {
        return getEntity(id);
    }

    @Transactional
    public Project create(CreateProjectRequest request) {
        String name = request.getName().trim();
        if (repository.existsByName(name)) {
            throw new DuplicateException("项目名称已存在");
        }

        Project project = ProjectMapper.INSTANCE.toEntity(request);
        try {
            return repository.save(project);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateException("项目名称已存在", ex);
        }
    }

    @Transactional(readOnly = true)
    public List<ProjectRepository> members(Long projectId) {
        getEntity(projectId);
        return memberRepository.findByProject_IdOrderBySortOrderAscCreatedAtAsc(projectId);
    }

    @Transactional
    public ProjectRepository addMember(Long projectId, AddProjectRepositoryRequest request) {
        Project project = getEntity(projectId);
        CodeRepository codeRepo = repositoryService.getEntity(request.getRepositoryId());
        String alias = alias(request.getAlias(), codeRepo.getName());
        if (memberRepository.existsByProject_IdAndRepository_Id(projectId, codeRepo.getId())) {
            throw new DuplicateException("项目已包含该仓库");
        }
        if (memberRepository.existsByProject_IdAndAlias(projectId, alias)) {
            throw new DuplicateException("项目中已存在该目录别名");
        }

        ProjectRepository member = new ProjectRepository();
        member.setProject(project);
        member.setRepository(codeRepo);
        member.setAlias(alias);
        member.setRole(defaultText(request.getRole(), "repository"));
        member.setPrimaryRepository(request.isPrimaryRepository());
        member.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        project.touch();
        this.repository.save(project);
        return memberRepository.save(member);
    }

    @Transactional
    public void removeMember(Long projectId, Long memberId) {
        ProjectRepository member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("项目仓库", memberId));
        if (!member.getProject().getId().equals(projectId)) {
            throw new ValidationException("项目仓库不属于当前项目");
        }
        memberRepository.delete(member);
        getEntity(projectId).touch();
    }

    @Transactional
    public void delete(Long id) {
        Project project = getEntity(id);
        // 先级联删除所有项目空间（含磁盘目录、成员、事件解绑）
        projectSpaceService.deleteByProjectId(id);
        // 删除项目仓库成员关联
        List<ProjectRepository> members = memberRepository.findByProject_IdOrderBySortOrderAscCreatedAtAsc(id);
        memberRepository.deleteAll(members);
        repository.delete(project);
    }

    @Transactional(readOnly = true)
    public Project getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("项目", id));
    }

    private String alias(String value, String fallback) {
        return defaultText(value, fallback).replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
