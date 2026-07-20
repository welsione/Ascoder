package cn.welsione.ascoder.runtime.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateSettingRequest {

    @NotBlank
    @Size(max = 500)
    private String value;
}