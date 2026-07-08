"use client";

import { useQueryClient } from "@tanstack/react-query";
import { AccountExportPanel } from "@/features/settings/components/AccountExportPanel";
import { clearSessionCookie } from "@/features/auth/lib/session-client";
import { resetRegisteredClientSessionState } from "@/lib/client-session-reset";
import { hardNavigate } from "@/lib/hard-navigation";
import { useLocale, useTranslations } from "next-intl";
import { useCallback, useEffect, useMemo, useState } from "react";
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
import { pollAccountJob } from "@/lib/async-task";
import type { AccountJobAcceptedDto, AuthUser } from "@/types/api";

const DELETE_LITERAL = "DELETE_ACCOUNT_AND_ALL_DATA";
const DELETE_POLL_MAX_MS = 180_000;

export default function SettingsAccountPage() {
  const t = useTranslations("Settings");
  const locale = useLocale();
  const queryClient = useQueryClient();
  const [deleteConfirm, setDeleteConfirm] = useState("");
  const [deleteEmail, setDeleteEmail] = useState("");
  const [sessionEmail, setSessionEmail] = useState<string | null>(null);
  const [deleteStatus, setDeleteStatus] = useState<string | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const me = await apiFetch<AuthUser>(apiProductPath("/auth/me"));
        if (!cancelled) {
          setSessionEmail(me.email);
        }
      } catch {
        if (!cancelled) {
          setSessionEmail(null);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const confirmValid = deleteConfirm === DELETE_LITERAL;
  const emailValid =
    sessionEmail != null &&
    deleteEmail.trim().toLowerCase() === sessionEmail.trim().toLowerCase();
  const canDelete = confirmValid && emailValid && !deleteBusy;

  const runDeletion = useCallback(async () => {
    if (!canDelete) {
      return;
    }
    setDeleteBusy(true);
    setDeleteStatus(null);
    try {
      const accepted = await apiFetch<AccountJobAcceptedDto>(apiProductPath("/me/account/deletion"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          confirm: DELETE_LITERAL,
          email: deleteEmail.trim(),
        }),
      });
      setDeleteStatus(t("accountDeletionPolling"));
      const terminal = await pollAccountJob(accepted.jobId, () => undefined, {
        maxWaitMs: DELETE_POLL_MAX_MS,
      });
      if (terminal.status === "SUCCEEDED") {
        await clearSessionCookie();
        await resetRegisteredClientSessionState({ queryClient });
        setDeleteStatus(t("accountDeletionSucceeded"));
        hardNavigate("/login", locale);
        return;
      }
      setDeleteStatus(terminal.errorMessage ?? t("accountDeletionError"));
    } catch (e) {
      setDeleteStatus(e instanceof Error ? e.message : t("accountDeletionError"));
    } finally {
      setDeleteBusy(false);
    }
  }, [canDelete, deleteEmail, locale, queryClient, t]);

  const deleteHint = useMemo(() => {
    if (!confirmValid || !emailValid) {
      return t("accountDeletionConfirmHint");
    }
    return null;
  }, [confirmValid, emailValid, t]);

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
              placeholder={DELETE_LITERAL}
            />
          </div>
          {deleteHint && <p className="text-muted-foreground text-sm">{deleteHint}</p>}
          <Button
            type="button"
            variant="destructive"
            data-testid="account-delete-request"
            onClick={() => void runDeletion()}
            disabled={!canDelete}
          >
            {deleteBusy ? t("accountDeletionRunning") : t("accountDeletionCta")}
          </Button>
          {deleteStatus && <p className="text-muted-foreground text-sm">{deleteStatus}</p>}
        </CardContent>
      </Card>
    </div>
  );
}
