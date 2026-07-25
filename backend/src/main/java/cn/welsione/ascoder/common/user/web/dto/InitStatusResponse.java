package cn.welsione.ascoder.common.user.web.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 系统初始化状态响应，用于前端判断是否需要引导注册。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InitStatusResponse {

    private boolean initialized;
}
