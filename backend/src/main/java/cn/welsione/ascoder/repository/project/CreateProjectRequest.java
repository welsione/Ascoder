package cn.welsione.ascoder.repository.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 创建项目的请求体，携带名称和描述。 */
@Data
public class CreateProjectRequest {

    @NotBlank
    @Size(max = 120)
    private String name;

    private String description;
}
