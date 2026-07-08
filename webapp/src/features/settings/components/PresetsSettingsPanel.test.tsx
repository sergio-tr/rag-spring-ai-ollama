import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";
import en from "@/../messages/en.json";
import es from "@/../messages/es.json";
import type { ExperimentalPresetCatalogItemDto, RagPresetDto } from "@/types/api";

const apiFetchMock = vi.fn();

vi.mock("@/lib/api-client", () => ({
  apiFetch: (...args: unknown[]) => apiFetchMock(...args),
  apiProductPath: (p: string) => {
    const seg = p.startsWith("/") ? p : `/${p}`;
    return `/api/v5${seg}`;
  },
}));

vi.mock("@/navigation", () => ({
  Link: ({ href, children, className, ...rest }: { href: string; children: React.ReactNode; className?: string }) => (
    <a href={href} className={className} {...rest}>
      {children}
    </a>
  ),
}));

import { PresetsSettingsPanel } from "./PresetsSettingsPanel";

function renderWithQuery(ui: ReactNode) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>);
}

const DEMO_WORST: RagPresetDto = {
  id: "cafe0001-0001-4001-8001-000000000001",
  name: "Demo_Worst",
  description: null,
  tags: [],
  values: { useRetrieval: false, naiveFullCorpusInPromptEnabled: false },
  system: true,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const DEMO_NAIVE: RagPresetDto = {
  id: "cafe0001-0001-4001-8001-000000000002",
  name: "Demo_NaiveFullCorpus",
  description: null,
  tags: [],
  values: { useRetrieval: false, naiveFullCorpusInPromptEnabled: true },
  system: true,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const DEMO_BEST: RagPresetDto = {
  id: "cafe0001-0001-4001-8001-000000000003",
  name: "Demo_Best",
  description: null,
  tags: [],
  values: {
    useRetrieval: true,
    materializationStrategy: "HYBRID",
    metadataEnabled: true,
    expansionEnabled: true,
    nerEnabled: true,
    deterministicToolRoutingEnabled: true,
    functionCallingEnabled: true,
    useAdvisor: true,
    clarificationEnabled: true,
    rankerEnabled: false,
    memoryEnabled: false,
  },
  system: true,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

function runtimeFlags(overrides: Record<string, unknown>) {
  return { runtimeFeatureFlags: overrides };
}

function buildExperimentalPresets(): ExperimentalPresetCatalogItemDto[] {
  const codes = Array.from({ length: 16 }, (_, i) => `P${i}`);
  const flagsByCode: Record<string, Record<string, unknown>> = {
    P0: {
      useRetrieval: false,
      naiveFullCorpusInPromptEnabled: false,
    },
    P8: {
      useRetrieval: true,
      materializationStrategy: "HYBRID",
      rankerEnabled: true,
      postRetrievalEnabled: true,
      metadataEnabled: true,
    },
  };

  return codes.map((code, index) => ({
    productPresetId: `cafe0001-0001-4001-8001-${String(index + 16).padStart(12, "0")}`,
    code,
    family: "S2",
    label: `${code} preset`,
    description: `${code} description`,
    indexRequirements:
      code === "P8"
        ? { requiredMaterializationStrategy: "HYBRID", requiresMetadataSupport: true }
        : code === "P2"
          ? { requiredMaterializationStrategy: "DOCUMENT_LEVEL", requiresMetadataSupport: false }
          : null,
    requiredCapabilities: [],
    supported: true,
    supportStatus: "EXECUTABLE" as const,
    reasonIfUnsupported: null,
    requiresMultiTurn: code === "P13" || code === "P14",
    mapsToRuntimeCapabilities: runtimeFlags(flagsByCode[code] ?? { useRetrieval: true }),
    allowedOutcomes: ["EXECUTED" as const],
    chatSelectable: code !== "P13" && code !== "P14",
    labSelectable: true,
    labOnly: code === "P13" || code === "P14",
    protocolStageIndex: index,
  }));
}

function mockCatalogApi() {
  apiFetchMock.mockImplementation(async (url: string | URL, init?: RequestInit) => {
    const u = String(url);
    const method = (init?.method ?? "GET").toUpperCase();
    if (method === "GET" && u.includes("/config/schema")) {
      return {
        version: 1,
        fields: [
          { key: "topK", type: "integer", userEditable: true, min: 1, max: 50 },
          { key: "llmModel", type: "string", userEditable: true },
        ],
      };
    }
    if (method === "GET" && u.includes("/chat/presets/catalog")) {
      return {
        productPresets: [DEMO_WORST, DEMO_NAIVE, DEMO_BEST],
        experimentalPresets: buildExperimentalPresets(),
      };
    }
    if (method === "POST" && u.endsWith("/presets")) {
      const body = JSON.parse(String(init?.body)) as {
        name: string;
        description: string | null;
        tags: string[];
        values: Record<string, unknown>;
      };
      return {
        id: "preset-new",
        name: body.name,
        description: body.description,
        tags: body.tags ?? [],
        values: body.values,
        system: false,
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-01T00:00:00Z",
      };
    }
    throw new Error(`unexpected ${method} ${u}`);
  });
}

describe("PresetsSettingsPanel", () => {
  let clipboardSpy: ReturnType<typeof vi.spyOn> | undefined;

  beforeEach(() => {
    apiFetchMock.mockReset();
    vi.unstubAllEnvs();
    clipboardSpy = vi.spyOn(navigator.clipboard, "writeText").mockResolvedValue(undefined as never);
    mockCatalogApi();
  });

  afterEach(() => {
    clipboardSpy?.mockRestore();
    vi.unstubAllEnvs();
  });

  it("renders read-only catalog with product and evaluation sections", async () => {
    renderWithQuery(
      <IntlTestProvider>
        <PresetsSettingsPanel />
      </IntlTestProvider>,
    );
    await waitFor(() => {
      expect(screen.getByTestId("presets-catalog-product-section")).toBeInTheDocument();
      expect(screen.getByTestId("presets-catalog-evaluation-section")).toBeInTheDocument();
    });
    expect(screen.getByTestId("preset-catalog-row-cafe0001-0001-4001-8001-000000000003")).toBeInTheDocument();
    expect(screen.getByTestId("preset-catalog-row-P0")).toBeInTheDocument();
    expect(screen.getByTestId("preset-catalog-row-P15")).toBeInTheDocument();
  });

  it("displays all 3 product and 16 experimental presets (P0–P15) from catalog API", async () => {
    renderWithQuery(
      <IntlTestProvider>
        <PresetsSettingsPanel />
      </IntlTestProvider>,
    );
    await waitFor(() => {
      expect(screen.getAllByTestId(/^preset-catalog-row-/)).toHaveLength(19);
    });
  });

  it("catalog mode hides custom preset creation by default", async () => {
    renderWithQuery(
      <IntlTestProvider>
        <PresetsSettingsPanel />
      </IntlTestProvider>,
    );
    await waitFor(() => {
      expect(screen.getByTestId("preset-catalog-row-cafe0001-0001-4001-8001-000000000003")).toBeInTheDocument();
    });
    expect(screen.queryByTestId("presets-create-card")).not.toBeInTheDocument();
    expect(screen.getByTestId("presets-creation-deferred")).toBeInTheDocument();
  });

  it("does not show feature toggles or edit/delete actions", async () => {
    renderWithQuery(
      <IntlTestProvider>
        <PresetsSettingsPanel />
      </IntlTestProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("preset-catalog-row-P0")).toBeInTheDocument());
    expect(screen.queryByRole("button", { name: /delete/i })).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/passages to retrieve/i)).not.toBeInTheDocument();
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  });

  it("spot-checks feature badges for P0, P8, and Demo_Best", async () => {
    renderWithQuery(
      <IntlTestProvider>
        <PresetsSettingsPanel />
      </IntlTestProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("preset-feature-matrix-P0")).toBeInTheDocument());

    const p0 = screen.getByTestId("preset-feature-matrix-P0");
    expect(within(p0).getByText("Direct LLM")).toHaveAttribute("data-enabled", "true");
    expect(within(p0).getByText("Retrieval")).toHaveAttribute("data-enabled", "false");

    const p8 = screen.getByTestId("preset-feature-matrix-P8");
    expect(within(p8).getByText("Hybrid retrieval")).toHaveAttribute("data-enabled", "true");
    expect(within(p8).getByText("Reranking")).toHaveAttribute("data-enabled", "true");

    const best = screen.getByTestId("preset-feature-matrix-cafe0001-0001-4001-8001-000000000003");
    expect(within(best).getByText("Function calling")).toHaveAttribute("data-enabled", "true");
    expect(within(best).getByText("Direct LLM")).toHaveAttribute("data-enabled", "false");
  });

  it("shows compatibility badges when index requirements are present", async () => {
    renderWithQuery(
      <IntlTestProvider>
        <PresetsSettingsPanel />
      </IntlTestProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("preset-catalog-row-P8")).toBeInTheDocument());
    const row = screen.getByTestId("preset-catalog-row-P8");
    expect(within(row).getByText(/Requires HYBRID index/i)).toBeInTheDocument();
    expect(within(row).getByText(/Requires metadata support/i)).toBeInTheDocument();
  });

  it("explains Chat customization with required copy", async () => {
    renderWithQuery(
      <IntlTestProvider>
        <PresetsSettingsPanel />
      </IntlTestProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("presets-catalog-customization-copy")).toBeInTheDocument());
    expect(screen.getByTestId("presets-catalog-customization-copy")).toHaveTextContent(
      en.Settings.presetsCatalogCustomizationHint,
    );
    expect(es.Settings.presetsCatalogCustomizationHint).toContain("solo lectura");
  });

  it("provides per-preset Use in Chat links without DELETE/POST on default render", async () => {
    renderWithQuery(
      <IntlTestProvider>
        <PresetsSettingsPanel />
      </IntlTestProvider>,
    );
    await waitFor(() => expect(screen.getAllByRole("link", { name: /use in chat/i }).length).toBeGreaterThan(0));
    expect(screen.getAllByRole("link", { name: /use in chat/i })[0]).toHaveAttribute("href", "/chat");

    const mutatingCalls = apiFetchMock.mock.calls.filter((c) => {
      const init = c[1] as RequestInit | undefined;
      const method = (init?.method ?? "GET").toUpperCase();
      return method === "DELETE" || method === "POST";
    });
    expect(mutatingCalls).toHaveLength(0);
    expect(apiFetchMock.mock.calls.some((c) => String(c[0]).includes("/chat/presets/catalog"))).toBe(true);
    expect(apiFetchMock.mock.calls.some((c) => String(c[0]).endsWith("/presets"))).toBe(false);
  });

  it("does not show an editable values textarea outside Advanced import when creation enabled", async () => {
    vi.stubEnv("NEXT_PUBLIC_PRESET_CREATION_ENABLED", "true");
    renderWithQuery(
      <IntlTestProvider>
        <PresetsSettingsPanel />
      </IntlTestProvider>,
    );
    await waitFor(() => {
      expect(screen.getByLabelText(/passages to retrieve/i)).toBeInTheDocument();
    });
    const card = screen.getByTestId("presets-create-card");
    expect(within(card).getAllByTestId("preset-import-textarea")).toHaveLength(1);
  });

  it("renders structured configuration summary without visible raw JSON in normal mode", async () => {
    vi.stubEnv("NEXT_PUBLIC_PRESET_CREATION_ENABLED", "true");
    const user = userEvent.setup();
    renderWithQuery(
      <IntlTestProvider>
        <PresetsSettingsPanel />
      </IntlTestProvider>,
    );
    await waitFor(() => {
      expect(screen.getByTestId("preset-draft-summary")).toBeInTheDocument();
      expect(screen.getByLabelText(/passages to retrieve/i)).toBeInTheDocument();
    });
    expect(screen.queryByTestId("preset-draft-preview")).not.toBeInTheDocument();
    const summary = screen.getByTestId("preset-draft-summary");
    const card = within(summary).getByTestId("preset-profile-card-draft");
    const advancedDetails = within(card).getByText(/Advanced technical details/i).closest("details");
    expect(advancedDetails).not.toHaveAttribute("open");

    const topK = screen.getByLabelText(/passages to retrieve/i);
    await user.clear(topK);
    await user.type(topK, "5");

    await user.click(within(card).getByText(/Advanced technical details/i));
    expect(advancedDetails).toHaveAttribute("open");
    expect(within(card).getByText(/"topK": 5/)).toBeInTheDocument();
  });

  it("rejects import JSON with unsupported keys", async () => {
    vi.stubEnv("NEXT_PUBLIC_PRESET_CREATION_ENABLED", "true");
    const user = userEvent.setup();
    renderWithQuery(
      <IntlTestProvider>
        <PresetsSettingsPanel />
      </IntlTestProvider>,
    );
    await waitFor(() => expect(screen.getByText(/Advanced.*import/i)).toBeInTheDocument());
    await user.click(screen.getByText(/Advanced.*import/i));
    fireEvent.change(screen.getByTestId("preset-import-textarea"), {
      target: { value: '{"topK":3,"badKey":true}' },
    });
    await user.click(screen.getByRole("button", { name: /validate and apply import/i }));
    expect(screen.getByRole("alert")).toHaveTextContent(/badKey/i);
  });

  it("submits merged preset values on create", async () => {
    vi.stubEnv("NEXT_PUBLIC_PRESET_CREATION_ENABLED", "true");
    const user = userEvent.setup();
    renderWithQuery(
      <IntlTestProvider>
        <PresetsSettingsPanel />
      </IntlTestProvider>,
    );
    await waitFor(() => expect(screen.getByLabelText(/passages to retrieve/i)).toBeInTheDocument());

    await user.type(screen.getByLabelText(/^Name$/), "My preset");
    const topK = screen.getByLabelText(/passages to retrieve/i);
    await user.clear(topK);
    await user.type(topK, "9");
    await user.click(screen.getByRole("button", { name: /^create preset$/i }));

    await waitFor(() => {
      const posts = apiFetchMock.mock.calls.filter((c) => {
        const init = c[1] as RequestInit | undefined;
        return String(c[0]).includes("/presets") && init?.method === "POST";
      });
      expect(posts.length).toBeGreaterThan(0);
      const body = JSON.parse(String((posts[0][1] as RequestInit).body)) as {
        name: string;
        values: Record<string, unknown>;
      };
      expect(body.name).toBe("My preset");
      expect(body.values.topK).toBe(9);
    });
  });

  it("copies export JSON via clipboard", async () => {
    vi.stubEnv("NEXT_PUBLIC_PRESET_CREATION_ENABLED", "true");
    const user = userEvent.setup();
    renderWithQuery(
      <IntlTestProvider>
        <PresetsSettingsPanel />
      </IntlTestProvider>,
    );
    await waitFor(() => expect(screen.getByText(/Advanced.*import/i)).toBeInTheDocument());
    await user.click(screen.getByText(/Advanced.*import/i));
    await user.click(screen.getByRole("button", { name: /copy payload json/i }));
    await waitFor(() => {
      expect(navigator.clipboard.writeText).toHaveBeenCalled();
    });
  });
});
