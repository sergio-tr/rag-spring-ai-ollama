package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCorpusEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvaluationCorpusRepository extends JpaRepository<EvaluationCorpusEntity, UUID> {

    @Query(
            """
            SELECT c FROM EvaluationCorpusEntity c
            JOIN FETCH c.indexProject
            WHERE c.id = :corpusId AND c.owner.id = :userId
            """)
    Optional<EvaluationCorpusEntity> findByIdAndOwner_Id(@Param("corpusId") UUID corpusId, @Param("userId") UUID userId);

    @Query("SELECT c.name FROM EvaluationCorpusEntity c WHERE c.id = :corpusId")
    Optional<String> findNameById(@Param("corpusId") UUID corpusId);

    boolean existsByIndexProject_Id(UUID indexProjectId);
}
