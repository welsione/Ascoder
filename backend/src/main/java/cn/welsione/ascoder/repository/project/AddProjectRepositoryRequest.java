package cn.welsione.ascoder.repository.project;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 向项目添加仓库的请求体，携带仓库、别名、角色及是否主仓库等信息。 */
@Data
public class AddProjectRepositoryRequest {

    @NotNull
    private Long repositoryId;

    @Size(max = 120)
    private String alias;

    @Size(max = 64)
    private String role;

    private boolean primaryRepository;

    private Integer sortOrder;
}
