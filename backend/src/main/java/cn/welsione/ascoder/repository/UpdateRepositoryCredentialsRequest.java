package cn.welsione.ascoder.repository;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新仓库认证凭据的请求体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRepositoryCredentialsRequest {
    @Size(max = 255)
    String authUsername;
    @Size(max = 4096)
    String authPassword;
}
