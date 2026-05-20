package com.uniovi.rag.interfaces.rest.mapper;

import com.uniovi.rag.application.result.runtime.RuntimeConfigCapability;
import com.uniovi.rag.application.result.runtime.RuntimeConfigCapabilities;
import com.uniovi.rag.application.result.runtime.RuntimeConfigValidationIssue;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigCapabilitiesResponse;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigCapabilityDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidationIssueDto;
import java.util.List;

/** Maps application runtime-config results to REST DTOs (controller boundary only). */
public final class RuntimeConfigRestMapper {

    private RuntimeConfigRestMapper() {}

    public static RuntimeConfigCapabilitiesResponse toCapabilitiesResponse(RuntimeConfigCapabilities caps) {
        if (caps == null) {
            return new RuntimeConfigCapabilitiesResponse(List.of());
        }
        return new RuntimeConfigCapabilitiesResponse(
                caps.capabilities().stream().map(RuntimeConfigRestMapper::toCapabilityDto).toList());
    }

    public static RuntimeConfigCapabilityDto toCapabilityDto(RuntimeConfigCapability c) {
        return new RuntimeConfigCapabilityDto(
                c.key(),
                c.label(),
                c.description(),
                c.category(),
                c.visibleInChat(),
                c.configurableInChat(),
                c.implemented(),
                c.engineWired(),
                c.supportMode(),
                c.displayOrder(),
                c.requires(),
                c.excludes(),
                c.requiresIndexSnapshot(),
                c.requiresReindexWhenChanged(),
                c.reasonIfDisabled(),
                c.reasonIfNotImplemented());
    }

    public static RuntimeConfigValidationIssueDto toValidationIssueDto(RuntimeConfigValidationIssue issue) {
        return new RuntimeConfigValidationIssueDto(
                issue.code(), issue.field(), issue.message(), issue.severity());
    }

    public static RuntimeConfigValidationIssue fromValidationIssueDto(RuntimeConfigValidationIssueDto dto) {
        if (dto == null) {
            return new RuntimeConfigValidationIssue(null, null, null, null);
        }
        return new RuntimeConfigValidationIssue(dto.code(), dto.field(), dto.message(), dto.severity());
    }

    public static List<RuntimeConfigValidationIssue> fromValidationIssueDtos(
            List<RuntimeConfigValidationIssueDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return List.of();
        }
        return dtos.stream().map(RuntimeConfigRestMapper::fromValidationIssueDto).toList();
    }
}
