import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  fetchExperimentalDatasets,
  uploadExperimentalDatasetAllow422,
  type UploadExperimentalDatasetOutcome,
} from "@/features/lab/lib/experimental-datasets-api";

export const experimentalDatasetsQueryKey = ["lab", "experimental-datasets"] as const;

export function useExperimentalDatasetsQuery() {
  return useQuery({
    queryKey: experimentalDatasetsQueryKey,
    queryFn: fetchExperimentalDatasets,
  });
}

export function useUploadExperimentalDatasetMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: uploadExperimentalDatasetAllow422,
    onSuccess: (outcome: UploadExperimentalDatasetOutcome) => {
      if (outcome.ok) {
        void queryClient.invalidateQueries({ queryKey: experimentalDatasetsQueryKey });
      }
    },
  });
}
