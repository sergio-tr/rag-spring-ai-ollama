"use client";

import { useMutation } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { RuntimeConfigValidateRequest, RuntimeConfigValidateResponse } from "@/types/api";

export function useRuntimeConfigValidate() {
  return useMutation({
    mutationFn: (body: RuntimeConfigValidateRequest) =>
      apiFetch<RuntimeConfigValidateResponse>(apiProductPath("/runtime-config/validate"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      }),
  });
}

