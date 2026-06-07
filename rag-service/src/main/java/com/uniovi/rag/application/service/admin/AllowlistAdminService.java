package com.uniovi.rag.application.service.admin;

import com.uniovi.rag.application.service.admin.model.AllowedModelReferenceGuard;
import com.uniovi.rag.domain.AllowedModelType;
import com.uniovi.rag.infrastructure.persistence.AllowedModelRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AllowedModelEntity;
import com.uniovi.rag.interfaces.rest.admin.dto.AdminAllowlistEntryDto;
import com.uniovi.rag.interfaces.rest.admin.dto.CreateAllowlistEntryRequest;
import com.uniovi.rag.interfaces.rest.admin.dto.UpdateAllowlistEntryRequest;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AllowlistAdminService {

    private final AllowedModelRepository allowedModelRepository;
    private final AllowedModelReferenceGuard referenceGuard;

    public AllowlistAdminService(
            AllowedModelRepository allowedModelRepository, AllowedModelReferenceGuard referenceGuard) {
        this.allowedModelRepository = allowedModelRepository;
        this.referenceGuard = referenceGuard;
    }

    @Transactional(readOnly = true)
    public List<AdminAllowlistEntryDto> list() {
        return allowedModelRepository.findAll().stream().map(AllowlistAdminService::toDto).sorted(
                Comparator.comparing(AdminAllowlistEntryDto::name)
                        .thenComparing(e -> e.type().name()))
                .toList();
    }

    @Transactional
    public AdminAllowlistEntryDto create(CreateAllowlistEntryRequest req) {
        allowedModelRepository
                .findByNameAndType(req.name().trim(), req.type())
                .ifPresent(e -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT, "Allowlist entry already exists for name and type");
                });
        AllowedModelEntity e = AllowedModelEntity.newRow(
                req.name().trim(), req.type(), req.inAllowlist(), null);
        return toDto(allowedModelRepository.save(e));
    }

    @Transactional
    public AdminAllowlistEntryDto update(UUID id, UpdateAllowlistEntryRequest req) {
        AllowedModelEntity e = allowedModelRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Allowlist entry not found"));
        if (req.name() != null && !req.name().isBlank()) {
            String trimmed = req.name().trim();
            AllowedModelType type = req.type() != null ? req.type() : e.getType();
            allowedModelRepository
                    .findByNameAndType(trimmed, type)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT, "Another entry already uses this name and type");
                    });
            e.setName(trimmed);
        }
        if (req.type() != null) {
            AllowedModelType newType = req.type();
            allowedModelRepository
                    .findByNameAndType(e.getName(), newType)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT, "Another entry already uses this name and type");
                    });
            e.setType(newType);
        }
        if (req.inAllowlist() != null) {
            e.setInAllowlist(req.inAllowlist());
        }
        return toDto(allowedModelRepository.save(e));
    }

    @Transactional
    public void delete(UUID id) {
        AllowedModelEntity e = allowedModelRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Allowlist entry not found"));
        if (referenceGuard.isReferenced(e.getName())) {
            e.setInAllowlist(false);
            allowedModelRepository.save(e);
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Model is referenced by historical runs; entry was disabled instead of deleted");
        }
        allowedModelRepository.deleteById(id);
    }

    private static AdminAllowlistEntryDto toDto(AllowedModelEntity e) {
        return new AdminAllowlistEntryDto(
                e.getId(), e.getName(), e.getType(), e.isInAllowlist(), e.getInstalledAt());
    }
}
