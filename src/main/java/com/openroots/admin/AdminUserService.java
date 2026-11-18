package com.openroots.admin;

import com.openroots.common.exception.NotFoundException;
import com.openroots.user.model.PlatformRole;
import com.openroots.user.model.UserEntity;
import com.openroots.user.repository.UserRepository;
import com.openroots.user.service.PlatformAuthorizationService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform administration of user accounts. Every operation requires the caller to be a
 * platform ADMIN, and the platform must always keep at least one ADMIN.
 */
@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final PlatformAuthorizationService platformAuthorizationService;

    public AdminUserService(UserRepository userRepository, PlatformAuthorizationService platformAuthorizationService) {
        this.userRepository = userRepository;
        this.platformAuthorizationService = platformAuthorizationService;
    }

    @Transactional(readOnly = true)
    public List<UserEntity> listUsers(String callerEmail) {
        platformAuthorizationService.requireAdmin(callerEmail);
        return userRepository.findAllByOrderByEmailAsc();
    }

    @Transactional
    public UserEntity changePlatformRole(String callerEmail, UUID userId, PlatformRole newRole) {
        platformAuthorizationService.requireAdmin(callerEmail);
        UserEntity target = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
        if (target.getPlatformRole() == PlatformRole.ADMIN
                && newRole != PlatformRole.ADMIN
                && userRepository.countByPlatformRole(PlatformRole.ADMIN) <= 1) {
            throw new IllegalArgumentException("The platform must keep at least one administrator");
        }
        target.setPlatformRole(newRole);
        return target;
    }
}
