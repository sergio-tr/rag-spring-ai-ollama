package com.uniovi.rag.application.config;

import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemPromptComposerTest {

    private final SystemPromptComposer composer = new SystemPromptComposer();

    @Test
    void composeJoinsNonBlankLayersInOrderWithDoubleNewlineSeparator() {
        SystemPromptLayers layers =
                new SystemPromptLayers("base", "account", "project", "preset");
        assertEquals("base\n\naccount\n\nproject\n\npreset", composer.compose(layers));
    }

    @Test
    void composeTrimsLayersAndOmitsBlankLayers() {
        SystemPromptLayers layers = new SystemPromptLayers("  a  ", "", "  \t  ", "b");
        assertEquals("a\n\nb", composer.compose(layers));
    }

    @Test
    void composeReturnsEmptyForNullLayers() {
        assertEquals("", composer.compose(null));
    }

    @Test
    void composeReturnsEmptyWhenAllLayersBlank() {
        assertEquals("", composer.compose(new SystemPromptLayers(" ", "", null, "\t")));
    }
}
