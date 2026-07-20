package cn.welsione.ascoder.runtime.web;

import cn.welsione.ascoder.runtime.application.RuntimeSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 运行时设置 REST 控制器。
 *
 * <p>提供全量 / 分类查询、单条更新、分类重置四个端点；写入操作需满足白名单与类型校验。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class RuntimeSettingsController {

    private final RuntimeSettingsService service;

    @GetMapping
    public List<RuntimeSettingsService.SettingView> list() {
        return service.listAll();
    }

    @GetMapping("/{category}")
    public List<RuntimeSettingsService.SettingView> listByCategory(@PathVariable String category) {
        return service.listByCategory(category);
    }

    @PutMapping("/{key}")
    public RuntimeSettingsService.SettingView update(@PathVariable String key,
                                                     @Valid @RequestBody UpdateSettingRequest request) {
        service.write(key, request.getValue());
        // 返回最新视图：再读一次拿合并值
        return service.listAll().stream()
                .filter(v -> v.getKey().equals(key))
                .findFirst()
                .orElseThrow();
    }

    @PostMapping("/reset/{category}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@PathVariable String category) {
        service.reset(category);
    }
}