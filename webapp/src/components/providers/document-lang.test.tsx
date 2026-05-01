import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, waitFor } from "@testing-library/react";
import { DocumentLang } from "./document-lang";

const useLocale = vi.fn(() => "en");

vi.mock("next-intl", () => ({
  useLocale: () => useLocale(),
}));

describe("DocumentLang", () => {
  const originalLang = document.documentElement.lang;

  beforeEach(() => {
    document.documentElement.lang = "";
    useLocale.mockReturnValue("en");
  });

  afterEach(() => {
    document.documentElement.lang = originalLang;
  });

  it("syncs documentElement.lang to the active locale", async () => {
    useLocale.mockReturnValue("es");
    render(<DocumentLang />);
    await waitFor(() => {
      expect(document.documentElement.lang).toBe("es");
    });
  });
});
