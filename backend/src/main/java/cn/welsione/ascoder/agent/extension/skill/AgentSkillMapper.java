package cn.welsione.ascoder.agent.extension.skill;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * Skill 模块的 MapStruct 映射器，处理 Request → Entity 转换。
 */
@Mapper
public interface AgentSkillMapper {

    AgentSkillMapper INSTANCE = Mappers.getMapper(AgentSkillMapper.class);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "name", expression = "java(request.getName().trim())")
    @Mapping(target = "description", expression = "java(request.getDescription().trim())")
    @Mapping(target = "skillContent", expression = "java(request.getSkillContent().trim())")
    @Mapping(target = "source", expression = "java(request.getSource() == null || request.getSource().isBlank() ? \"manual\" : request.getSource().trim())")
    AgentSkillConfig toEntity(CreateAgentSkillRequest request);
}
