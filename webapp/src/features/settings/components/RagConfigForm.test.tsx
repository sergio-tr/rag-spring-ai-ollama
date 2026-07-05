import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";
import { ADVANCED_TECHNICAL_DETAILS_TITLE } from "@/lib/product-provider-labels";

const putUser = vi.fn();
const putProject = vi.fn();
const delProject = vi.fn();
const mutateState = {
  putUserPending: false,
  putUserError: false,
  putProjectPending: false,
  putProjectError: false,
  delProjectPending: false,
  delProjectError: false,
};

type ConfigField = {
  key: string;
  type: "integer" | "number" | "boolean" | "string" | "text";
  userEditable: boolean;
  min?: number;
  max?: number;
};

type QueryState<T> = {
  isLoading: boolean;
  isError: boolean;
  data: T;
};

const mockSchemaState: QueryState<{ fields: ConfigField[] }> = {
  isLoading: false,
  isError: false,
  data: { fields: [] },
};
const mockUserState: QueryState<Record<string, unknown>> = { isLoading: false, isError: false, data: {} };
const mockProjectState: QueryState<Record<string, unknown>> = { isLoading: false, isError: false, data: {} };

const patchProject = vi.fn();

vi.mock("@/features/projects/hooks/use-projects", () => ({
  useProject: () => ({
    data: { id: "p1", projectPrompt: "Project context" },
    isLoading: false,
    isError: false,
  }),
  usePatchProject: () => ({
    mutateAsync: patchProject,
    isPending: false,
    isError: false,
  }),
}));

vi.mock("@/lib/api-client", () => ({
  apiFetch: vi.fn().mockResolvedValue({
    personalization: { globalPersonaPrompt: "", theme: "system" },
    schemaVersion: 1,
  }),
  apiProductPath: (path: string) => path,
}));

vi.mock("@/features/settings/hooks/use-rag-config", () => ({
  useConfigSchemaQuery: () => mockSchemaState,
  useUserStoredRagConfigQuery: () => mockUserState,
  useProjectStoredRagConfigQuery: () => mockProjectState,
  usePutUserRagConfig: () => ({
    mutateAsync: putUser,
    isPending: mutateState.putUserPending,
    isError: mutateState.putUserError,
  }),
  usePutProjectRagConfig: () => ({
    mutateAsync: putProject,
    isPending: mutateState.putProjectPending,
    isError: mutateState.putProjectError,
  }),
  useDeleteProjectRagConfig: () => ({
    mutateAsync: delProject,
    isPending: mutateState.delProjectPending,
    isError: mutateState.delProjectError,
  }),
}));

vi.mock("@/features/chat/hooks/use-me-selectable-llm-models", () => ({
  useMeSelectableLlmModels: (capability: string) => ({
    data: {
      effectiveProvider: "OPENAI_COMPATIBLE",
      capability,
      models:
        capability === "EMBEDDING"
          ? [{ modelName: "nomic-embed-text", displayName: "nomic-embed-text", selectable: true }]
          : [
              { modelName: "qwen:latest", displayName: "Qwen", selectable: true },
              { modelName: "missing-model", displayName: "Missing", selectable: false },
            ],
    },
    isLoading: false,
    isError: false,
  }),
}));

vi.mock("@/features/settings/components/InternalPromptConfigurationSection", () => ({
  InternalPromptConfigurationSection: () => <div data-testid="internal-prompt-config" />,
}));

vi.mock("@/features/settings/components/TaskLlmSettingsSection", () => ({
  TaskLlmSettingsSection: () => <div data-testid="task-llm-settings" />,
  TASK_LLM_OVERRIDES_KEY: "taskLlmOverrides",
}));

vi.mock("@/features/settings/components/AdvancedTaskModelSettingsForm", () => ({
  AdvancedTaskModelSettingsForm: () => <div data-testid="advanced-task-model-settings" />,
  TASK_LLM_OVERRIDES_KEY: "taskLlmOverrides",
}));

