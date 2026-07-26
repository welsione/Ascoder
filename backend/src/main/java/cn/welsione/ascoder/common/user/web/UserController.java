package cn.welsione.ascoder.common.user.web;

import cn.welsione.ascoder.common.user.UserService;
import cn.welsione.ascoder.common.user.web.dto.AssignRolesRequest;
import cn.welsione.ascoder.common.user.web.dto.CreateUserRequest;
import cn.welsione.ascoder.common.user.web.dto.ResetPasswordRequest;
import cn.welsione.ascoder.common.user.web.dto.UpdateUserRequest;
import cn.welsione.ascoder.common.user.web.dto.UserDetail;
import cn.welsione.ascoder.common.user.web.dto.UserSummary;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 用户管理 REST 接口（管理员）。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('USER:MANAGE')")
public class UserController {

    private final UserService userService;

    @GetMapping
    public List<UserSummary> list() {
        return userService.list();
    }

    @GetMapping("/{id}")
    public UserDetail get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDetail create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    public UserDetail update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request);
    }

    @PutMapping("/{id}/unlock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlock(@PathVariable Long id) {
        userService.unlock(id);
    }

    @PutMapping("/{id}/roles")
    public UserDetail assignRoles(@PathVariable Long id, @Valid @RequestBody AssignRolesRequest request) {
        return userService.assignRoles(id, request.getRoleCodes());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        userService.delete(id);
    }
}
