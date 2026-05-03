package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmailIgnoreCase(String email);
}
