package cn.welsione.ascoder.repository.projectspace;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 创建项目空间的请求体，携带所属项目、名称、默认分支及成员分支配置。 */
@Data
public class CreateProjectSpaceRequest {

    @NotNull
    private Long projectId;

    @NotBlank
    @Size(max = 120)
    private String name;

    private String description;

    @NotBlank
    @Size(max = 255)
    private String defaultBranch;

    @Valid
    private List<MemberBranchRequest> members;

    @Data
    public static class MemberBranchRequest {
        @NotNull
        private Long repositoryId;

        private Long branchId;

        @Size(max = 255)
        private String branchName;

        @Size(max = 120)
        private String alias;

        @Size(max = 64)
        private String role;
    }
}
