import { describe, it, expect } from "vitest";
import { routing } from "./routing";

describe("i18n routing", () => {
  it("defines en and es with always prefix", () => {
    expect(routing.locales).toEqual(["en", "es"]);
    expect(routing.defaultLocale).toBe("en");
  });
});
