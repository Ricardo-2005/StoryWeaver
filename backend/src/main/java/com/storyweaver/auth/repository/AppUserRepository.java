package com.storyweaver.auth.repository;

import com.storyweaver.auth.domain.AppUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    boolean existsByNormalizedUsername(String normalizedUsername);

    boolean existsByNormalizedEmail(String normalizedEmail);

    Optional<AppUser> findByNormalizedUsernameOrNormalizedEmail(String normalizedUsername, String normalizedEmail);
}
