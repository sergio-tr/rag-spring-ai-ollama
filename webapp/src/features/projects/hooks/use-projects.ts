"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, apiFetch, apiProductPath } from "@/lib/api-client";
import { activeProjectFromSummary, useAppStore } from "@/store/app.store";
import {
  type CreateProjectOutcome,
  reconcileProjectAfterPostFailure,
} from "@/features/projects/lib/project-create-reconciliation";
import type {
  ActivateProjectResponse,
  CreateProjectBody,
  ProjectListResponse,
  ProjectSummary,
} from "@/types/api";

export type { CreateProjectOutcome };

const projectsKey = ["projects"] as const;

/** Merge POST /projects result into cached list pages before refetch (avoids clearing active via stale rows). */
function prependCreatedProjectToProjectListCaches(
  queryClient: ReturnType<typeof useQueryClient>,
  created: ProjectSummary,
): void {
  queryClient.setQueriesData<ProjectListResponse>({ queryKey: projectsKey }, (old) => {
    if (!old) {
      return { items: [created], total: 1 };
    }
    if (old.items.some((p) => p.id === created.id)) {
      return old;
    }
    return {
      ...old,
      items: [created, ...old.items],
      total: old.total + 1,
    };
  });
}

export function useProjectList(page = 0, size = 24) {
  return useQuery({
    queryKey: [...projectsKey, page, size],
    queryFn: async () => {
      const search = new URLSearchParams({
        page: String(page),
        size: String(size),
      });
      return apiFetch<ProjectListResponse>(apiProductPath(`/projects?${search.toString()}`));
    },
  });
}

export function useCreateProject() {
  const queryClient = useQueryClient();
  const setActiveProject = useAppStore((s) => s.setActiveProject);

  return useMutation({
    mutationFn: async (body: CreateProjectBody): Promise<CreateProjectOutcome> => {
      let created: ProjectSummary;
      let reconciledFromList = false;

      try {
        created = await apiFetch<ProjectSummary>(apiProductPath("/projects"), {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
        });
      } catch (postErr) {
        const reconciled = await reconcileProjectAfterPostFailure(body, postErr);
        if (!reconciled) {
          throw postErr;
        }
        created = reconciled;
        reconciledFromList = true;
      }

      if (!created?.id?.trim()) {
        throw new ApiError(500, "Create project response missing id");
      }

      let activateFailed = false;
      try {
        await apiFetch<ActivateProjectResponse>(apiProductPath(`/projects/${created.id}/activate`), {
          method: "PUT",
        });
      } catch (activateErr) {
        // POST succeeded — never surface activate/refetch issues as "could not create project".
        activateFailed = true;
        if (activateErr instanceof ApiError && activateErr.status === 401) {
          useAppStore.getState().setActiveProject(null);
        }
      }

      return { project: created, activateFailed, reconciledFromList };
    },
    onSuccess: (outcome) => {
      const created = outcome.project;
      if (!outcome.activateFailed) {
        setActiveProject(activeProjectFromSummary(created));
      }
      prependCreatedProjectToProjectListCaches(queryClient, created);
      void queryClient.invalidateQueries({ queryKey: projectsKey }).catch(() => {
        /* Refetch failure must not undo a successful create. */
      });
      void queryClient.invalidateQueries({ queryKey: ["config", "project", created.id] }).catch(() => {
        /* Same — config refresh is best-effort after create. */
      });
    },
    onError: (err) => {
      if (err instanceof ApiError && err.status === 401) {
        useAppStore.getState().setActiveProject(null);
      }
    },
  });
}

export function usePatchProject() {
  const queryClient = useQueryClient();
  const active = useAppStore((s) => s.activeProject);
  const setActiveProject = useAppStore((s) => s.setActiveProject);

  return useMutation({
    mutationFn: (vars: {
      id: string;
      name?: string;
      description?: string | null;
      projectPrompt?: string | null;
      colorHex?: string | null;
      iconKey?: string | null;
    }) => {
      const body: Record<string, unknown> = {};
      if (vars.name !== undefined) body.name = vars.name;
      if (vars.description !== undefined) body.description = vars.description;
      if (vars.projectPrompt !== undefined) body.projectPrompt = vars.projectPrompt;
      if (vars.colorHex !== undefined) body.colorHex = vars.colorHex;
      if (vars.iconKey !== undefined) body.iconKey = vars.iconKey;
      return apiFetch<ProjectSummary>(apiProductPath(`/projects/${vars.id}`), {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
    },
    onSuccess: (updated) => {
      void queryClient.invalidateQueries({ queryKey: projectsKey });
      if (active?.id === updated.id) {
        setActiveProject(activeProjectFromSummary(updated));
      }
    },
  });
}

export function useDeleteProject() {
  const queryClient = useQueryClient();
  const active = useAppStore((s) => s.activeProject);
  const setActiveProject = useAppStore((s) => s.setActiveProject);

  return useMutation({
    mutationFn: (projectId: string) =>
      apiFetch<void>(apiProductPath(`/projects/${projectId}`), { method: "DELETE" }),
    onSuccess: (_data, projectId) => {
      void queryClient.invalidateQueries({ queryKey: projectsKey });
      if (active?.id === projectId) {
        setActiveProject(null);
      }
      void queryClient.removeQueries({ queryKey: ["config", "project", projectId] });
      void queryClient.removeQueries({ queryKey: ["project-documents", projectId] });
    },
  });
}

export function useActivateProject() {
  const queryClient = useQueryClient();
  const setActiveProject = useAppStore((s) => s.setActiveProject);

  return useMutation({
    mutationFn: async (
      vars: {
        id: string;
        name: string;
        iconKey?: string | null;
        colorHex?: string | null;
      },
    ) => {
      const res = await apiFetch<ActivateProjectResponse>(
        apiProductPath(`/projects/${vars.id}/activate`),
        { method: "PUT" },
      );
      return res;
    },
    onSuccess: async (_data, vars) => {
      void queryClient.invalidateQueries({ queryKey: projectsKey });
      setActiveProject(
        activeProjectFromSummary({
          id: vars.id,
          name: vars.name,
          iconKey: vars.iconKey,
          colorHex: vars.colorHex,
        }),
      );
      void queryClient.invalidateQueries({ queryKey: ["config", "project", vars.id] });
    },
    onError: (err) => {
      if (err instanceof ApiError && err.status === 401) {
        setActiveProject(null);
      }
    },
  });
}
