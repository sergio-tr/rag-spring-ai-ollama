package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.DefaultSystemConfigurationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DefaultSystemConfigurationRepository extends JpaRepository<DefaultSystemConfigurationEntity, UUID> {

    Optional<DefaultSystemConfigurationEntity> findFirstByOrderByUpdatedAtDesc();
}