vi.mock("@/features/settings/hooks/use-me-effective-embedding-defaults", () => ({
  useMeEffectiveEmbeddingDefaults: () => ({
    data: {
      effectiveProvider: "OPENAI_COMPATIBLE",
      embeddingModel: "nomic-embed-text",
      embeddingOptions: { encodingFormat: "float", dimensions: 768, timeoutSeconds: 30 },
      retrievalOptions: { topK: 10, similarityThreshold: 0.35, materializationStrategy: "CHUNK_LEVEL" },
      indexingOptions: { maxInputChars: 2048, batchSize: 16, normalize: false },
    },
    isLoading: false,
    isError: false,
  }),
}));

vi.mock("@/features/settings/hooks/use-me-effective-llm-defaults", () => ({
  useMeEffectiveLlmDefaults: () => ({
    data: {
      effectiveProvider: "OPENAI_COMPATIBLE",
      chatModel: "qwen:latest",
      classifierModelId: "default",
      temperature: 0.1,
      additionalParameters: { think: false, topP: 1 },
    },
    isLoading: false,
    isError: false,
  }),
}));

vi.mock("@/features/lab/hooks/use-lab-evaluation-models", () => ({
  useLabEvaluationModels: () => ({
    data: {
      models: [
        {
          modelName: "nomic-embed-text",
          evalSelectable: true,
          supportsEncodingFormat: true,
          supportedEncodingFormats: ["float", "base64"],
          supportsDimensions: true,
          supportsNormalize: true,
        },
      ],
    },
    isLoading: false,
    isError: false,
  }),
}));

vi.mock("@/features/lab/hooks/use-classifier-registry", () => ({
  useClassifierModelsQuery: () => ({
    data: [{ id: "c1", name: "Default classifier", inferenceTag: "default", status: "READY", active: true }],
    isLoading: false,
    isError: false,
  }),
}));

vi.mock("@/features/projects/hooks/use-project-index-profile", () => ({
  useProjectIndexProfile: () => ({
    data: {
      projectId: "p1",
      embeddingModelId: "nomic-embed-text",
      materializationStrategy: "CHUNK_LEVEL",
      metadataEnabled: true,
      metadataProfile: null,
      chunkMaxChars: 2048,
      chunkOverlap: 128,
      profileHash: "abc",
      createdAt: "",
      updatedAt: "",
    },
    isLoading: false,
    isError: false,
  }),
}));

import { RagConfigForm } from "./RagConfigForm";

function batchSizeInput(): HTMLInputElement {
  const field = screen.getByTestId("embedding-default-embeddingBatchSize");
  const input = field.querySelector('input[type="number"]');
  if (!(input instanceof HTMLInputElement)) {
    throw new Error("Batch size number input not found");
  }
  return input;
}

async function waitForBatchSizeInput(): Promise<HTMLInputElement> {
  await screen.findByTestId("embedding-default-embeddingBatchSize");
  return batchSizeInput();
}

function renderWithProviders(ui: React.ReactElement) {
  const queryClient = createTestQueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <IntlTestProvider>{ui}</IntlTestProvider>
    </QueryClientProvider>,
  );
}

