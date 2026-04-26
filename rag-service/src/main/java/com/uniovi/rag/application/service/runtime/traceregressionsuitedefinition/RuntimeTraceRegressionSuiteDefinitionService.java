package com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition;

import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.CreateDefinitionCommand;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionSnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionUserSummary;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionValidation;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.UpdateDefinitionCommand;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteRequest;
import com.uniovi.rag.infrastructure.persistence.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionEntity;
import com.uniovi.rag.infrastructure.persistence.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionEntryEntity;
import com.uniovi.rag.infrastructure.persistence.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionEntryRepository;
import com.uniovi.rag.infrastructure.persistence.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionEntryTraceEntity;
import com.uniovi.rag.infrastructure.persistence.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionEntryTraceRepository;
import com.uniovi.rag.infrastructure.persistence.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionPersistenceMapper;
import com.uniovi.rag.infrastructure.persistence.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionRepository;
import com.uniovi.rag.infrastructure.persistence.traceregressionsuitedefinition.SuiteDefinitionEntryKindColumn;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * P33 — sole writer/reader for {@code runtime_trace_regression_suite_*}; materializes {@link RuntimeTraceRegressionSuiteRequest} without invoking
 * {@code RuntimeTraceRegressionSuiteService}.
 */
@Service
public class RuntimeTraceRegressionSuiteDefinitionService {

    /**
     * Matches {@code com.uniovi.rag.interfaces.rest.NotFoundException} without depending on that package (P33 ArchUnit §15).
     */
    private static final String NOT_FOUND_EXCEPTION_CLASS_NAME = "com.uniovi.rag.interfaces.rest.NotFoundException";

    private static final String DUPLICATE_NAME_MESSAGE = "A regression suite definition with this name already exists for the user";
    private static final String NOT_FOUND_MESSAGE = "suite definition not found";

    private final RuntimeTraceRegressionSuiteDefinitionRepository definitionRepository;
    private final RuntimeTraceRegressionSuiteDefinitionEntryRepository entryRepository;
    private final RuntimeTraceRegressionSuiteDefinitionEntryTraceRepository traceRepository;
    private final RuntimeTraceRegressionSuiteDefinitionPersistenceMapper mapper;

    public RuntimeTraceRegressionSuiteDefinitionService(
            RuntimeTraceRegressionSuiteDefinitionRepository definitionRepository,
            RuntimeTraceRegressionSuiteDefinitionEntryRepository entryRepository,
            RuntimeTraceRegressionSuiteDefinitionEntryTraceRepository traceRepository,
            RuntimeTraceRegressionSuiteDefinitionPersistenceMapper mapper) {
        this.definitionRepository = definitionRepository;
        this.entryRepository = entryRepository;
        this.traceRepository = traceRepository;
        this.mapper = mapper;
    }

    @Transactional
    public UUID create(UUID userId, CreateDefinitionCommand command) {
        RuntimeTraceRegressionSuiteDefinitionValidation.validateUserId(userId);
        RuntimeTraceRegressionSuiteDefinitionValidation.validateEntryList(command.entries());
        String name = RuntimeTraceRegressionSuiteDefinitionValidation.normalizeAndValidateName(command.name());
        Optional<String> description = RuntimeTraceRegressionSuiteDefinitionValidation.normalizeDescription(command.description());

        Instant now = Instant.now();
        UUID definitionId = UUID.randomUUID();
        RuntimeTraceRegressionSuiteDefinitionEntity def =
                mapper.newDefinitionForInsert(definitionId, userId, name, description.orElse(null), now);
        try {
            // Entity has an assigned UUID, so Spring Data JPA may use merge(); keep the managed instance.
            def = definitionRepository.saveAndFlush(def);
        } catch (DataIntegrityViolationException ex) {
            throw duplicateName(ex);
        }
        mapper.insertEntriesFromCommand(def, command, entryRepository, traceRepository);
        return definitionId;
    }

    @Transactional
    public void update(UUID definitionId, UUID userId, UpdateDefinitionCommand command) {
        RuntimeTraceRegressionSuiteDefinitionValidation.validateUserId(userId);
        RuntimeTraceRegressionSuiteDefinitionValidation.validateEntryList(command.entries());
        String name = RuntimeTraceRegressionSuiteDefinitionValidation.normalizeAndValidateName(command.name());
        Optional<String> description = RuntimeTraceRegressionSuiteDefinitionValidation.normalizeDescription(command.description());

        RuntimeTraceRegressionSuiteDefinitionEntity def =
                definitionRepository.findByIdAndUserId(definitionId, userId).orElseThrow(RuntimeTraceRegressionSuiteDefinitionService::notFound);

        entryRepository.deleteByDefinition_Id(definitionId);
        entryRepository.flush();

        Instant now = Instant.now();
        mapper.applyDefinitionUpdate(def, name, description.orElse(null), now);
        try {
            // Entity has an assigned UUID; keep the managed instance (avoid merge returning a different instance).
            def = definitionRepository.saveAndFlush(def);
        } catch (DataIntegrityViolationException ex) {
            throw duplicateName(ex);
        }
        mapper.insertEntriesFromCommand(def, command, entryRepository, traceRepository);
    }

