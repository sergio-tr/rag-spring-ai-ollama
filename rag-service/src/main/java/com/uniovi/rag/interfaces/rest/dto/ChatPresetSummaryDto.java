package com.uniovi.rag.interfaces.rest.dto;

/**
 * Backend-authoritative preset summary for Chat runtime state.
 *
 * <p>This is used by the Chat UI to render preset identity and support status without local inference.
 */
public record ChatPresetSummaryDto(
        String kind, // PRODUCT | EXPERIMENTAL | DEFAULT | MISSING
        String code, // optional, e.g. P4
        String label,
        boolean chatSelectable,
        boolean supported,
        String supportStatus,
        String reasonIfUnsupported
) {}

