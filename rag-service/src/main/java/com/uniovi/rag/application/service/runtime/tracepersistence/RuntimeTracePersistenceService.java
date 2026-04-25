package com.uniovi.rag.application.service.runtime.tracepersistence;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.infrastructure.observability.Loggable;
import com.uniovi.rag.infrastructure.persistence.jpa.RuntimeExecutionTraceEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RuntimeExecutionTraceRepository;
import com.uniovi.rag.infrastructure.persistence.mapper.RuntimeExecutionTraceEntityMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class RuntimeTracePersistenceService implements Loggable {

    private final RuntimeExecutionTraceRepository repository;
    private final RuntimeExecutionTraceEntityMapper mapper;

    public RuntimeTracePersistenceService(
            RuntimeExecutionTraceRepository repository,
            RuntimeExecutionTraceEntityMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<UUID> persistBestEffort(ExecutionContext ctx, ExecutionTrace trace) {
        try {
            RuntimeExecutionTraceEntity e =
                    mapper.toNewEntity(
                            ctx.userId(),
                            ctx.projectId(),
                            Optional.ofNullable(ctx.conversationId()),
                            ctx.originatingUserMessageId(),
                            ctx.correlationId(),
                            trace);
            RuntimeExecutionTraceEntity saved = repository.save(e);
            return Optional.ofNullable(saved.getId());
        } catch (Exception e) {
            log()
                    .warn(
                            "Runtime trace persistence failed (best-effort). correlationId={} err={}",
                            ctx != null ? ctx.correlationId() : "",
                            e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}

