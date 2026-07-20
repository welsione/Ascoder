package cn.welsione.ascoder.repository.workspace;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * BranchWorkspace 模块的 MapStruct 映射器，处理 Entity → Response DTO 转换。
 */
@Mapper
public interface BranchWorkspaceMapper {

    BranchWorkspaceMapper INSTANCE = Mappers.getMapper(BranchWorkspaceMapper.class);

    BranchWorkspaceResponse toResponse(BranchWorkspace workspace);
}