describe("RagConfigForm", () => {
  beforeEach(() => {
    putUser.mockReset();
    putProject.mockReset();
    patchProject.mockReset();
    delProject.mockReset();
    mockSchemaState.isLoading = false;
    mockSchemaState.isError = false;
    mockSchemaState.data = { fields: [] };
    mockUserState.isLoading = false;
    mockUserState.isError = false;
    mockUserState.data = {};
    mockProjectState.isLoading = false;
    mockProjectState.isError = false;
    mockProjectState.data = {};
    mutateState.putUserPending = false;
    mutateState.putUserError = false;
    mutateState.putProjectPending = false;
    mutateState.putProjectError = false;
    mutateState.delProjectPending = false;
    mutateState.delProjectError = false;
  });

  it("renders no-active-project message in project mode without projectId", () => {
    renderWithProviders(<RagConfigForm mode="project" projectId={undefined} />);
    expect(screen.getByRole("status")).toBeInTheDocument();
  });

  it("submits user config form when schema has editable fields", async () => {
    mockSchemaState.data = {
      fields: [{ key: "embeddingModel", type: "string", userEditable: true }],
    };
    mockUserState.data = { embeddingModel: "nomic-embed-text", embeddingBatchSize: 16 };
    putUser.mockResolvedValueOnce({});

    renderWithProviders(<RagConfigForm mode="user" />);

    const batchSize = await waitForBatchSizeInput();
    await waitFor(() => expect(batchSize).toHaveValue(16));
    fireEvent.submit(screen.getByTestId("rag-config-structured-form"));

    await waitFor(() => expect(putUser).toHaveBeenCalled());
    const payload = putUser.mock.calls[0]?.[0] as Record<string, unknown>;
    expect(payload).toEqual({});
  });

  it("shows loading state", () => {
    mockSchemaState.isLoading = true;
    renderWithProviders(<RagConfigForm mode="user" />);
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  it("shows load error when schema query fails", () => {
    mockSchemaState.isError = true;
    renderWithProviders(<RagConfigForm mode="user" />);
    expect(screen.getByRole("alert")).toHaveTextContent(/could not load configuration/i);
  });

  it("shows empty schema message when no fields exist", () => {
    mockSchemaState.data = { fields: [] };
    renderWithProviders(<RagConfigForm mode="user" />);
    expect(screen.getByText(/no settings are available/i)).toBeInTheDocument();
  });

  it("shows product-oriented project description without removed HTTP copy in the card body", async () => {
    mockSchemaState.data = {
      fields: [{ key: "llmSystemPrompt", type: "text", userEditable: true, max: 50_000 }],
    };
    mockProjectState.data = { llmSystemPrompt: "Project instructions" };
    renderWithProviders(<RagConfigForm mode="project" projectId="p1" />);
    await waitFor(() => expect(screen.getByTestId("settings-collapsible-index-profile")).toBeInTheDocument());
    expect(screen.queryByText(/Project overrides from GET/i)).not.toBeInTheDocument();
    expect(
      screen.getByText(/Configure the project prompt and review the fixed index profile/i),
    ).toBeInTheDocument();
    expect(screen.queryByTestId("assistant-behavior-section")).not.toBeInTheDocument();
    expect(screen.queryByTestId("settings-preview-configuration")).not.toBeInTheDocument();
    expect(screen.queryByTestId("settings-retrieval-feature-toggle")).not.toBeInTheDocument();
  });

  it("does not render language or theme controls in user Assistant Configuration", () => {
    mockSchemaState.data = {
      fields: [{ key: "embeddingModel", type: "string", userEditable: true }],
    };
    renderWithProviders(<RagConfigForm mode="user" />);
    expect(screen.queryByTestId("user-account-preferences")).not.toBeInTheDocument();
    expect(screen.queryByTestId("user-pref-locale")).not.toBeInTheDocument();
  });

  it("shows human technical reference copy without REST path literals", async () => {
    mockSchemaState.data = { fields: [{ key: "topK", type: "integer", userEditable: true, min: 1, max: 50 }] };
    mockProjectState.data = { topK: 3 };
    const user = userEvent.setup();
    renderWithProviders(<RagConfigForm mode="project" projectId="proj-77" />);
    await waitFor(() => expect(screen.getByText(/Technical reference/i)).toBeInTheDocument());
    await user.click(screen.getByText(/Technical reference \(API\)/i));
    expect(screen.getByText(/obtained from the project configuration/i)).toBeInTheDocument();
    expect(screen.queryByText(/\/config\/project\//)).not.toBeInTheDocument();
    expect(screen.queryByText(/GET \/config/i)).not.toBeInTheDocument();
  });

  it("confirms before clearing project overrides and preserves DELETE semantics", async () => {
    mockSchemaState.data = {
      fields: [{ key: "llmSystemPrompt", type: "text", userEditable: true, max: 50_000 }],
    };
    mockProjectState.data = { llmSystemPrompt: "Project instructions" };
    delProject.mockResolvedValueOnce({});
    const user = userEvent.setup();
    renderWithProviders(<RagConfigForm mode="project" projectId="p1" />);

    await user.click(screen.getByRole("button", { name: /remove project overrides/i }));
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /^remove overrides$/i }));
    await waitFor(() => {
      expect(delProject).toHaveBeenCalled();
    });
  });

  it("does not re-send unchanged project overrides on save", async () => {
    mockSchemaState.data = {
      fields: [{ key: "llmSystemPrompt", type: "text", userEditable: true, max: 50_000 }],
    };
    mockProjectState.data = { llmSystemPrompt: "Keep me", futureClientFlag: true };
    putProject.mockResolvedValueOnce({});
    renderWithProviders(<RagConfigForm mode="project" projectId="p9" />);

    await screen.findByTestId("config-field-llmSystemPrompt");
    fireEvent.submit(screen.getByTestId("rag-config-structured-form"));

    await waitFor(() => expect(putProject).toHaveBeenCalled());
    expect(putProject).toHaveBeenCalledWith({});
  });

  it("does not render global provider-aware model parameters in user Assistant Configuration", async () => {
    mockSchemaState.data = {
      fields: [
        { key: "embeddingModel", type: "string", userEditable: true },
        { key: "topK", type: "integer", userEditable: true, min: 1, max: 50 },
        { key: "similarityThreshold", type: "number", userEditable: true, min: 0, max: 1 },
      ],
    };
    renderWithProviders(<RagConfigForm mode="user" />);
    expect(await screen.findByTestId("settings-collapsible-task-models")).toBeInTheDocument();
    expect(screen.getByTestId("settings-collapsible-classifier")).toBeInTheDocument();
    expect(screen.getByTestId("settings-collapsible-retrieval")).toBeInTheDocument();
    expect(screen.getByTestId("settings-retrieval-defaults")).toBeInTheDocument();
    expect(screen.queryByTestId("provider-aware-model-parameters")).not.toBeInTheDocument();
    expect(screen.queryByTestId("assistant-instructions-preview")).not.toBeInTheDocument();
    expect(screen.queryByTestId("assistant-configuration-effective-summary")).not.toBeInTheDocument();
  });

  it("uses the exact Advanced technical details label on the collapsed wrapper", () => {
    mockSchemaState.data = {
      fields: [{ key: "embeddingModel", type: "string", userEditable: true }],
    };
    renderWithProviders(<RagConfigForm mode="user" />);
    const advancedWrapper = screen.getByTestId("settings-model-parameters-advanced");
    expect(advancedWrapper).toHaveTextContent(ADVANCED_TECHNICAL_DETAILS_TITLE);
    expect(advancedWrapper).not.toHaveAttribute("open");
  });

  it("renders structured form before collapsed advanced JSON in user mode", () => {
    mockSchemaState.data = {
      fields: [{ key: "embeddingModel", type: "string", userEditable: true }],
    };
    renderWithProviders(<RagConfigForm mode="user" />);
    const structured = screen.getByTestId("rag-config-structured-form");
    const advancedWrapper = screen.getByTestId("settings-model-parameters-advanced");
    expect(structured.compareDocumentPosition(advancedWrapper) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(advancedWrapper).not.toHaveAttribute("open");
    expect(screen.queryByRole("textbox", { name: /advanced configuration/i })).not.toBeVisible();
  });

  it("renders structured form before collapsed advanced JSON in project mode", async () => {
    mockSchemaState.data = {
      fields: [{ key: "llmSystemPrompt", type: "text", userEditable: true, max: 50_000 }],
    };
    mockProjectState.data = { llmSystemPrompt: "Project scope" };
    renderWithProviders(<RagConfigForm mode="project" projectId="p1" />);
    await waitFor(() => expect(screen.getByTestId("rag-config-structured-form")).toBeInTheDocument());
    const advancedWrapper = screen.getByTestId("settings-model-parameters-advanced");
    expect(advancedWrapper).not.toHaveAttribute("open");
  });

  it("shows warning when selected embedding model is unavailable in catalog", async () => {
    mockSchemaState.data = {
      fields: [{ key: "embeddingModel", type: "string", userEditable: true }],
    };
    mockUserState.data = { embeddingModel: "ghost-model" };
    renderWithProviders(<RagConfigForm mode="user" />);
    expect(await screen.findByTestId("rag-config-model-warnings")).toHaveTextContent(/ghost-model/i);
    expect(screen.getByTestId("rag-config-model-warnings")).toHaveTextContent(/no longer available/i);
  });

  it("renders prompt configuration before task models in user mode", async () => {
    mockSchemaState.data = {
      fields: [
        { key: "llmSystemPrompt", type: "text", userEditable: true, max: 50_000 },
        { key: "embeddingModel", type: "string", userEditable: true },
        { key: "topK", type: "integer", userEditable: true, min: 1, max: 50 },
      ],
    };
    renderWithProviders(<RagConfigForm mode="user" />);
    const promptSection = await screen.findByTestId("settings-collapsible-prompt");
    const taskSection = screen.getByTestId("settings-collapsible-task-models");
    expect(promptSection.compareDocumentPosition(taskSection) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(screen.queryByTestId("settings-preview-configuration")).not.toBeInTheDocument();
  });

  it("renders assistant configuration sections as collapsible wrappers", async () => {
    mockSchemaState.data = {
      fields: [
        { key: "llmSystemPrompt", type: "text", userEditable: true, max: 50_000 },
        { key: "embeddingModel", type: "string", userEditable: true },
        { key: "topK", type: "integer", userEditable: true, min: 1, max: 50 },
        { key: "similarityThreshold", type: "number", userEditable: true, min: 0, max: 1 },
      ],
    };
    renderWithProviders(<RagConfigForm mode="user" />);
    expect(await screen.findByTestId("assistant-instructions-editor")).toBeInTheDocument();
    expect(screen.getByTestId("settings-collapsible-prompt")).toBeInTheDocument();
    expect(screen.getByTestId("settings-collapsible-task-models")).toBeInTheDocument();
    expect(screen.getByTestId("settings-collapsible-embedding")).toBeInTheDocument();
    expect(screen.getByTestId("settings-collapsible-retrieval")).toBeInTheDocument();
    expect(screen.getByTestId("settings-collapsible-classifier")).toBeInTheDocument();
    expect(screen.getByTestId("settings-retrieval-defaults")).toBeInTheDocument();
    expect(screen.getByTestId("embedding-defaults-settings")).toBeInTheDocument();
    expect(screen.queryByTestId("assistant-instructions-preview")).not.toBeInTheDocument();
    expect(screen.queryByTestId("assistant-configuration-effective-summary")).not.toBeInTheDocument();
    expect(screen.queryByTestId("assistant-behavior-section")).not.toBeInTheDocument();
  });

  it("renders project configuration with retrieval settings only (no feature toggles)", async () => {
    mockSchemaState.data = {
      fields: [
        { key: "llmSystemPrompt", type: "text", userEditable: true, max: 50_000 },
        { key: "topK", type: "integer", userEditable: true, min: 1, max: 50 },
        { key: "similarityThreshold", type: "number", userEditable: true, min: 0, max: 1 },
        { key: "expansionEnabled", type: "boolean", userEditable: true },
        { key: "toolsEnabled", type: "boolean", userEditable: true },
        { key: "metadataEnabled", type: "boolean", userEditable: true },
      ],
    };
    renderWithProviders(<RagConfigForm mode="project" projectId="p1" />);
    expect(await screen.findByTestId("settings-collapsible-retrieval")).toBeInTheDocument();
    expect(screen.getByTestId("settings-retrieval-defaults")).toBeInTheDocument();
    expect(screen.queryByTestId("config-field-expansionEnabled")).not.toBeInTheDocument();
    expect(screen.queryByTestId("config-field-toolsEnabled")).not.toBeInTheDocument();
    expect(screen.queryByTestId("config-field-metadataEnabled")).not.toBeInTheDocument();
    expect(screen.queryByTestId("assistant-instructions-preview")).not.toBeInTheDocument();
  });

  it("shows project prompt field in project mode", async () => {
    mockSchemaState.data = {
      fields: [{ key: "llmSystemPrompt", type: "text", userEditable: true, max: 50_000 }],
    };
    renderWithProviders(<RagConfigForm mode="project" projectId="p1" />);
    expect(await screen.findByTestId("assistant-source-usage-instructions-field")).toBeInTheDocument();
    expect(screen.getByTestId("project-index-profile-section")).toBeInTheDocument();
    expect(screen.getByTestId("settings-collapsible-index-profile")).toBeInTheDocument();
  });

  it("persists user retrieval settings on save", async () => {
    mockSchemaState.data = {
      fields: [
        { key: "topK", type: "integer", userEditable: true, min: 1, max: 50 },
        { key: "similarityThreshold", type: "number", userEditable: true, min: 0, max: 1 },
      ],
    };
    mockUserState.data = {};
    putUser.mockResolvedValueOnce({});
    renderWithProviders(<RagConfigForm mode="user" />);
    await screen.findByTestId("config-field-topK");
    fireEvent.change(screen.getByTestId("config-field-topK"), { target: { value: "12" } });
    fireEvent.change(screen.getByTestId("config-field-similarityThreshold"), { target: { value: "0.15" } });
    fireEvent.submit(screen.getByTestId("rag-config-structured-form"));
    await waitFor(() => expect(putUser).toHaveBeenCalled());
    expect(putUser).toHaveBeenCalledWith(expect.objectContaining({ topK: 12, similarityThreshold: 0.15 }));
  });

  it("saves supported system and answer instructions on submit", async () => {
    const user = userEvent.setup();
    mockSchemaState.data = {
      fields: [{ key: "llmSystemPrompt", type: "text", userEditable: true, max: 50_000 }],
    };
    mockUserState.data = {};
    renderWithProviders(<RagConfigForm mode="user" />);
    await screen.findByTestId("config-field-llmSystemPrompt");
    await user.clear(screen.getByTestId("config-field-llmSystemPrompt"));
    await user.type(screen.getByTestId("config-field-llmSystemPrompt"), "Be helpful with records.");
    await user.type(screen.getByTestId("assistant-global-persona-input"), "Use plain language.");
    await user.click(screen.getByRole("button", { name: /save/i }));
    await waitFor(() => {
      expect(putUser).toHaveBeenCalledWith(
        expect.objectContaining({ llmSystemPrompt: "Be helpful with records." }),
      );
    });
  });

  it("shows separate reset LLM and embedding defaults buttons in user mode", async () => {
    mockSchemaState.data = {
      fields: [
        { key: "llmModel", type: "string", userEditable: true },
        { key: "embeddingModel", type: "string", userEditable: true },
        { key: "topK", type: "integer", userEditable: true, min: 1, max: 50 },
      ],
    };
    renderWithProviders(<RagConfigForm mode="user" />);
    expect(await screen.findByTestId("reset-llm-defaults-button")).toBeInTheDocument();
    expect(screen.getByTestId("reset-embedding-defaults-button")).toBeInTheDocument();
  });

  it("reset LLM defaults clears only LLM override keys on confirm", async () => {
    mockSchemaState.data = {
      fields: [{ key: "embeddingModel", type: "string", userEditable: true }],
    };
    mockUserState.data = { llmModel: "custom-model", llmTemperature: 0.8, embeddingBatchSize: 7 };
    putUser.mockResolvedValueOnce({});
    const user = userEvent.setup();
    renderWithProviders(<RagConfigForm mode="user" />);
    await user.click(await screen.findByTestId("reset-llm-defaults-button"));
    await user.click(screen.getByRole("button", { name: /^Reset LLM defaults$/i }));
    await waitFor(() => expect(putUser).toHaveBeenCalled());
    const payload = putUser.mock.calls.at(-1)?.[0] as Record<string, unknown>;
    expect(payload.llmModel).toBeNull();
    expect(payload.embeddingBatchSize).toBeUndefined();
  });

  it("shows save error when mutate hook is in error state", () => {
    mutateState.putUserError = true;
    mockSchemaState.data = { fields: [{ key: "embeddingModel", type: "string", userEditable: true }] };
    renderWithProviders(<RagConfigForm mode="user" />);
    expect(screen.getByRole("alert")).toHaveTextContent(/could not save/i);
  });

  it("applies overflow-safe layout classes on the form root and action row", () => {
    mockSchemaState.data = {
      fields: [{ key: "embeddingModel", type: "string", userEditable: true }],
    };
    renderWithProviders(<RagConfigForm mode="user" />);

    const formCard = screen.getByTestId("user-rag-config-form");
    expect(formCard.className).toMatch(/min-w-0/);
    expect(formCard.className).toMatch(/max-w-full/);
    expect(formCard.className).toMatch(/overflow-hidden/);

    const structuredForm = screen.getByTestId("rag-config-structured-form");
    expect(structuredForm.className).toMatch(/min-w-0/);

    const saveButton = screen.getByRole("button", { name: /save/i });
    expect(saveButton.className).toMatch(/whitespace-normal|inline-flex/);
  });
});

