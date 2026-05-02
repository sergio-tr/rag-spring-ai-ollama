import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, waitFor } from "@testing-library/react";
import { SettingsTabQueryNormalizerInner } from "./SettingsNavigationPersistence";

const mockReplace = vi.fn();
const mockPathname = vi.fn(() => "/settings");
const mockSearchParams = vi.fn(() => new URLSearchParams());

vi.mock("@/navigation", () => ({
  usePathname: () => mockPathname(),
  useRouter: () => ({ replace: mockReplace }),
}));

vi.mock("next/navigation", () => ({
  useSearchParams: () => mockSearchParams(),
}));

describe("SettingsTabQueryNormalizerInner", () => {
  beforeEach(() => {
    mockReplace.mockClear();
    mockPathname.mockReturnValue("/settings");
    mockSearchParams.mockReturnValue(new URLSearchParams());
  });

  it("replaces /settings?tab=account with /settings/account", async () => {
    mockSearchParams.mockReturnValue(new URLSearchParams("tab=account"));
    render(<SettingsTabQueryNormalizerInner />);
    await waitFor(() => {
      expect(mockReplace).toHaveBeenCalledWith("/settings/account");
    });
  });

  it("normalizes general tab to /settings", async () => {
    mockSearchParams.mockReturnValue(new URLSearchParams("tab=general"));
    render(<SettingsTabQueryNormalizerInner />);
    await waitFor(() => {
      expect(mockReplace).toHaveBeenCalledWith("/settings");
    });
  });

  it("falls back to /settings for invalid tab", async () => {
    mockSearchParams.mockReturnValue(new URLSearchParams("tab=nope"));
    render(<SettingsTabQueryNormalizerInner />);
    await waitFor(() => {
      expect(mockReplace).toHaveBeenCalledWith("/settings");
    });
  });

  it("does nothing when not on settings root", () => {
    mockPathname.mockReturnValue("/settings/user");
    mockSearchParams.mockReturnValue(new URLSearchParams("tab=account"));
    render(<SettingsTabQueryNormalizerInner />);
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it("does nothing when tab query is absent", () => {
    mockSearchParams.mockReturnValue(new URLSearchParams());
    render(<SettingsTabQueryNormalizerInner />);
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it("calls replace with locale-neutral paths only", async () => {
    mockSearchParams.mockReturnValue(new URLSearchParams("tab=data"));
    render(<SettingsTabQueryNormalizerInner />);
    await waitFor(() => {
      expect(mockReplace).toHaveBeenCalledWith("/settings/data");
      expect(mockReplace.mock.calls[0]?.[0]).not.toContain("/en/");
    });
  });
});
