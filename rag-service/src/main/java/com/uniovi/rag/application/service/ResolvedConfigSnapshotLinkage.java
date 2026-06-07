package com.uniovi.rag.application.service;

import java.util.UUID;

/** Id + config hash returned to application callers without exposing JPA entities. */
public record ResolvedConfigSnapshotLinkage(UUID id, String configHash) {}
