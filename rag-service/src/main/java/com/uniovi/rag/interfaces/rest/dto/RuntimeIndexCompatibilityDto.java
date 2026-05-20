package com.uniovi.rag.interfaces.rest.dto;

import java.util.Map;
import java.util.UUID;

public record RuntimeIndexCompatibilityDto(
        UUID activeProjectSnapshotId,
        UUID activeConversationSnapshotId,
        String activeIndexProfileHash,
        Map<String, Object> activeIndexProfile,
        boolean hasActiveIndex,
        RuntimeSnapshotCapabilitiesDto activeSnapshotCapabilities,
        RuntimePresetIndexRequirementsDto presetIndexRequirements,
        boolean compatibleWithPreset,
        String compatibilityStatus
) {}

