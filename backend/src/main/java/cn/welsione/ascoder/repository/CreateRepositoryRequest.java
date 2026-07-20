package cn.welsione.ascoder.repository;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建代码仓库的请求体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateRepositoryRequest {
    @NotBlank @Size(max = 120)
    String name;
    @Size(max = 4096)
    String localPath;
    @Size(max = 4096)
    String remoteUrl;
    @Size(max = 255)
    String defaultBranch;
    @Size(max = 255)
    String authUsername;
    @Size(max = 4096)
    String authPassword;
}
