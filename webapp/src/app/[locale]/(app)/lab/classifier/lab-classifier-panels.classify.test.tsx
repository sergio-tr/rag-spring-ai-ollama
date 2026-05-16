import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClientProvider } from "@tanstack/react-query";
import { IntlTestProvider } from "@/test-utils/intl";
import { createTestQueryClient } from "@/test-utils/query-client";

vi.mock("@/features/lab/hooks/use-classifier-registry", () => ({
  classifierModelsQueryKey: ["lab", "classifier-models"],
  useClassifierModelsQuery: () => ({
    data: [{ id: "m1", name: "M", inferenceTag: "tag-1", status: "READY", active: true }],
    isLoading: false,
    isError: false,
  }),
}));

import * as apiClient from "@/lib/api-client";
import { LabClassifierClassifyPanel } from "./lab-classifier-panels";

describe("LabClassifierClassifyPanel", () => {
  beforeEach(() => {
    vi.spyOn(apiClient, "apiFetch").mockReset();
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("blocks empty classify client-side", async () => {
    const user = userEvent.setup();
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabClassifierClassifyPanel classifierOk />
        </IntlTestProvider>
      </QueryClientProvider>,
    );

    await user.clear(screen.getByLabelText(/Query/i));
    const btn = screen.getByRole("button", { name: /Classify/i });
    expect(btn).toBeDisabled();
    await user.click(btn);

    expect(apiClient.apiFetch).not.toHaveBeenCalled();
  });

  it("sends the selected classifier model id", async () => {
    const user = userEvent.setup();
    vi.mocked(apiClient.apiFetch).mockResolvedValue({ queryType: "COUNT_DOCUMENTS", modelId: "tag-1" });
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabClassifierClassifyPanel classifierOk />
        </IntlTestProvider>
      </QueryClientProvider>,
    );

    await user.selectOptions(screen.getByTestId("lab-classifier-classify-model"), "tag-1");
    await user.clear(screen.getByTestId("lab-classifier-classify-query"));
    await user.type(screen.getByTestId("lab-classifier-classify-query"), "How many meetings?");
    await user.click(screen.getByTestId("lab-classifier-classify"));

    expect(apiClient.apiFetch).toHaveBeenCalledWith(
      expect.stringContaining("/lab/classifier/classify"),
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ query: "How many meetings?", modelId: "tag-1" }),
      }),
    );
    expect(await screen.findByText(/COUNT_DOCUMENTS/)).toBeInTheDocument();
  });
});

