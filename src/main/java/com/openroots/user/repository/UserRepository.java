package com.openroots.user.repository;

import com.openroots.user.model.PlatformRole;
import com.openroots.user.model.UserEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmail(String email);

    boolean existsByPlatformRole(PlatformRole platformRole);

    long countByPlatformRole(PlatformRole platformRole);

    List<UserEntity> findAllByOrderByEmailAsc();
}
