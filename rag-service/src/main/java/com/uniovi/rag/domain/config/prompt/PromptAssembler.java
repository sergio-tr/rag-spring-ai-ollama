package com.uniovi.rag.domain.config.prompt;

/**
 * Joins {@link PromptStack} fragments in strict role order for LLM input and debug views.
 */
public final class PromptAssembler {

    private PromptAssembler() {
    }

    public static String assemble(PromptStack stack) {
        if (stack == null || stack.fragments() == null || stack.fragments().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (PromptFragment f : stack.fragments()) {
            if (f.text() == null || f.text().isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(f.text().trim());
        }
        return sb.toString();
    }
}
