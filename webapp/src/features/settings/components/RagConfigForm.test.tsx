import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";
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
  useUserRagConfigQuery: () => mockUserState,
  useProjectRagConfigQuery: () => mockProjectState,
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

vi.mock("@/features/settings/components/UserAccountPreferencesSection", () => ({
  UserAccountPreferencesSection: () => <div data-testid="user-account-preferences" />,
}));

vi.mock("@/features/settings/components/InternalPromptConfigurationSection", () => ({
  InternalPromptConfigurationSection: () => <div data-testid="internal-prompt-config" />,
}));

vi.mock("@/features/settings/components/TaskLlmSettingsSection", () => ({
  TaskLlmSettingsSection: () => <div data-testid="task-llm-settings" />,
  TASK_LLM_OVERRIDES_KEY: "taskLlmOverrides",
}));

import { RagConfigForm } from "./RagConfigForm";

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
    render(
      <IntlTestProvider>
        <RagConfigForm mode="project" projectId={undefined} />
      </IntlTestProvider>,
    );
    expect(screen.getByRole("status")).toBeInTheDocument();
  });

  it("submits user config form when schema has editable fields", async () => {
    mockSchemaState.data = {
      fields: [
        { key: "topK", type: "integer", userEditable: true, min: 1, max: 50 },
        { key: "enableFoo", type: "boolean", userEditable: true },
        { key: "llmModel", type: "string", userEditable: true },
        { key: "embeddingModel", type: "string", userEditable: false },
      ],
    };
    mockUserState.data = { topK: 5, enableFoo: false, llmModel: "qwen:latest" };
    putUser.mockResolvedValueOnce({});

    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <RagConfigForm mode="user" />
      </IntlTestProvider>,
    );

    const topK = await screen.findByLabelText(/passages to retrieve/i);
    await waitFor(() => expect(topK).toHaveValue(5));
    await user.clear(topK);
    await user.type(topK, "7");
    await user.click(screen.getByLabelText("enableFoo"));
    await user.click(screen.getByRole("button", { name: /save/i }));

    await waitFor(() => expect(putUser).toHaveBeenCalled());
  });

  it("shows loading state", () => {
    mockSchemaState.isLoading = true;
    render(
      <IntlTestProvider>
        <RagConfigForm mode="user" />
      </IntlTestProvider>,
    );
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  it("shows load error when schema query fails", () => {
    mockSchemaState.isError = true;
    render(
      <IntlTestProvider>
        <RagConfigForm mode="user" />
      </IntlTestProvider>,
    );
    expect(screen.getByRole("alert")).toHaveTextContent(/could not load configuration/i);
  });

  it("shows empty schema message when no fields exist", () => {
    mockSchemaState.data = { fields: [] };
    render(
      <IntlTestProvider>
        <RagConfigForm mode="user" />
      </IntlTestProvider>,
    );
    expect(screen.getByText(/no settings are available/i)).toBeInTheDocument();
  });

  it("shows product-oriented project description without removed HTTP copy in the card body", async () => {
    mockSchemaState.data = { fields: [{ key: "topK", type: "integer", userEditable: true, min: 1, max: 50 }] };
    mockProjectState.data = { topK: 3 };
    render(
      <IntlTestProvider>
        <RagConfigForm mode="project" projectId="p1" />
      </IntlTestProvider>,
    );
    await waitFor(() => expect(screen.getByLabelText(/passages to retrieve/i)).toBeInTheDocument());
    expect(screen.queryByText(/Project overrides from GET/i)).not.toBeInTheDocument();
    expect(screen.getByText(/These values apply only while this project is selected/i)).toBeInTheDocument();
  });

  it("shows human technical reference copy without REST path literals", async () => {
    mockSchemaState.data = { fields: [{ key: "topK", type: "integer", userEditable: true, min: 1, max: 50 }] };
    mockProjectState.data = { topK: 3 };
    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <RagConfigForm mode="project" projectId="proj-77" />
      </IntlTestProvider>,
    );
    await waitFor(() => expect(screen.getByText(/Technical reference/i)).toBeInTheDocument());
    await user.click(screen.getByText(/Technical reference \(API\)/i));
    expect(screen.getByText(/obtained from the project configuration/i)).toBeInTheDocument();
    expect(screen.queryByText(/\/config\/project\//)).not.toBeInTheDocument();
    expect(screen.queryByText(/GET \/config/i)).not.toBeInTheDocument();
  });

  it("confirms before clearing project overrides and preserves DELETE semantics", async () => {
    mockSchemaState.data = {
      fields: [
        { key: "llmModel", type: "string", userEditable: true },
        { key: "llmTemperature", type: "number", userEditable: true, min: 0, max: 2 },
      ],
    };
    mockProjectState.data = { llmTemperature: 0.4 };
    delProject.mockResolvedValueOnce({});
    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <RagConfigForm mode="project" projectId="p1" />
      </IntlTestProvider>,
    );

    await user.click(screen.getByRole("button", { name: /remove project overrides/i }));
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /^remove overrides$/i }));
    await waitFor(() => {
      expect(delProject).toHaveBeenCalled();
    });
  });

  it("merges unknown keys into project PUT payload like before", async () => {
    mockSchemaState.data = {
      fields: [{ key: "topK", type: "integer", userEditable: true, min: 1, max: 50 }],
    };
    mockProjectState.data = { topK: 4, futureClientFlag: true };
    putProject.mockResolvedValueOnce({});
    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <RagConfigForm mode="project" projectId="p9" />
      </IntlTestProvider>,
    );

    const input = await screen.findByLabelText(/passages to retrieve/i);
    await waitFor(() => expect(input).toHaveValue(4));
    await user.clear(input);
    await user.type(input, "6");
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    await waitFor(() => expect(putProject).toHaveBeenCalled());
    expect(putProject).toHaveBeenCalledWith(
      expect.objectContaining({ topK: 6, futureClientFlag: true }),
    );
  });

  it("shows provider-aware model parameters and hides unsupported params by default", async () => {
    mockSchemaState.data = {
      fields: [
        { key: "llmModel", type: "string", userEditable: true },
        { key: "llmTemperature", type: "number", userEditable: true, min: 0, max: 2 },
      ],
    };
    render(
      <IntlTestProvider>
        <RagConfigForm mode="user" />
      </IntlTestProvider>,
    );
    expect(await screen.findByTestId("config-effective-provider")).toHaveTextContent(/Configured model provider/i);
    expect(screen.getByTestId("provider-aware-model-parameters")).toBeInTheDocument();
    expect(screen.getByTestId("model-param-field-temperature")).toBeInTheDocument();
    const unsupported = screen.queryByTestId("provider-unsupported-model-parameters");
    if (unsupported) {
      expect(unsupported).not.toBeVisible();
    }
    expect(screen.getByTestId("effective-model-parameters-preview")).toBeInTheDocument();
  });

  it("uses the exact Advanced technical details label on the collapsed wrapper", () => {
    mockSchemaState.data = {
      fields: [{ key: "topK", type: "integer", userEditable: true, min: 1, max: 50 }],
    };
    render(
      <IntlTestProvider>
        <RagConfigForm mode="user" />
      </IntlTestProvider>,
    );
    const advancedWrapper = screen.getByTestId("settings-model-parameters-advanced");
    expect(advancedWrapper).toHaveTextContent(ADVANCED_TECHNICAL_DETAILS_TITLE);
    expect(advancedWrapper).not.toHaveAttribute("open");
  });

  it("renders structured form before collapsed advanced JSON in user mode", () => {
    mockSchemaState.data = {
      fields: [{ key: "topK", type: "integer", userEditable: true, min: 1, max: 50 }],
    };
    render(
      <IntlTestProvider>
        <RagConfigForm mode="user" />
      </IntlTestProvider>,
    );
    const structured = screen.getByTestId("rag-config-structured-form");
    const advancedWrapper = screen.getByTestId("settings-model-parameters-advanced");
    expect(structured.compareDocumentPosition(advancedWrapper) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(advancedWrapper).not.toHaveAttribute("open");
    expect(screen.queryByRole("textbox", { name: /advanced configuration/i })).not.toBeVisible();
  });

  it("renders structured form before collapsed advanced JSON in project mode", async () => {
    mockSchemaState.data = {
      fields: [{ key: "topK", type: "integer", userEditable: true, min: 1, max: 50 }],
    };
    mockProjectState.data = { topK: 3 };
    render(
      <IntlTestProvider>
        <RagConfigForm mode="project" projectId="p1" />
      </IntlTestProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("rag-config-structured-form")).toBeInTheDocument());
    const advancedWrapper = screen.getByTestId("settings-model-parameters-advanced");
    expect(advancedWrapper).not.toHaveAttribute("open");
  });

  it("shows warning when selected chat model is unavailable in catalog", async () => {
    mockSchemaState.data = {
      fields: [
        { key: "llmModel", type: "string", userEditable: true },
        { key: "topK", type: "integer", userEditable: true, min: 1, max: 50 },
      ],
    };
    mockUserState.data = { llmModel: "ghost-model", topK: 5 };
    render(
      <IntlTestProvider>
        <RagConfigForm mode="user" />
      </IntlTestProvider>,
    );
    expect(await screen.findByTestId("rag-config-model-warnings")).toHaveTextContent(/ghost-model/i);
    expect(screen.getByTestId("rag-config-model-warnings")).toHaveTextContent(/no longer available/i);
  });

  it("renders assistant profile and behavior sections when llmSystemPrompt is in schema", async () => {
    mockSchemaState.data = {
      fields: [
        { key: "llmSystemPrompt", type: "text", userEditable: true, max: 50_000 },
        { key: "llmModel", type: "string", userEditable: true },
        { key: "embeddingModel", type: "string", userEditable: true },
        { key: "topK", type: "integer", userEditable: true, min: 1, max: 50 },
      ],
    };
    render(
      <IntlTestProvider>
        <RagConfigForm mode="user" />
      </IntlTestProvider>,
    );
    expect(await screen.findByTestId("assistant-profile-section")).toBeInTheDocument();
    expect(await screen.findByTestId("assistant-instructions-editor")).toBeInTheDocument();
    expect(screen.getByTestId("assistant-system-instructions-field")).toBeInTheDocument();
    expect(screen.getByTestId("assistant-answer-instructions-field")).toBeInTheDocument();
    expect(screen.getByTestId("assistant-instructions-preview")).toBeInTheDocument();
    expect(await screen.findByTestId("settings-model-configuration-section")).toBeInTheDocument();
    expect(screen.getByTestId("settings-embedding-model-section")).toBeInTheDocument();
    expect(screen.getByTestId("settings-retrieval-settings-section")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /Assistant instructions/i })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /Model & retrieval settings/i })).toBeInTheDocument();
    expect(screen.getByTestId("assistant-behavior-section")).toBeInTheDocument();
    expect(screen.getByTestId("config-field-llmSystemPrompt")).toBeInTheDocument();
    expect(screen.getByTestId("assistant-answer-instructions-field")).toBeInTheDocument();
  });

  it("shows project prompt field in project mode", async () => {
    mockSchemaState.data = {
      fields: [{ key: "topK", type: "integer", userEditable: true, min: 1, max: 50 }],
    };
    render(
      <IntlTestProvider>
        <RagConfigForm mode="project" projectId="p1" />
      </IntlTestProvider>,
    );
    expect(await screen.findByTestId("assistant-source-usage-instructions-field")).toBeInTheDocument();
  });

  it("saves supported system and answer instructions on submit", async () => {
    const user = userEvent.setup();
    mockSchemaState.data = {
      fields: [{ key: "llmSystemPrompt", type: "text", userEditable: true, max: 50_000 }],
    };
    mockUserState.data = {};
    render(
      <IntlTestProvider>
        <RagConfigForm mode="user" />
      </IntlTestProvider>,
    );
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

  it("shows save error when mutate hook is in error state", () => {
    mutateState.putUserError = true;
    mockSchemaState.data = { fields: [{ key: "topK", type: "integer", userEditable: true }] };
    render(
      <IntlTestProvider>
        <RagConfigForm mode="user" />
      </IntlTestProvider>,
    );
    expect(screen.getByRole("alert")).toHaveTextContent(/could not save/i);
  });
});

