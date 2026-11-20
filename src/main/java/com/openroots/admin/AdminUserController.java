package com.openroots.admin;

import com.openroots.auth.CurrentUser;
import com.openroots.openapi.api.AdminApi;
import com.openroots.openapi.model.AdminUserApiResponse;
import com.openroots.openapi.model.AdminUserListApiResponse;
import com.openroots.openapi.model.AdminUserResponse;
import com.openroots.openapi.model.PlatformRole;
import com.openroots.openapi.model.PlatformRoleRequest;
import com.openroots.user.model.UserEntity;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminUserController implements AdminApi {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @Override
    public ResponseEntity<AdminUserListApiResponse> listUsers() {
        List<AdminUserResponse> users = adminUserService.listUsers(CurrentUser.email()).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(new AdminUserListApiResponse(true, "Users fetched", users, now()));
    }

    @Override
    public ResponseEntity<AdminUserApiResponse> updateUserPlatformRole(UUID userId, PlatformRoleRequest request) {
        UserEntity user = adminUserService.changePlatformRole(
                CurrentUser.email(),
                userId,
                com.openroots.user.model.PlatformRole.valueOf(request.getPlatformRole().name()));
        return ResponseEntity.ok(new AdminUserApiResponse(true, "Platform role updated", toResponse(user), now()));
    }

    private AdminUserResponse toResponse(UserEntity user) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                PlatformRole.valueOf(user.getPlatformRole().name()),
                user.getCreatedAt().atOffset(ZoneOffset.UTC));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
