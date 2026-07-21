package com.knox.galaxy.repository;

import com.knox.galaxy.model.PlatformUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlatformUserRepository extends JpaRepository<PlatformUser, Long> {
    Optional<PlatformUser> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}
