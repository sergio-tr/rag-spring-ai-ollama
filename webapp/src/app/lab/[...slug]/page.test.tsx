import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";

const replaceMock = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: replaceMock }),
}));

vi.mock("@/i18n/routing", () => ({
  routing: { defaultLocale: "en" },
}));

describe("/lab/[...slug] bridge", () => {
  beforeEach(() => {
    replaceMock.mockReset();
    Object.defineProperty(window, "location", {
      writable: true,
      value: {
        pathname: "/lab/classifier",
        search: "?x=1",
        hash: "#tab",
      },
    });
  });

  it("preserves subpath, search, and hash", async () => {
    const Page = (await import("./page")).default;
    render(<Page params={{ slug: ["classifier"] }} />);
    expect(screen.getByText(/Redirecting/i)).toBeInTheDocument();
    expect(replaceMock).toHaveBeenCalledWith("/en/lab/classifier?x=1#tab");
  });
});

