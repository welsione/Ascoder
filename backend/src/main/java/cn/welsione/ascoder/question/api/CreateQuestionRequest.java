package cn.welsione.ascoder.question.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 创建问题的请求体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateQuestionRequest {
    @NotNull
    Long projectSpaceId;
    Long conversationId;
    @NotBlank @Size(max = 8000)
    String text;
    @Size(max = 64)
    String role;
    List<Long> logUploadIds;
}
