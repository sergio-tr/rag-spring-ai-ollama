import { describe, expect, it } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useForm } from "react-hook-form";
import { useState } from "react";
import { IntlTestProvider } from "@/test-utils/intl";
import { AssistantInstructionsEditor } from "./AssistantInstructionsEditor";
import {
  buildAssistantInstructionsPreview,
  FORBIDDEN_INSTRUCTION_EDITOR_KEYS,
} from "@/features/settings/lib/assistant-instructions-preview";
import type { ConfigFormValues } from "@/features/settings/lib/build-config-zod";

function EditorHarness(
  props: Readonly<{
    mode: "user" | "project";
    defaultSystem?: string;
    initialPersona?: string;
    initialProject?: string;
  }>,
) {
  const form = useForm<ConfigFormValues>({
    defaultValues: { llmSystemPrompt: props.defaultSystem },
  });
  const [persona, setPersona] = useState(props.initialPersona ?? "");
  const [project, setProject] = useState(props.initialProject ?? "");

  return (
    <AssistantInstructionsEditor
      mode={props.mode}
      form={form}
      instructionFields={[{ key: "llmSystemPrompt", type: "text", userEditable: true, max: 50_000 }]}
      fieldLabel={(key) => key}
      globalPersonaPrompt={persona}
      projectPrompt={project}
      onGlobalPersonaPromptChange={setPersona}
      onProjectPromptChange={setProject}
    />
  );
}

describe("assistant-instructions-preview", () => {
  it("builds product-facing layer summary without internal keys", () => {
    const layers = buildAssistantInstructionsPreview({
      mode: "user",
      systemInstructions: "Be concise.",
      answerInstructions: "Friendly tone.",
    });
    expect(layers.map((l) => l.id)).toEqual(["system", "answer", "sourceUsage", "grounding", "abstention"]);
    expect(layers[0]?.status).toBe("set");
    expect(layers[2]?.status).toBe("not_applicable");
    const serialized = JSON.stringify(layers);
    for (const forbidden of FORBIDDEN_INSTRUCTION_EDITOR_KEYS) {
      expect(serialized).not.toContain(forbidden);
    }
  });
});

describe("AssistantInstructionsEditor", () => {
  it("renders system and answer instruction fields in user mode", () => {
    render(
      <IntlTestProvider locale="en">
        <EditorHarness mode="user" defaultSystem="You help with records." initialPersona="Be clear." />
      </IntlTestProvider>,
    );
    expect(screen.getByTestId("assistant-instructions-editor")).toBeInTheDocument();
    expect(screen.getByTestId("assistant-system-instructions-field")).toBeInTheDocument();
    expect(screen.getByTestId("config-field-llmSystemPrompt")).toBeInTheDocument();
    expect(screen.getByTestId("assistant-answer-instructions-field")).toBeInTheDocument();
    expect(screen.getByText("System instructions", { selector: "h4" })).toBeInTheDocument();
    expect(screen.queryByTestId("assistant-source-usage-instructions-field")).not.toBeInTheDocument();
  });

  it("renders source usage instructions in project mode", () => {
    render(
      <IntlTestProvider locale="en">
        <EditorHarness mode="project" initialProject="Cite page numbers." />
      </IntlTestProvider>,
    );
    expect(screen.getByTestId("assistant-source-usage-instructions-field")).toBeInTheDocument();
    expect(screen.queryByTestId("assistant-answer-instructions-field")).not.toBeInTheDocument();
  });

  it("preview configuration shows product-facing summary", async () => {
    const user = userEvent.setup();
    render(
      <IntlTestProvider locale="en">
        <EditorHarness mode="user" defaultSystem="System layer text." initialPersona="Answer style." />
      </IntlTestProvider>,
    );
    const preview = screen.getByTestId("assistant-instructions-preview");
    await user.click(within(preview).getByText(/Preview configuration/i));
    expect(screen.getByTestId("assistant-preview-layer-system")).toHaveTextContent(/System layer text/);
    expect(screen.getByTestId("assistant-preview-layer-answer")).toHaveTextContent(/Answer style/);
    expect(screen.getByTestId("assistant-preview-layer-abstention")).toHaveTextContent(/platform safety defaults/i);
    expect(preview.textContent ?? "").not.toMatch(/promptBundle|PromptBundleFingerprint|judgePrompt/i);
  });

  it("reset to default clears system instructions field", async () => {
    const user = userEvent.setup();
    render(
      <IntlTestProvider locale="en">
        <EditorHarness mode="user" defaultSystem="Remove me." />
      </IntlTestProvider>,
    );
    const textarea = screen.getByTestId("config-field-llmSystemPrompt");
    expect(textarea).toHaveValue("Remove me.");
    await user.click(screen.getByTestId("assistant-reset-system-instructions"));
    expect(textarea).toHaveValue("");
  });

  it("does not render unsupported internal prompt editors", () => {
    render(
      <IntlTestProvider locale="en">
        <EditorHarness mode="user" />
      </IntlTestProvider>,
    );
    for (const key of FORBIDDEN_INSTRUCTION_EDITOR_KEYS) {
      expect(screen.queryByTestId(`config-field-${key}`)).not.toBeInTheDocument();
    }
    expect(screen.queryByLabelText(/judge prompt/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/query rewriting/i)).not.toBeInTheDocument();
  });
});
