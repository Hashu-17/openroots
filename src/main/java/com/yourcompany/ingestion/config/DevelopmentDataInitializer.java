package com.yourcompany.ingestion.config;

import com.yourcompany.ingestion.project.model.ProjectEntity;
import com.yourcompany.ingestion.project.model.ProjectMemberEntity;
import com.yourcompany.ingestion.project.repository.ProjectMemberRepository;
import com.yourcompany.ingestion.project.repository.ProjectRepository;
import com.yourcompany.ingestion.user.model.UserEntity;
import com.yourcompany.ingestion.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevelopmentDataInitializer implements ApplicationRunner {

    private static final String SEED_EMAIL = "dev@openroots.local";
    private static final String SEED_PROJECT = "OpenRoots Development";
    private static final String SEED_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    public DevelopmentDataInitializer(
            UserRepository userRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        UserEntity user = userRepository.findByEmail(SEED_EMAIL)
                .orElseGet(() -> userRepository.save(new UserEntity(SEED_EMAIL, SEED_PASSWORD_HASH)));
        ProjectEntity project = projectRepository.findByName(SEED_PROJECT)
                .orElseGet(() -> projectRepository.save(new ProjectEntity(SEED_PROJECT)));

        if (!projectMemberRepository.existsByUser_IdAndProject_Id(user.getId(), project.getId())) {
            projectMemberRepository.save(new ProjectMemberEntity(user, project, "owner"));
        }
    }
}