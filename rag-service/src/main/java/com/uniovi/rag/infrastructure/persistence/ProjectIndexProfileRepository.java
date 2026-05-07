package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.ProjectIndexProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProjectIndexProfileRepository extends JpaRepository<ProjectIndexProfileEntity, UUID> {}

