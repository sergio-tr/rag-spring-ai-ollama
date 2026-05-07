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
});

