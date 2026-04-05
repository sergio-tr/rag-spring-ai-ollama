package com.uniovi.rag.interfaces.rest.dto.me;

import java.util.Map;

public record MePreferencesResponse(int schemaVersion, Map<String, Object> preferences) {}
