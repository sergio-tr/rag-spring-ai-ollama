import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import { createTestQueryClient } from "@/test-utils/query-client";
import { IntlTestProvider } from "@/test-utils/intl";
import { ClassifierRegistrySection } from "./classifier-registry-section";

const refetch = vi.fn();

vi.mock("@/features/help/HelpPopover", () => ({
  HelpPopover: () => <button type="button">Help</button>,
}));

vi.mock("@/features/lab/hooks/use-lab-status", () => ({
  useLabStatus: () => ({ data: { classifier: { configured: true } } }),
}));

vi.mock("@/features/lab/hooks/use-classifier-registry", () => ({
  useClassifierModelsQuery: () => ({
    data: [
      {
        id: "m1",
        name: "Model A",
        inferenceTag: "tag-a",
        status: "READY",
        trainedAt: null,
        accuracy: 0.9,
        f1Macro: 0.88,
        active: true,
        hyperparams: {},
      },
    ],
    isLoading: false,
    error: null,
    refetch,
  }),
  useActivateClassifierModel: () => ({ mutateAsync: vi.fn(), isPending: false }),
}));

vi.mock("@/store/app.store", () => ({
  useAppStore: (selector: (s: { activeProject: { id: string; name: string } | null }) => unknown) =>
    selector({ activeProject: { id: "p1", name: "Project One" } }),
}));

function wrap(ui: React.ReactElement) {
  const qc = createTestQueryClient();
  function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: qc }, children);
  }
  return <Wrapper>{ui}</Wrapper>;
}

describe("ClassifierRegistrySection", () => {
  beforeEach(() => {
    refetch.mockClear();
  });

  it("shows registry table when models are returned", async () => {
    render(
      wrap(
        <IntlTestProvider>
          <ClassifierRegistrySection />
        </IntlTestProvider>,
      ),
    );
    await waitFor(() => {
      expect(screen.getByTestId("classifier-registry-table")).toBeInTheDocument();
    });
    expect(screen.getByText("Model A")).toBeInTheDocument();
  });
});
