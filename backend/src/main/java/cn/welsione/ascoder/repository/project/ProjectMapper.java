package cn.welsione.ascoder.repository.project;

import cn.welsione.ascoder.common.TextUtil;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * Project 模块的 MapStruct 映射器，处理 Request → Entity 转换。
 */
@Mapper
public interface ProjectMapper {

    ProjectMapper INSTANCE = Mappers.getMapper(ProjectMapper.class);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "name", expression = "java(request.getName().trim())")
    @Mapping(target = "description", expression = "java(cn.welsione.ascoder.common.TextUtil.trimToNull(request.getDescription()))")
    Project toEntity(CreateProjectRequest request);
}
