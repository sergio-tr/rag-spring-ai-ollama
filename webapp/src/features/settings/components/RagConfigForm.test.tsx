import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { IntlTestProvider } from "@/test-utils/intl";

vi.mock("@/features/settings/hooks/use-rag-config", () => ({
  useConfigSchemaQuery: () => ({ isLoading: false, isError: false, data: { fields: [] } }),
  useUserRagConfigQuery: () => ({ isLoading: false, isError: false, data: {} }),
  useProjectRagConfigQuery: () => ({ isLoading: false, isError: false, data: {} }),
  usePutUserRagConfig: () => ({ mutateAsync: vi.fn(), isPending: false, isError: false }),
  usePutProjectRagConfig: () => ({ mutateAsync: vi.fn(), isPending: false, isError: false }),
  useDeleteProjectRagConfig: () => ({ mutateAsync: vi.fn(), isPending: false }),
}));

import { RagConfigForm } from "./RagConfigForm";

describe("RagConfigForm", () => {
  it("renders no-active-project message in project mode without projectId", () => {
    render(
      <IntlTestProvider>
        <RagConfigForm mode="project" projectId={undefined} />
      </IntlTestProvider>,
    );
    expect(screen.getByRole("status")).toBeInTheDocument();
  });
});

