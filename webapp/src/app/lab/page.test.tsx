import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";

const replaceMock = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: replaceMock }),
}));

vi.mock("@/i18n/routing", () => ({
  routing: { defaultLocale: "en" },
}));

describe("/lab bridge", () => {
  beforeEach(() => {
    replaceMock.mockReset();
    Object.defineProperty(window, "location", {
      writable: true,
      value: {
        pathname: "/lab",
        search: "",
        hash: "#datasets",
      },
    });
  });

  it("preserves hash and redirects to default locale", async () => {
    const Page = (await import("./page")).default;
    render(<Page />);
    expect(screen.getByText(/Redirecting/i)).toBeInTheDocument();
    expect(replaceMock).toHaveBeenCalledWith("/en/lab#datasets");
  });
});