    @Transactional
    public void delete(UUID definitionId, UUID userId) {
        RuntimeTraceRegressionSuiteDefinitionValidation.validateUserId(userId);
        RuntimeTraceRegressionSuiteDefinitionEntity def =
                definitionRepository.findByIdAndUserId(definitionId, userId).orElseThrow(RuntimeTraceRegressionSuiteDefinitionService::notFound);
        definitionRepository.delete(def);
        // Ensure DELETE executes before callers query via JdbcTemplate in the same transaction.
        definitionRepository.flush();
    }

    @Transactional(readOnly = true)
    public List<RuntimeTraceRegressionSuiteDefinitionUserSummary> listSummariesForUser(UUID userId) {
        RuntimeTraceRegressionSuiteDefinitionValidation.validateUserId(userId);
        List<RuntimeTraceRegressionSuiteDefinitionEntity> rows =
                definitionRepository.findAllByUserIdOrderByUpdatedAtDescNameAscIdAsc(userId);
        List<RuntimeTraceRegressionSuiteDefinitionUserSummary> out = new ArrayList<>(rows.size());
        for (RuntimeTraceRegressionSuiteDefinitionEntity def : rows) {
            int entryCount = (int) entryRepository.countByDefinition_Id(def.getId());
            out.add(
                    new RuntimeTraceRegressionSuiteDefinitionUserSummary(
                            def.getId(), def.getName(), def.getDescription(), entryCount, def.getCreatedAt(), def.getUpdatedAt()));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Optional<RuntimeTraceRegressionSuiteDefinitionSnapshot> loadByIdForUser(UUID definitionId, UUID userId) {
        RuntimeTraceRegressionSuiteDefinitionValidation.validateUserId(userId);
        return definitionRepository.findByIdAndUserId(definitionId, userId).map(this::buildSnapshot);
    }

    @Transactional(readOnly = true)
    public RuntimeTraceRegressionSuiteRequest materializeToSuiteRequest(UUID definitionId, UUID userId) {
        RuntimeTraceRegressionSuiteDefinitionValidation.validateUserId(userId);
        RuntimeTraceRegressionSuiteDefinitionEntity def =
                definitionRepository.findByIdAndUserId(definitionId, userId).orElseThrow(RuntimeTraceRegressionSuiteDefinitionService::notFound);
        List<RuntimeTraceRegressionSuiteDefinitionEntryEntity> entries =
                entryRepository.findByDefinition_IdOrderByPositionAsc(definitionId);
        Map<UUID, List<UUID>> traceIdsByEntryId = traceIdsForEntries(entries);
        return mapper.toSuiteRequest(def.getUserId(), entries, traceIdsByEntryId);
    }

    private RuntimeTraceRegressionSuiteDefinitionSnapshot buildSnapshot(RuntimeTraceRegressionSuiteDefinitionEntity def) {
        List<RuntimeTraceRegressionSuiteDefinitionEntryEntity> entries =
                entryRepository.findByDefinition_IdOrderByPositionAsc(def.getId());
        Map<UUID, List<UUID>> traceIdsByEntryId = traceIdsForEntries(entries);
        return mapper.toSnapshot(def, entries, traceIdsByEntryId);
    }

    private Map<UUID, List<UUID>> traceIdsForEntries(List<RuntimeTraceRegressionSuiteDefinitionEntryEntity> entries) {
        Map<UUID, List<UUID>> traceIdsByEntryId = new HashMap<>();
        for (RuntimeTraceRegressionSuiteDefinitionEntryEntity e : entries) {
            if (SuiteDefinitionEntryKindColumn.BY_TRACE_IDS.equals(e.getEntryKind())) {
                traceIdsByEntryId.put(e.getId(), loadTraceIdsOrdered(e.getId()));
            }
        }
        return traceIdsByEntryId;
    }

    private List<UUID> loadTraceIdsOrdered(UUID entryId) {
        return traceRepository.findByEntry_IdOrderByPositionAsc(entryId).stream()
                .map(RuntimeTraceRegressionSuiteDefinitionEntryTraceEntity::getTraceId)
                .toList();
    }

    private static IllegalStateException duplicateName(DataIntegrityViolationException cause) {
        return new IllegalStateException(DUPLICATE_NAME_MESSAGE, cause);
    }

    private static RuntimeException notFound() {
        try {
            Class<?> c = Class.forName(NOT_FOUND_EXCEPTION_CLASS_NAME);
            Constructor<?> ctor = c.getConstructor(String.class);
            return (RuntimeException) ctor.newInstance(NOT_FOUND_MESSAGE);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to construct NotFoundException", e);
        }
    }
}
