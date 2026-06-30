import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";
import { RagConfigAdvancedJsonPanel } from "./RagConfigAdvancedJsonPanel";

describe("RagConfigAdvancedJsonPanel", () => {
  it("keeps JSON editor collapsed by default", () => {
    render(
      <IntlTestProvider>
        <RagConfigAdvancedJsonPanel config={{ topK: 5 }} onApply={vi.fn()} />
      </IntlTestProvider>,
    );
    const panel = screen.getByTestId("rag-config-advanced-json");
    expect(panel).not.toHaveAttribute("open");
    expect(screen.queryByRole("textbox", { name: /advanced configuration/i })).not.toBeVisible();
  });

  it("serializes config into the textarea when expanded", async () => {
    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <RagConfigAdvancedJsonPanel config={{ topK: 5, llmModel: "gpt-test" }} onApply={vi.fn()} />
      </IntlTestProvider>,
    );
    await user.click(screen.getByText(/Advanced configuration \(JSON\)/i));
    const textarea = screen.getByRole("textbox", { name: /advanced configuration/i });
    expect(textarea).toBeVisible();
    expect(textarea).toHaveValue(JSON.stringify({ topK: 5, llmModel: "gpt-test" }, null, 2));
  });

  it("applies parsed JSON object via onApply", async () => {
    const user = userEvent.setup();
    const onApply = vi.fn();
    render(
      <IntlTestProvider>
        <RagConfigAdvancedJsonPanel config={{ topK: 3 }} onApply={onApply} />
      </IntlTestProvider>,
    );
    await user.click(screen.getByText(/Advanced configuration \(JSON\)/i));
    const textarea = screen.getByRole("textbox", { name: /advanced configuration/i });
    await user.clear(textarea);
    await user.click(textarea);
    await user.paste('{ "topK": 7 }');
    await user.click(screen.getByRole("button", { name: /Apply JSON/i }));
    expect(onApply).toHaveBeenCalledWith({ topK: 7 });
  });

  it("shows validation error for invalid JSON", async () => {
    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <RagConfigAdvancedJsonPanel config={{}} onApply={vi.fn()} />
      </IntlTestProvider>,
    );
    await user.click(screen.getByText(/Advanced configuration \(JSON\)/i));
    const textarea = screen.getByRole("textbox", { name: /advanced configuration/i });
    await user.clear(textarea);
    await user.type(textarea, "not-json");
    await user.click(screen.getByRole("button", { name: /Apply JSON/i }));
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });
});
