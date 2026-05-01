package com.uniovi.rag.domain.config.prompt;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromptAssemblerTest {

    @Test
    void assemble_nullOrEmpty_returnsEmptyString() {
        assertEquals("", PromptAssembler.assemble(null));
        assertEquals("", PromptAssembler.assemble(new PromptStack(null)));
        assertEquals("", PromptAssembler.assemble(PromptStack.empty()));
    }

    @Test
    void assemble_skipsBlankFragments_andJoinsWithDoubleNewline() {
        PromptStack stack =
                new PromptStack(
                        List.of(
                                new PromptFragment(PromptFragmentRole.SYSTEM_BASE, "a", "  "),
                                new PromptFragment(PromptFragmentRole.USER_TASK, "b", "first"),
                                new PromptFragment(PromptFragmentRole.PROJECT, "c", null),
                                new PromptFragment(PromptFragmentRole.PRESET_TECHNICAL, "d", "  second line  ")));
        assertEquals("first\n\nsecond line", PromptAssembler.assemble(stack));
    }
}
