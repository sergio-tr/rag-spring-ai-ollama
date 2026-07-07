import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { ChatConfigurationPanelContent } from "./ChatConfigurationPanelContent";
import { useChatToolbarStore } from "@/features/chat/store/chat-toolbar.store";
import { mockChatRuntimeState } from "@/test-utils/chat-runtime-state-mock";
import { mockChatToolbarApi } from "@/test-utils/chat-toolbar-api-mock";

const hooksMock = vi.hoisted(() => ({
  useRuntimeConfigCapabilities: vi.fn(),
  useProjectIndexProfile: vi.fn(),
  useActiveProjectSnapshot: vi.fn(),
  useClassifierModelsQuery: vi.fn(),
  useMeEffectiveEmbeddingDefaults: vi.fn(),
  useProjectStoredRagConfigQuery: vi.fn(),
}));

vi.mock("@/features/chat/hooks/use-runtime-config-capabilities", () => ({
  useRuntimeConfigCapabilities: (...args: unknown[]) => hooksMock.useRuntimeConfigCapabilities(...args),
}));
vi.mock("@/features/projects/hooks/use-project-index-profile", () => ({
  useProjectIndexProfile: (...args: unknown[]) => hooksMock.useProjectIndexProfile(...args),
}));
vi.mock("@/features/projects/hooks/use-active-project-snapshot", () => ({
  useActiveProjectSnapshot: (...args: unknown[]) => hooksMock.useActiveProjectSnapshot(...args),
}));
vi.mock("@/features/lab/hooks/use-classifier-registry", () => ({
  useClassifierModelsQuery: (...args: unknown[]) => hooksMock.useClassifierModelsQuery(...args),
}));
vi.mock("@/features/settings/hooks/use-me-effective-embedding-defaults", () => ({
  useMeEffectiveEmbeddingDefaults: (...args: unknown[]) => hooksMock.useMeEffectiveEmbeddingDefaults(...args),
}));
vi.mock("@/features/settings/hooks/use-rag-config", () => ({
  useProjectStoredRagConfigQuery: (...args: unknown[]) => hooksMock.useProjectStoredRagConfigQuery(...args),
}));

function renderSubject() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <IntlTestProvider locale="en">
        <ChatConfigurationPanelContent />
      </IntlTestProvider>
    </QueryClientProvider>,
  );
}

function openRetrievalSettings() {
  fireEvent.click(screen.getByTestId("chat-config-edit-button"));
}

function baseApi(saveRuntimeOverride = vi.fn()) {
  return mockChatToolbarApi({
    saveRuntimeOverride,
    runtimeState: mockChatRuntimeState({
      selectedPresetId: null,
      effectivePresetId: "preset",
      preset: {
        kind: "MISSING",
        code: null,
        label: "Assistant configuration",
        chatSelectable: false,
        supported: false,
        supportStatus: null,
        reasonIfUnsupported: null,
      },
      baseEffectiveConfig: { useRetrieval: true, topK: 5, similarityThreshold: 0.9 },
      effectiveConfig: { useRetrieval: true, topK: 5, similarityThreshold: 0.9 },
      effectiveRetrievalParameters: {
        topK: 5,
        similarityThreshold: 0.9,
        topKSource: "PRESET_LOCKED",
        similarityThresholdSource: "PRESET_LOCKED",
      },
    }),
  });
}

