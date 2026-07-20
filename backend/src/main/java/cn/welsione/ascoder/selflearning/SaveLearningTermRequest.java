package cn.welsione.ascoder.selflearning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建或更新术语的请求体。
 */
@Data
public class SaveLearningTermRequest {
    @NotBlank
    @Size(max = 160)
    String term;
    String aliasesJson;
    @NotBlank
    String definition;
    String scope;
    String examples;
    @Size(max = 64)
    String source;
    Double confidence;
}
