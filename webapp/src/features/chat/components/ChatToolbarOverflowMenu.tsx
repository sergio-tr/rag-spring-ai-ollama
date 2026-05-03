"use client";

import { MoreVertical } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId } from "react";
import { buttonVariants } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Label } from "@/components/ui/label";
import { resolvePresetSelectLabel } from "@/features/chat/lib/conversation-preset-ui";
import { useChatToolbarStore } from "@/features/chat/store/chat-toolbar.store";
import { cn } from "@/lib/utils";

function MenuHint({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <output className="text-muted-foreground mt-0.5 block max-w-[18rem] text-xs font-normal leading-snug">
      {children}
    </output>
  );
}

/**
 * Shell toolbar overflow for Chat: model, preset, retrieval limits, documents sheet, move, delete.
 * State comes from {@link useChatToolbarStore} populated by the chat page.
 */
export function ChatToolbarOverflowMenu() {
  const t = useTranslations("SectionActions");
  const tChat = useTranslations("Chat");
  const api = useChatToolbarStore((s) => s.api);
  const modelSelectId = useId();
  const presetSelectId = useId();

  const needsProject = !api?.projectId;
  const needsConversation = !api?.conversationId;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        type="button"
        data-testid="chat-actions-menu-trigger"
        className={cn(buttonVariants({ variant: "ghost", size: "icon-sm" }), "shrink-0")}
        aria-label={t("chatMenuLabel")}
      >
        <MoreVertical className="size-4" aria-hidden />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="max-h-[min(85vh,32rem)] w-[min(94vw,22rem)] overflow-y-auto">
        <DropdownMenuItem
          className="cursor-default"
          onSelect={(e) => {
            e.preventDefault();
          }}
        >
          <div className="flex w-full flex-col gap-1 py-1">
            <Label htmlFor={modelSelectId} className="text-xs">
              {tChat("modelLabel")}
            </Label>
            <select
              id={modelSelectId}
              className={cn(
                "border-input bg-background h-9 w-full rounded-md border px-2 text-sm",
                api?.modelsError && "border-destructive",
              )}
              value={api?.llmModelChoice ?? ""}
              onChange={(e) => api?.setLlmModelChoice(e.target.value)}
              disabled={needsProject || needsConversation || !!api?.modelsError}
              aria-label={tChat("modelLabel")}
            >
              <option value="">{tChat("modelDefault")}</option>
              {api?.modelsCatalog?.allowlist
                ?.filter((e) => e.type === "LLM")
                .sort((a, b) => a.name.localeCompare(b.name))
                .map((m) => {
                  const usable = m.inAllowlist && m.installedInOllama;
                  return (
                    <option key={m.name} value={m.name} disabled={!usable}>
                      {m.name}
                      {!m.installedInOllama ? ` (${tChat("modelNotInstalled")})` : ""}
                      {!m.inAllowlist ? ` (${tChat("modelNotAllowlisted")})` : ""}
                    </option>
                  );
                })}
            </select>
            {api?.modelsError ? (
              <p className="text-destructive text-xs" role="alert">
                {api.modelsErrorMessage || tChat("modelsLoadError")}
              </p>
            ) : null}
            {!api?.modelsError && !api?.modelsCatalog?.ollamaReachable && api ? (
              <p className="text-muted-foreground text-xs">{tChat("ollamaUnreachable")}</p>
            ) : null}
          </div>
        </DropdownMenuItem>

        <DropdownMenuItem
          className="cursor-default"
          onSelect={(e) => {
            e.preventDefault();
          }}
        >
          <div className="flex w-full flex-col gap-1 py-1">
            <Label htmlFor={presetSelectId} className="text-xs">
              {tChat("presetLabel")}
            </Label>
            <select
              id={presetSelectId}
              className={cn(
                "border-input bg-background h-9 w-full rounded-md border px-2 text-sm",
                api?.presetsError && "border-destructive",
              )}
              value={api?.presetSelectValue ?? ""}
              onChange={(e) => api?.onPresetChange(e.target.value)}
              disabled={needsProject || needsConversation || !!api?.presetSelectDisabled}
              aria-label={tChat("presetLabel")}
            >
              {api?.syntheticPresetOptionNeeded && api.presets ? (
                <option value={api.presetSelectValue}>
                  {resolvePresetSelectLabel(api.presets, api.presetSelectValue, api.presetLabelOpts)}
                </option>
              ) : null}
              {api?.presets?.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                  {p.system ? ` (${tChat("presetSystem")})` : ""}
                </option>
              ))}
            </select>
            {api?.presetsLoading && !api.presetsError ? (
              <p className="text-muted-foreground text-xs">{tChat("presetCatalogLoading")}</p>
            ) : null}
            {api?.presets?.length === 0 && !api.presetsError ? (
              <output className="text-muted-foreground block text-xs">{tChat("presetCatalogEmpty")}</output>
            ) : null}
            {api?.presetsError ? <p className="text-destructive text-xs">{tChat("presetsLoadError")}</p> : null}
          </div>
        </DropdownMenuItem>

        <DropdownMenuItem
          className="cursor-default flex-col items-stretch"
          onSelect={(e) => {
            e.preventDefault();
          }}
        >
          <div className="flex w-full flex-col gap-2 py-1">
            <label className="flex cursor-pointer items-start gap-2 text-sm leading-snug">
              <input
                type="checkbox"
                className="border-input mt-0.5 size-4 shrink-0 rounded"
                checked={api?.limitDocs ?? false}
                onChange={(e) => api?.onLimitDocsChange(e.target.checked)}
                disabled={needsProject || needsConversation || !!api?.patchConvPending}
              />
              <span>{tChat("limitDocuments")}</span>
            </label>
            {api?.limitDocsToggleNotice ? <MenuHint>{api.limitDocsToggleNotice}</MenuHint> : null}
            <button
              type="button"
              data-testid="chat-open-documents-sheet"
              className={cn(
                buttonVariants({ variant: "outline", size: "sm" }),
                "h-8 w-full justify-center",
              )}
              disabled={needsProject || needsConversation || !!api?.patchConvPending}
              onClick={() => api?.openDocumentsSheet()}
            >
              {tChat("chatManageDocuments")}
            </button>
          </div>
        </DropdownMenuItem>

        <DropdownMenuSeparator />

        <DropdownMenuItem
          disabled={needsProject || needsConversation}
          className={needsProject || needsConversation ? "opacity-60" : ""}
          onClick={() => {
            if (!needsProject && !needsConversation) api?.openMoveDialog();
          }}
        >
          <span>{t("chatMoveProject")}</span>
          {needsProject ? <MenuHint>{t("needsActiveProject")}</MenuHint> : null}
          {needsConversation ? <MenuHint>{t("needsActiveConversation")}</MenuHint> : null}
        </DropdownMenuItem>

        <DropdownMenuSeparator />

        <DropdownMenuItem
          data-testid="chat-delete-menu-item"
          disabled={needsProject || needsConversation}
          variant={needsProject || needsConversation ? "default" : "destructive"}
          className={needsProject || needsConversation ? "opacity-60" : ""}
          onClick={() => {
            if (!needsProject && !needsConversation) api?.openDeleteForActiveConversation();
          }}
        >
          <span>{t("chatDelete")}</span>
          {needsProject ? <MenuHint>{t("needsActiveProject")}</MenuHint> : null}
          {needsConversation ? <MenuHint>{t("needsActiveConversation")}</MenuHint> : null}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
