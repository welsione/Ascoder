package cn.welsione.ascoder.repository.workspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 创建分支工作区的请求体，携带分支名称。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateBranchWorkspaceRequest {

    @NotBlank
    @Size(max = 255)
    private String branchName;
}
