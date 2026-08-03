package cn.welsione.ascoder.repository;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 重命名代码仓库的请求体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RenameRepositoryRequest {
    @NotBlank @Size(max = 120)
    String name;
}