describe("ChatConfigurationPanelContent retrieval source modes", () => {
  beforeEach(() => {
    hooksMock.useProjectIndexProfile.mockReturnValue({ data: null });
    hooksMock.useActiveProjectSnapshot.mockReturnValue({ data: null });
    hooksMock.useClassifierModelsQuery.mockReturnValue({ data: [], isError: false });
    hooksMock.useRuntimeConfigCapabilities.mockReturnValue({ data: { capabilities: [] } });
    hooksMock.useMeEffectiveEmbeddingDefaults.mockReturnValue({
      data: { retrievalOptions: { topK: 8, similarityThreshold: 0.25, materializationStrategy: "CHUNK_LEVEL" } },
    });
    hooksMock.useProjectStoredRagConfigQuery.mockReturnValue({
      data: { topK: 10, similarityThreshold: 0.35 },
    });
    useChatToolbarStore.setState({ api: baseApi() });
  });

  it("can switch preset → project → preset", () => {
    const saveRuntimeOverride = vi.fn();
    useChatToolbarStore.setState({ api: baseApi(saveRuntimeOverride) });

    renderSubject();
    openRetrievalSettings();

    fireEvent.click(screen.getByTestId("chat-retrieval-mode-project-settings"));
    expect(saveRuntimeOverride).toHaveBeenCalledWith({ retrievalOverrideMode: "project_settings" });

    fireEvent.click(screen.getByTestId("chat-retrieval-mode-preset"));
    expect(saveRuntimeOverride).toHaveBeenLastCalledWith({ retrievalOverrideMode: "preset" });
  });

  it("can switch preset → assistant → preset", () => {
    const saveRuntimeOverride = vi.fn();
    useChatToolbarStore.setState({ api: baseApi(saveRuntimeOverride) });

    renderSubject();
    openRetrievalSettings();

    fireEvent.click(screen.getByTestId("chat-retrieval-mode-assistant-defaults"));
    expect(saveRuntimeOverride).toHaveBeenCalledWith({ retrievalOverrideMode: "assistant_defaults" });

    fireEvent.click(screen.getByTestId("chat-retrieval-mode-preset"));
    expect(saveRuntimeOverride).toHaveBeenLastCalledWith({ retrievalOverrideMode: "preset" });
  });

  it("can switch preset → custom → preset", () => {
    const saveRuntimeOverride = vi.fn();
    useChatToolbarStore.setState({ api: baseApi(saveRuntimeOverride) });

    renderSubject();
    openRetrievalSettings();

    fireEvent.click(screen.getByTestId("chat-retrieval-mode-custom"));
    expect(saveRuntimeOverride).toHaveBeenCalledWith({
      retrievalOverrideMode: "custom",
      topK: 5,
      similarityThreshold: 0.9,
    });

    fireEvent.click(screen.getByTestId("chat-retrieval-mode-preset"));
    expect(saveRuntimeOverride).toHaveBeenLastCalledWith({ retrievalOverrideMode: "preset" });
  });

  it("custom values persist while custom mode active", () => {
    const saveRuntimeOverride = vi.fn();
    useChatToolbarStore.setState({
      api: {
        ...baseApi(saveRuntimeOverride),
        runtimeState: {
          ...baseApi().runtimeState!,
          runtimeOverride: {
            retrievalOverrideMode: "custom",
            topK: 12,
            similarityThreshold: 0.4,
          },
          effectiveConfig: { useRetrieval: true, topK: 12, similarityThreshold: 0.4 },
          effectiveRetrievalParameters: {
            topK: 12,
            similarityThreshold: 0.4,
            topKSource: "CONVERSATION_CUSTOM",
            similarityThresholdSource: "CONVERSATION_CUSTOM",
          },
        },
      },
    });

    renderSubject();
    openRetrievalSettings();

    expect(screen.getByTestId("chat-retrieval-mode-custom")).toBeChecked();
    expect(screen.getByTestId("chat-runtime-toggle-topK")).toHaveValue("12");
    expect(screen.getByTestId("chat-runtime-toggle-similarityThreshold")).toHaveValue("0.4");
  });

  it("preset mode does not send custom topK/similarityThreshold", () => {
    const saveRuntimeOverride = vi.fn();
    useChatToolbarStore.setState({
      api: {
        ...baseApi(saveRuntimeOverride),
        runtimeState: {
          ...baseApi().runtimeState!,
          runtimeOverride: {
            retrievalOverrideMode: "custom",
            topK: 12,
            similarityThreshold: 0.4,
          },
        },
      },
    });

    renderSubject();
    openRetrievalSettings();
    fireEvent.click(screen.getByTestId("chat-retrieval-mode-preset"));

    expect(saveRuntimeOverride).toHaveBeenCalledWith({ retrievalOverrideMode: "preset" });
    expect(saveRuntimeOverride).not.toHaveBeenCalledWith(expect.objectContaining({ topK: expect.anything() }));
  });

  it("project mode resolves project values in PATCH payload", () => {
    const saveRuntimeOverride = vi.fn();
    useChatToolbarStore.setState({ api: baseApi(saveRuntimeOverride) });

    renderSubject();
    openRetrievalSettings();
    fireEvent.click(screen.getByTestId("chat-retrieval-mode-project-settings"));

    expect(saveRuntimeOverride).toHaveBeenCalledWith({ retrievalOverrideMode: "project_settings" });
    expect(saveRuntimeOverride).not.toHaveBeenCalledWith(expect.objectContaining({ topK: expect.anything() }));
  });

  it("assistant mode resolves assistant values in PATCH payload", () => {
    const saveRuntimeOverride = vi.fn();
    useChatToolbarStore.setState({ api: baseApi(saveRuntimeOverride) });

    renderSubject();
    openRetrievalSettings();
    fireEvent.click(screen.getByTestId("chat-retrieval-mode-assistant-defaults"));

    expect(saveRuntimeOverride).toHaveBeenCalledWith({ retrievalOverrideMode: "assistant_defaults" });
    expect(saveRuntimeOverride).not.toHaveBeenCalledWith(expect.objectContaining({ topK: expect.anything() }));
  });

  it("no regression in conversation PATCH payload for boolean toggles", () => {
    const saveRuntimeOverride = vi.fn();
    useChatToolbarStore.setState({ api: baseApi(saveRuntimeOverride) });

    renderSubject();
    openRetrievalSettings();
    saveRuntimeOverride.mockClear();

    const rankerToggle = screen.queryByTestId("chat-runtime-toggle-rankerEnabled");
    if (rankerToggle) {
      fireEvent.click(rankerToggle);
      expect(saveRuntimeOverride).toHaveBeenCalledWith(expect.objectContaining({ rankerEnabled: expect.any(Boolean) }));
      expect(saveRuntimeOverride).not.toHaveBeenCalledWith(expect.objectContaining({ retrievalOverrideMode: "preset" }));
    }
  });

  it("disables numeric fields unless custom mode is active", () => {
    renderSubject();
    openRetrievalSettings();

    expect(screen.getByTestId("chat-runtime-toggle-topK")).toBeDisabled();
    expect(screen.getByTestId("chat-runtime-toggle-similarityThreshold")).toBeDisabled();

    fireEvent.click(screen.getByTestId("chat-retrieval-mode-custom"));
    expect(screen.getByTestId("chat-runtime-toggle-topK")).not.toBeDisabled();
    expect(screen.getByTestId("chat-runtime-toggle-similarityThreshold")).not.toBeDisabled();
  });
});
