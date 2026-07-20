package cn.welsione.ascoder.repository.projectspace;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * ProjectSpace 模块的 MapStruct 映射器，处理 Entity ↔ Response DTO 转换。
 */
@Mapper
public interface ProjectSpaceMapper {

    ProjectSpaceMapper INSTANCE = Mappers.getMapper(ProjectSpaceMapper.class);

    ProjectSpaceResponse toResponse(ProjectSpace space);
}
