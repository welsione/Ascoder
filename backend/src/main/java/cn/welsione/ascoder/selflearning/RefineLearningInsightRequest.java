package cn.welsione.ascoder.selflearning;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 候选洞察微调请求，承载管理员对当前洞察的自然语言修改意图。
 */
@Data
public class RefineLearningInsightRequest {
    @NotBlank
    private String instruction;
}
