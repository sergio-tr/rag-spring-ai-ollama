import { describe, expect, it } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useForm } from "react-hook-form";
import { useState } from "react";
import { IntlTestProvider } from "@/test-utils/intl";
import { ProviderAwareModelParameters } from "./ProviderAwareModelParameters";
import { ProviderUnsupportedParametersPanel } from "./ProviderUnsupportedParametersPanel";
import { EffectiveModelParametersPreview } from "./EffectiveModelParametersPreview";
import type { ConfigFormValues } from "@/features/settings/lib/build-config-zod";
import { LLM_TEMPERATURE_KEY } from "@/features/settings/lib/provider-aware-llm-parameters";

function ParametersHarness(props: Readonly<{ provider: "OPENAI_COMPATIBLE" | "OLLAMA_NATIVE" }>) {
  const form = useForm<ConfigFormValues>({ defaultValues: { [LLM_TEMPERATURE_KEY]: 0.7 } });
  const [additional, setAdditional] = useState<Record<string, unknown>>({ topP: 0.9 });

  return (
    <>
      <ProviderAwareModelParameters
        provider={props.provider}
        form={form}
        additionalParameters={additional}
        onAdditionalParameterChange={(key, value) => {
          setAdditional((prev) => {
            const next = { ...prev };
            if (value === undefined) delete next[key];
            else next[key] = value;
            return next;
          });
        }}
      />
      <EffectiveModelParametersPreview
        provider={props.provider}
        config={{ llmTemperature: 0.7, llmAdditionalParameters: additional }}
      />
      <details data-testid="advanced-wrap">
        <summary>Advanced technical details</summary>
        <ProviderUnsupportedParametersPanel
          provider={props.provider}
          config={{ llmAdditionalParameters: { presencePenalty: 0.1 } }}
        />
      </details>
    </>
  );
}

describe("provider-aware model parameters UI", () => {
  it("renders only temperature for configured model provider", () => {
    render(
      <IntlTestProvider locale="en">
        <ParametersHarness provider="OPENAI_COMPATIBLE" />
      </IntlTestProvider>,
    );
    expect(screen.getByTestId("provider-aware-model-parameters")).toBeInTheDocument();
    expect(screen.getByTestId("model-param-field-temperature")).toBeInTheDocument();
    expect(screen.queryByTestId("model-param-field-top_p")).not.toBeInTheDocument();
    expect(screen.getByTestId("effective-model-parameters-preview")).toHaveTextContent("Temperature");
    expect(screen.getByTestId("model-param-effective-temperature")).toHaveTextContent("0.7");
  });

  it("hides unsupported parameters until advanced technical details is open", async () => {
    const user = userEvent.setup();
    render(
      <IntlTestProvider locale="en">
        <ParametersHarness provider="OPENAI_COMPATIBLE" />
      </IntlTestProvider>,
    );
    const unsupported = screen.queryByTestId("provider-unsupported-model-parameters");
    if (unsupported) {
      expect(unsupported).not.toBeVisible();
    }
    const wrap = screen.getByTestId("advanced-wrap");
    await user.click(within(wrap).getByText(/Advanced technical details/i));
    expect(screen.getByTestId("provider-unsupported-model-parameters")).toBeInTheDocument();
    expect(screen.getByTestId("model-param-unsupported-top_p")).toBeInTheDocument();
  });

  it("renders ollama-applied parameters for local model provider", () => {
    render(
      <IntlTestProvider locale="en">
        <ParametersHarness provider="OLLAMA_NATIVE" />
      </IntlTestProvider>,
    );
    expect(screen.getByTestId("model-param-field-top_p")).toBeInTheDocument();
    expect(screen.getByTestId("model-param-field-num_ctx")).toBeInTheDocument();
    expect(screen.getByTestId("model-param-effective-top_p")).toHaveTextContent("0.9");
  });
});
