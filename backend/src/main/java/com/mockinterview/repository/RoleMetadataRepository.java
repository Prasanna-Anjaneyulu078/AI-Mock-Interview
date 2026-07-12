package com.mockinterview.repository;

import com.mockinterview.entity.RoleMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleMetadataRepository extends JpaRepository<RoleMetadata, Long> {
    Optional<RoleMetadata> findByRoleName(String roleName);
}
