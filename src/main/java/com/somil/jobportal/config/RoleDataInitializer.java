package com.somil.jobportal.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.somil.jobportal.repository.UsersTypeRepository;

@Component
public class RoleDataInitializer implements ApplicationRunner {

    private final UsersTypeRepository usersTypeRepository;
    private final JdbcTemplate jdbcTemplate;

    public RoleDataInitializer(UsersTypeRepository usersTypeRepository, JdbcTemplate jdbcTemplate) {
        this.usersTypeRepository = usersTypeRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createRoleIfMissing(1, "Recruiter");
        createRoleIfMissing(2, "Job Seeker");
    }

    private void createRoleIfMissing(int id, String name) {
        if (usersTypeRepository.existsById(id)) {
            return;
        }

        jdbcTemplate.update(
                "INSERT INTO users_type (user_type_id, user_type_name) VALUES (?, ?)",
                id,
                name);
    }
}
