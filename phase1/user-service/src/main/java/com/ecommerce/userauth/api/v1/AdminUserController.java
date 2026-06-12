package com.ecommerce.userauth.api.v1;

import com.ecommerce.userauth.api.v1.dto.DeactivateUserResponse;
import com.ecommerce.userauth.api.v1.dto.UserListResponse;
import com.ecommerce.userauth.domain.UserRole;
import com.ecommerce.userauth.domain.UserStatus;
import com.ecommerce.userauth.service.AdminUserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin-only user management per docs/api-specs/user-service-api.yaml `/admin/users` paths
 * and docs/lld/user-auth-lld.md §8.1 (LLD-SD-01). Access restricted to {@code ROLE_ADMIN}
 * (SecurityConfig).
 */
@RestController
@RequestMapping("/v1/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public UserListResponse listUsers(@RequestParam(required = false) UserStatus status,
                                       @RequestParam(required = false) UserRole role,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return UserListResponse.from(adminUserService.listUsers(status, role, pageable));
    }

    @PostMapping("/{userId}/deactivate")
    public DeactivateUserResponse deactivateUser(@PathVariable UUID userId) {
        return DeactivateUserResponse.from(adminUserService.deactivateUser(userId));
    }
}
