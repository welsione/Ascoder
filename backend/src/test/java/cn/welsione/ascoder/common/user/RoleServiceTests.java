package cn.welsione.ascoder.common.user;

import cn.welsione.ascoder.common.exception.DuplicateException;
import cn.welsione.ascoder.common.exception.InvalidStateException;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.common.user.domain.Permission;
import cn.welsione.ascoder.common.user.domain.Role;
import cn.welsione.ascoder.common.user.domain.RolePermission;
import cn.welsione.ascoder.common.user.persistence.PermissionJpaRepository;
import cn.welsione.ascoder.common.user.persistence.RoleJpaRepository;
import cn.welsione.ascoder.common.user.persistence.RolePermissionJpaRepository;
import cn.welsione.ascoder.common.user.web.dto.CreateRoleRequest;
import cn.welsione.ascoder.common.user.web.dto.RoleDetail;
import cn.welsione.ascoder.common.user.web.dto.RoleSummary;
import cn.welsione.ascoder.common.user.web.dto.UpdateRoleRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RoleService 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class RoleServiceTests {

    @Mock
    private RoleJpaRepository roleRepository;

    @Mock
    private PermissionJpaRepository permissionRepository;

    @Mock
    private RolePermissionJpaRepository rolePermissionRepository;

    @InjectMocks
    private RoleService roleService;

    private Role adminRole;
    private Role userRole;

    @BeforeEach
    void setUp() {
        adminRole = new Role();
        adminRole.setId(1L);
        adminRole.setCode("ADMIN");
        adminRole.setName("管理员");
        adminRole.setBuiltin(true);
        adminRole.setEnabled(true);

        userRole = new Role();
        userRole.setId(2L);
        userRole.setCode("USER");
        userRole.setName("普通用户");
        userRole.setBuiltin(true);
        userRole.setEnabled(true);
    }

    @Nested
    class ListRoles {

        @Test
        void shouldReturnAllRoles() {
            when(roleRepository.findAll()).thenReturn(List.of(adminRole, userRole));
            when(rolePermissionRepository.findByRoleId(1L)).thenReturn(List.of());
            when(rolePermissionRepository.findByRoleId(2L)).thenReturn(List.of());

            List<RoleSummary> result = roleService.list();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getCode()).isEqualTo("ADMIN");
            assertThat(result.get(1).getCode()).isEqualTo("USER");
        }
    }

    @Nested
    class CreateRole {

        @Test
        void shouldCreateCustomRole() {
            CreateRoleRequest request = new CreateRoleRequest();
            request.setCode("VIEWER");
            request.setName("查看者");
            request.setDescription("只读角色");

            when(roleRepository.findByCode("VIEWER")).thenReturn(Optional.empty());
            Role savedRole = new Role();
            savedRole.setId(3L);
            savedRole.setCode("VIEWER");
            savedRole.setBuiltin(false);
            when(roleRepository.save(any(Role.class))).thenReturn(savedRole);
            when(roleRepository.findById(3L)).thenReturn(Optional.of(savedRole));
            when(rolePermissionRepository.findByRoleId(3L)).thenReturn(List.of());

            RoleDetail detail = roleService.create(request);

            assertThat(detail.getCode()).isEqualTo("VIEWER");
            assertThat(detail.isBuiltin()).isFalse();
            verify(roleRepository).save(any(Role.class));
        }

        @Test
        void shouldRejectDuplicateCode() {
            CreateRoleRequest request = new CreateRoleRequest();
            request.setCode("ADMIN");
            request.setName("重复管理员");

            when(roleRepository.findByCode("ADMIN")).thenReturn(Optional.of(adminRole));

            assertThatThrownBy(() -> roleService.create(request))
                    .isInstanceOf(DuplicateException.class);
        }
    }

    @Nested
    class DeleteRole {

        @Test
        void shouldDeleteCustomRole() {
            Role customRole = new Role();
            customRole.setId(3L);
            customRole.setCode("VIEWER");
            customRole.setBuiltin(false);

            when(roleRepository.findById(3L)).thenReturn(Optional.of(customRole));

            roleService.delete(3L);

            verify(roleRepository).delete(customRole);
        }

        @Test
        void shouldRejectDeletingBuiltinRole() {
            when(roleRepository.findById(1L)).thenReturn(Optional.of(adminRole));

            assertThatThrownBy(() -> roleService.delete(1L))
                    .isInstanceOf(InvalidStateException.class)
                    .hasMessageContaining("内置角色不可删除");
        }
    }

    @Nested
    class AssignPermissions {

        @Test
        void shouldReplacePermissionsUsingDeleteByRoleId() {
            Permission perm1 = new Permission();
            perm1.setId(10L);
            perm1.setCode("PROJECT:READ");

            when(roleRepository.findById(2L)).thenReturn(Optional.of(userRole));
            when(permissionRepository.findByCode("PROJECT:READ")).thenReturn(Optional.of(perm1));
            when(rolePermissionRepository.findByRoleId(2L)).thenReturn(List.of());
            when(rolePermissionRepository.save(any(RolePermission.class))).thenAnswer(inv -> inv.getArgument(0));

            RoleDetail detail = roleService.assignPermissions(2L, Set.of("PROJECT:READ"));

            verify(rolePermissionRepository).deleteByRoleId(2L);
            verify(rolePermissionRepository).save(any(RolePermission.class));
            assertThat(detail.getId()).isEqualTo(2L);
        }

        @Test
        void shouldThrowWhenPermissionNotFound() {
            when(roleRepository.findById(2L)).thenReturn(Optional.of(userRole));
            when(permissionRepository.findByCode("NONEXISTENT")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> roleService.assignPermissions(2L, Set.of("NONEXISTENT")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
