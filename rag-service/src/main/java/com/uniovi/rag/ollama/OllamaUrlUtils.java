package com.uniovi.rag.ollama;

/**
 * Normaliza la URL base de Ollama (sin barra final).
 */
public final class OllamaUrlUtils {

    private OllamaUrlUtils() {
    }

    public static String stripTrailingSlash(String url) {
        if (url == null || url.isEmpty()) {
            return "http://localhost:11434";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
