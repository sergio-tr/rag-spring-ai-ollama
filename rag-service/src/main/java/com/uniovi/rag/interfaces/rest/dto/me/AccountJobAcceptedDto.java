package com.uniovi.rag.interfaces.rest.dto.me;

import java.util.UUID;

/** Accepted async account job (export/deletion); poll path is under {@code /me/account/jobs}, not Lab. */
public record AccountJobAcceptedDto(UUID jobId, String status, String pollPath) {}
