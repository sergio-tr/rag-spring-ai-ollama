"use client";

import { AccountExportPanel } from "@/features/settings/components/AccountExportPanel";
import { useTranslations } from "next-intl";
import { useState } from "react";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { AccountJobAcceptedDto } from "@/types/api";

export default function SettingsAccountPage() {
  const t = useTranslations("Settings");
  const [deleteConfirm, setDeleteConfirm] = useState("");
  const [deleteEmail, setDeleteEmail] = useState("");
  const [deleteStatus, setDeleteStatus] = useState<string | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);

  async function runDeletion() {
    setDeleteBusy(true);
    setDeleteStatus(null);
    try {
      await apiFetch<AccountJobAcceptedDto>(apiProductPath("/me/account/deletion"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          confirm: "DELETE_ACCOUNT_AND_ALL_DATA",
          email: deleteEmail.trim(),
        }),
      });
      setDeleteStatus(t("accountDeletionQueued"));
    } catch (e) {
      setDeleteStatus(e instanceof Error ? e.message : t("accountDeletionError"));
    } finally {
      setDeleteBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <AccountExportPanel />
      <Card>
        <CardHeader>
          <CardTitle>{t("accountDeletionTitle")}</CardTitle>
          <CardDescription>{t("accountDeletionDescription")}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label htmlFor="del-email">{t("accountDeletionEmailLabel")}</Label>
            <Input
              id="del-email"
              type="email"
              autoComplete="email"
              placeholder="you@example.com"
              value={deleteEmail}
              onChange={(e) => setDeleteEmail(e.target.value)}
            />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="del-confirm">{t("accountDeletionConfirmLabel")}</Label>
            <Input
              id="del-confirm"
              value={deleteConfirm}
              onChange={(e) => setDeleteConfirm(e.target.value)}
              placeholder="DELETE_ACCOUNT_AND_ALL_DATA"
            />
          </div>
          <Button
            type="button"
            variant="destructive"
            onClick={() => void runDeletion()}
            disabled={deleteBusy}
          >
            {deleteBusy ? t("accountDeletionRunning") : t("accountDeletionCta")}
          </Button>
          {deleteStatus && <p className="text-muted-foreground text-sm">{deleteStatus}</p>}
        </CardContent>
      </Card>
    </div>
  );
}
