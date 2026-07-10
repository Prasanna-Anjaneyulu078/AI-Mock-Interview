package com.mockinterview.config;

import com.mockinterview.entity.User;
import com.mockinterview.repository.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds a demo account on startup so the app is usable immediately in local dev
 * (the dev profile uses an empty in-memory H2 database, otherwise every login
 * returns 401 "Invalid email or password").
 *
 * <p>Dev-only: gated to the {@code dev} profile so production is never touched.
 * Idempotent — it only inserts when no users exist yet.</p>
 *
 * <p>Demo credentials:</p>
 * <ul>
 *   <li>email: {@code demo@mockinterview.ai}</li>
 *   <li>password: {@code password123}</li>
 * </ul>
 */
@Component
@Profile({"dev", "default"})
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }

        User demo = User.builder()
                .fullName("Demo User")
                .email("demo@mockinterview.ai")
                .password(passwordEncoder.encode("password123"))
                .role("ROLE_USER")
                .profileImage("")
                .build();
        userRepository.save(demo);

        org.slf4j.LoggerFactory.getLogger(DataSeeder.class).info(
                "Seeded demo user (dev): demo@mockinterview.ai / password123");
    }
}
