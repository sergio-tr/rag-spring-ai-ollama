package com.uniovi.rag.interfaces.rest.dto.me;

import java.util.List;

public record MeDocumentsPageResponse(List<UserDocumentRowDto> items, long total) {}
