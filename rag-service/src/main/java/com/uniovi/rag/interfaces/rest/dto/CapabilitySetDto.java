package com.uniovi.rag.interfaces.rest.dto;

import com.uniovi.rag.domain.config.capability.Capability;
import com.uniovi.rag.domain.config.capability.CapabilitySet;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record CapabilitySetDto(
        List<String> activeCapabilities,
        String embeddingModelId,
        String indexMode,
        String chunkingPolicy) {

    public static CapabilitySetDto fromDomain(CapabilitySet c) {
        List<String> names =
                c.activeCapabilities().stream().map(Enum::name).collect(Collectors.toList());
        return new CapabilitySetDto(names, c.embeddingModelId(), c.indexMode(), c.chunkingPolicy());
    }

    public CapabilitySet toCapabilitySet() {
        EnumSet<Capability> caps = EnumSet.noneOf(Capability.class);
        if (activeCapabilities != null) {
            for (String n : activeCapabilities) {
                caps.add(Capability.valueOf(n));
            }
        }
        return new CapabilitySet(Set.copyOf(caps), embeddingModelId, indexMode, chunkingPolicy);
    }
}
