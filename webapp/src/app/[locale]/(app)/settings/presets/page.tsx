"use client";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { RagPresetDto } from "@/types/api";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { useMemo, useState } from "react";

const presetsKey = ["presets"] as const;

export default function SettingsPresetsPage() {
  const t = useTranslations("Settings");
  const qc = useQueryClient();
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [valuesJson, setValuesJson] = useState("{}");
  const [formError, setFormError] = useState<string | null>(null);

  const listQ = useQuery({
    queryKey: presetsKey,
    queryFn: () => apiFetch<RagPresetDto[]>(apiProductPath("/presets")),
  });

  const createM = useMutation({
    mutationFn: async () => {
      let values: Record<string, unknown> = {};
      try {
        values = JSON.parse(valuesJson || "{}") as Record<string, unknown>;
      } catch {
        throw new Error(t("presetsInvalidJson"));
      }
      return apiFetch<RagPresetDto>(apiProductPath("/presets"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name, description: description || null, tags: [], values }),
      });
    },
    onSuccess: () => {
      setName("");
      setDescription("");
      setValuesJson("{}");
      setFormError(null);
      void qc.invalidateQueries({ queryKey: presetsKey });
    },
    onError: (e: unknown) => {
      setFormError(e instanceof Error ? e.message : t("presetsSaveError"));
    },
  });

  const deleteM = useMutation({
    mutationFn: (id: string) => apiFetch(apiProductPath(`/presets/${id}`), { method: "DELETE" }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: presetsKey }),
  });

  const items = useMemo(() => listQ.data ?? [], [listQ.data]);

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader>
          <CardTitle>{t("presetsTitle")}</CardTitle>
          <CardDescription>{t("presetsDescription")}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {listQ.isLoading ? <p className="text-muted-foreground text-sm">{t("configLoading")}</p> : null}
          {listQ.isError ? (
            <p className="text-destructive text-sm" role="alert">
              {t("presetsLoadError")}
            </p>
          ) : null}
          {!listQ.isLoading && !listQ.isError && items.length === 0 ? (
            <p className="text-muted-foreground text-sm">{t("presetsEmpty")}</p>
          ) : null}
          <ul className="flex flex-col gap-2">
            {items.map((p) => (
              <li
                key={p.id}
                className="bg-muted/40 flex flex-wrap items-center justify-between gap-2 rounded-md border border-border px-3 py-2 text-sm"
              >
                <div>
                  <span className="font-medium">{p.name}</span>
                  {p.system ? (
                    <span className="text-muted-foreground ml-2">({t("presetsSystem")})</span>
                  ) : null}
                  <pre className="mt-1 max-h-24 max-w-xl overflow-auto text-xs">
                    {JSON.stringify(p.values, null, 2)}
                  </pre>
                </div>
                {!p.system ? (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={deleteM.isPending}
                    onClick={() => deleteM.mutate(p.id)}
                  >
                    {t("presetsDelete")}
                  </Button>
                ) : null}
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t("presetsCreateTitle")}</CardTitle>
          <CardDescription>{t("presetsCreateDescription")}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          <div className="grid gap-2">
            <Label htmlFor="preset-name">{t("presetsName")}</Label>
            <Input
              id="preset-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              autoComplete="off"
            />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="preset-desc">{t("presetsDescriptionField")}</Label>
            <Input
              id="preset-desc"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              autoComplete="off"
            />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="preset-values">{t("presetsValuesJson")}</Label>
            <Textarea
              id="preset-values"
              value={valuesJson}
              onChange={(e) => setValuesJson(e.target.value)}
              className="font-mono text-xs"
              rows={8}
            />
          </div>
          {formError ? (
            <p className="text-destructive text-sm" role="alert">
              {formError}
            </p>
          ) : null}
          <Button
            type="button"
            disabled={createM.isPending || !name.trim()}
            onClick={() => createM.mutate()}
          >
            {t("presetsCreateSubmit")}
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
