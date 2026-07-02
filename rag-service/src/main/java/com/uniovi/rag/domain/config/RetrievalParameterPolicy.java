package com.uniovi.rag.domain.config;

/**
 * Provenance for effective chat retrieval parameters ({@code topK}, {@code similarityThreshold}).
 *
 * <ul>
 *   <li>{@link #USER_DEFAULTS} — account, project, or system retrieval defaults</li>
 *   <li>{@link #PRESET_LOCKED} — preset recommended retrieval parameters</li>
 *   <li>{@link #CONVERSATION_CUSTOM} — conversation runtime override</li>
 * </ul>
 */
public enum RetrievalParameterPolicy {
    USER_DEFAULTS,
    PRESET_LOCKED,
    CONVERSATION_CUSTOM
}
