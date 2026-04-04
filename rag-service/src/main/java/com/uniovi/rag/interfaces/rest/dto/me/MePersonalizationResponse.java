package com.uniovi.rag.interfaces.rest.dto.me;

import java.util.Map;

public record MePersonalizationResponse(int schemaVersion, Map<String, Object> personalization) {}
