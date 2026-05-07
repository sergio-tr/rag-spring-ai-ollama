"use client";

import { useTranslations } from "next-intl";
import { useChatExplainStore } from "@/store/chat-explain.store";

/** Right-rail explainability: query type, pipeline steps, future sources. */
export function ExplainabilityPanel() {
  const t = useTranslations("Explain");
  const last = useChatExplainStore((s) => s.lastDone);
  const busy = useChatExplainStore((s) => s.isStreaming);

  return (
    <div className="flex flex-col gap-4 text-sm">
      {busy && (
        <p className="text-muted-foreground" aria-live="polite">
          {t("streaming")}
        </p>
      )}
      {last?.queryType && (
        <section>
          <h3 className="mb-1 font-medium text-xs uppercase tracking-wide text-muted-foreground">
            {t("queryType")}
          </h3>
          <p className="rounded-md border bg-muted/30 px-2 py-1 font-mono text-xs">{last.queryType}</p>
        </section>
      )}
      {last?.pipelineSteps && last.pipelineSteps.length > 0 && (
        <section>
          <h3 className="mb-1 font-medium text-xs uppercase tracking-wide text-muted-foreground">
            {t("pipeline")}
          </h3>
          <ol className="list-decimal space-y-1 pl-4 text-muted-foreground">
            {last.pipelineSteps.map((step) => (
              <li key={JSON.stringify(step)}>
                <span className="text-foreground">
                  {String(
                    (step as { name?: string }).name ??
                      (step as { id?: string }).id ??
                      "step",
                  )}
                </span>
                {Boolean((step as { detail?: string }).detail) && (
                  <span className="block text-xs opacity-80">
                    {String((step as { detail?: string }).detail)}
                  </span>
                )}
              </li>
            ))}
          </ol>
        </section>
      )}
      {last?.runtimeTelemetry && Object.keys(last.runtimeTelemetry).length > 0 && (
        <section data-testid="explain-runtime-telemetry">
          <h3 className="mb-1 font-medium text-xs uppercase tracking-wide text-muted-foreground">
            {t("runtimeTelemetry")}
          </h3>
          <dl className="space-y-1 font-mono text-[11px] text-muted-foreground">
            {Object.entries(last.runtimeTelemetry).map(([k, v]) => (
              <div key={k} className="flex flex-col gap-0.5 rounded-md border bg-muted/20 px-2 py-1">
                <dt className="text-foreground">{k}</dt>
                <dd className="break-all">{typeof v === "object" ? JSON.stringify(v) : String(v)}</dd>
              </div>
            ))}
          </dl>
        </section>
      )}
      {last?.sources && last.sources.length > 0 && (
        <section>
          <h3 className="mb-1 font-medium text-xs uppercase tracking-wide text-muted-foreground">
            {t("sources")}
          </h3>
          <ul className="space-y-2">
            {last.sources.map((s, i) => (
              <li key={i} className="rounded-md border p-2 text-xs">
                <pre className="whitespace-pre-wrap break-words">{JSON.stringify(s, null, 2)}</pre>
              </li>
            ))}
          </ul>
        </section>
      )}
      {!last && !busy && (
        <p className="text-muted-foreground">{t("empty")}</p>
      )}
    </div>
  );
}
