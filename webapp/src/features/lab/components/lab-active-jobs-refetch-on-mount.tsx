"use client";

import { activeLabJobsQueryKey } from "@/features/lab/hooks/use-active-lab-jobs";
import { useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";

/** Refetch backend active jobs whenever the Lab section mounts (navigation recovery). */
export function LabActiveJobsRefetchOnMount() {
  const queryClient = useQueryClient();
  useEffect(() => {
    void queryClient.invalidateQueries({ queryKey: activeLabJobsQueryKey });
  }, [queryClient]);
  return null;
}
