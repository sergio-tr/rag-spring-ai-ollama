import { describe, expect, it } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useForm } from "react-hook-form";
import { useState } from "react";
import { IntlTestProvider } from "@/test-utils/intl";
import { ProviderAwareModelParameters } from "./ProviderAwareModelParameters";
import { ProviderUnsupportedParametersPanel } from "./ProviderUnsupportedParametersPanel";
import type { ConfigFormValues } from "@/features/settings/lib/build-config-zod";
import { LLM_TEMPERATURE_KEY } from "@/features/settings/lib/provider-aware-llm-parameters";
import type { MeEffectiveLlmDefaultsResponse } from "@/types/api";

const effectiveDefaults: MeEffectiveLlmDefaultsResponse = {
  effectiveProvider: "OPENAI_COMPATIBLE",
  chatModel: "gpt-test",
  classifierModelId: "default",
  temperature: 0.1,
  additionalParameters: { think: false, topP: 1 },
};

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
        effectiveDefaults={effectiveDefaults}
        config={{ llmTemperature: 0.7, llmAdditionalParameters: additional }}
      />
      <details data-testid="advanced-wrap">
        <summary>Advanced technical details</summary>
        <ProviderUnsupportedParametersPanel
          provider={props.provider}
          config={{ llmAdditionalParameters: { topK: 40 } }}
        />
      </details>
    </>
  );
}

describe("provider-aware model parameters UI", () => {
  it("renders OpenAI-compatible applied parameters including top_p with effective values in fields", () => {
    render(
      <IntlTestProvider locale="en">
        <ParametersHarness provider="OPENAI_COMPATIBLE" />
      </IntlTestProvider>,
    );
    expect(screen.getByTestId("provider-aware-model-parameters")).toBeInTheDocument();
    const temperature = screen
      .getByTestId("model-param-field-temperature")
      .querySelector('input[type="number"]');
    expect(temperature).toHaveValue(0.7);
    const topP = screen.getByTestId("model-param-field-top_p").querySelector('input[type="number"]');
    expect(topP).toHaveValue(0.9);
    expect(screen.queryByTestId("effective-model-parameters-preview")).not.toBeInTheDocument();
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
    expect(screen.getByTestId("model-param-unsupported-top_k")).toBeInTheDocument();
  });

  it("renders ollama-applied parameters for local model provider", () => {
    render(
      <IntlTestProvider locale="en">
        <ParametersHarness provider="OLLAMA_NATIVE" />
      </IntlTestProvider>,
    );
    expect(screen.getByTestId("model-param-field-top_p")).toBeInTheDocument();
    expect(screen.getByTestId("model-param-field-num_ctx")).toBeInTheDocument();
  });

  it("shows inherited effective temperature on first load", () => {
    function InheritedHarness() {
      const form = useForm<ConfigFormValues>({ defaultValues: {} });
      const [additional, setAdditional] = useState<Record<string, unknown>>({ topP: 0.9 });
      return (
        <ProviderAwareModelParameters
          provider="OPENAI_COMPATIBLE"
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
          effectiveDefaults={effectiveDefaults}
          config={{ llmAdditionalParameters: { topP: 0.9 } }}
        />
      );
    }

    render(
      <IntlTestProvider locale="en">
        <InheritedHarness />
      </IntlTestProvider>,
    );

    const temperature = screen
      .getByTestId("model-param-field-temperature")
      .querySelector('input[type="number"]');
    const topP = screen.getByTestId("model-param-field-top_p").querySelector('input[type="number"]');
    if (!(temperature instanceof HTMLInputElement) || !(topP instanceof HTMLInputElement)) {
      throw new Error("Expected numeric parameter inputs");
    }

    expect(temperature).toHaveValue(0.1);
    expect(topP).toHaveValue(0.9);
  });
});
