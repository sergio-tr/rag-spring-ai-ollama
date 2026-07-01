package com.uniovi.rag.domain.llm.catalog;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** Infers role capabilities from catalog capability and model naming conventions. */
public final class LlmModelRoleResolver {

    private LlmModelRoleResolver() {}

    public static Set<LlmModelRoleCapability> rolesFor(String modelName, LlmModelCapability catalogCapability) {
        if (catalogCapability == LlmModelCapability.EMBEDDING) {
            return EnumSet.of(LlmModelRoleCapability.EMBEDDING);
        }
        if (modelName == null || modelName.isBlank()) {
            return EnumSet.of(LlmModelRoleCapability.CHAT_PRIMARY, LlmModelRoleCapability.CHAT_SECONDARY);
        }
        String lower = modelName.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("ocr")) {
            return EnumSet.of(LlmModelRoleCapability.OCR);
        }
        if (lower.contains("rerank")) {
            return EnumSet.of(LlmModelRoleCapability.RERANKER);
        }
        EnumSet<LlmModelRoleCapability> roles =
                EnumSet.of(LlmModelRoleCapability.CHAT_PRIMARY, LlmModelRoleCapability.CHAT_SECONDARY);
        if (lower.contains("-vl") || lower.contains("vision")) {
            roles.add(LlmModelRoleCapability.VISION);
        }
        return roles;
    }

    public static boolean supportsPrimaryChat(String modelName, LlmModelCapability catalogCapability) {
        return rolesFor(modelName, catalogCapability).contains(LlmModelRoleCapability.CHAT_PRIMARY);
    }

    public static boolean supportsSecondaryChat(String modelName, LlmModelCapability catalogCapability) {
        Set<LlmModelRoleCapability> roles = rolesFor(modelName, catalogCapability);
        return roles.contains(LlmModelRoleCapability.CHAT_SECONDARY)
                || roles.contains(LlmModelRoleCapability.CHAT_PRIMARY);
    }

    public static String primaryRoleLabel(String modelName, LlmModelCapability catalogCapability) {
        Set<LlmModelRoleCapability> roles = rolesFor(modelName, catalogCapability);
        if (roles.contains(LlmModelRoleCapability.OCR)) {
            return LlmModelRoleCapability.OCR.name();
        }
        if (roles.contains(LlmModelRoleCapability.RERANKER)) {
            return LlmModelRoleCapability.RERANKER.name();
        }
        if (roles.contains(LlmModelRoleCapability.VISION) && !roles.contains(LlmModelRoleCapability.CHAT_PRIMARY)) {
            return LlmModelRoleCapability.VISION.name();
        }
        return roles.isEmpty() ? "UNKNOWN" : roles.iterator().next().name();
    }
}
