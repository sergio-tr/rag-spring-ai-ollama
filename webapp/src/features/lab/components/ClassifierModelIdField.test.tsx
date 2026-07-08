import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen } from "@testing-library/react";
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

import { LabClassifierClassifyPanel } from "@/app/[locale]/(app)/lab/classifier/lab-classifier-panels";

describe("ClassifierModelIdField", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("does not show ambiguous Model id label", () => {
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <IntlTestProvider>
          <LabClassifierClassifyPanel classifierOk />
        </IntlTestProvider>
      </QueryClientProvider>,
    );

    expect(screen.queryByText("modelId")).not.toBeInTheDocument();
    expect(screen.queryByText(/Model id \(optional\)/i)).not.toBeInTheDocument();
    expect(screen.getByLabelText(/Classifier model \(optional\)/i)).toBeInTheDocument();
    expect(screen.getByText(/Optional registered classifier model/i)).toBeInTheDocument();
  });
});
