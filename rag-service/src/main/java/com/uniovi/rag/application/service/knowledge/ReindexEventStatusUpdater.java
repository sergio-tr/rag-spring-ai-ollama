package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.domain.knowledge.ReindexEventStatus;
import com.uniovi.rag.infrastructure.persistence.ReindexEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Updates {@code reindex_event} status in its own transactional boundary so callers (e.g.
 * {@link ReindexService}) do not rely on {@code this}-proxied {@code @Transactional} methods.
 */
@Service
public class ReindexEventStatusUpdater {

    private final ReindexEventRepository reindexEventRepository;

    public ReindexEventStatusUpdater(ReindexEventRepository reindexEventRepository) {
        this.reindexEventRepository = reindexEventRepository;
    }

    @Transactional
    public void update(UUID eventId, ReindexEventStatus status) {
        reindexEventRepository
                .findById(eventId)
                .ifPresent(
                        ev -> {
                            ev.setStatus(status);
                            ev.setUpdatedAt(Instant.now());
                            reindexEventRepository.save(ev);
                        });
    }
}
