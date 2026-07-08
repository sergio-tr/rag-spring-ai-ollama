import { render } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";
import { activeLabJobsQueryKey } from "@/features/lab/hooks/use-active-lab-jobs";
import { LabActiveJobsRefetchOnMount } from "@/features/lab/components/lab-active-jobs-refetch-on-mount";

describe("LabActiveJobsRefetchOnMount", () => {
  it("invalidates active lab jobs query on mount", () => {
    const queryClient = new QueryClient();
    const invalidate = vi.spyOn(queryClient, "invalidateQueries");

    render(
      <QueryClientProvider client={queryClient}>
        <LabActiveJobsRefetchOnMount />
      </QueryClientProvider>,
    );

    expect(invalidate).toHaveBeenCalledWith({ queryKey: activeLabJobsQueryKey });
  });
});
