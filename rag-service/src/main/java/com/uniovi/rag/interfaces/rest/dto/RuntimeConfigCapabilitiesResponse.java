package com.uniovi.rag.interfaces.rest.dto;

import java.util.List;

public record RuntimeConfigCapabilitiesResponse(
        List<RuntimeConfigCapabilityDto> capabilities
) {}

