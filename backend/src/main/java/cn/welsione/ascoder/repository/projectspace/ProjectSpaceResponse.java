package cn.welsione.ascoder.repository.projectspace;

import lombok.Value;

import java.util.Date;

/**
 * 项目空间响应 DTO。将实体与 Controller 解耦，避免 JPA 实体直接序列化导致字段意外暴露或懒加载异常。
 */
@Value
public class ProjectSpaceResponse {
    Long id;
    Long projectId;
    String projectName;
    String name;
    String description;
    String rootPath;
    String codegraphIndexPath;
    ProjectSpaceStatus status;
    Date lastPreparedAt;
    Date lastIndexedAt;
    String lastError;
    Date createdAt;
    Date updatedAt;

    public static ProjectSpaceResponse from(ProjectSpace space) {
        return ProjectSpaceMapper.INSTANCE.toResponse(space);
    }
}
