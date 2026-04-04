package com.uniovi.rag.interfaces.rest.dto;

import jakarta.validation.constraints.Size;

public record PatchProjectRequest(
        @Size(max = 255) String name,
        @Size(max = 4000) String description) {
}
