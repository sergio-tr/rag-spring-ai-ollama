import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import SettingsAccountPage from "./page";

vi.mock("next-intl", () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn() }),
}));

vi.mock("@/features/settings/components/AccountExportPanel", () => ({
  AccountExportPanel: () => <div data-testid="account-export-panel-mock" />,
}));

vi.mock("@/features/auth/lib/session-client", () => ({
  clearSessionCookie: vi.fn(),
}));

vi.mock("@/lib/async-task", () => ({
  pollAccountJob: vi.fn(),
}));

vi.mock("@/lib/api-client", () => ({
  apiFetch: vi.fn(),
  apiProductPath: (p: string) => p,
}));

import { apiFetch } from "@/lib/api-client";

describe("SettingsAccountPage", () => {
  beforeEach(() => {
    vi.mocked(apiFetch).mockImplementation(async (url: string) => {
      if (url.includes("/auth/me")) {
        return { id: "u1", email: "user@test.local", name: "U", role: "USER" };
      }
      throw new Error("unexpected " + url);
    });
  });

  it("disables delete until email and confirm literal match", async () => {
    const user = userEvent.setup();
    render(<SettingsAccountPage />);

    const btn = await screen.findByTestId("account-delete-request");
    expect(btn).toBeDisabled();

    await user.type(screen.getByLabelText(/accountDeletionEmailLabel/i), "user@test.local");
    expect(btn).toBeDisabled();

    await user.type(
      screen.getByLabelText(/accountDeletionConfirmLabel/i),
      "DELETE_ACCOUNT_AND_ALL_DATA",
    );
    expect(btn).toBeEnabled();
  });
});
