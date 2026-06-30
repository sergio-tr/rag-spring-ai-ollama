import { describe, expect, it } from "vitest";
import {
  buildPersonalizationPutPayload,
  buildPreferencesPutPayload,
  isStoredPreferenceLocaleUnsupported,
  isStoredPersonalizationThemeUnsupported,
  personalizationFormDefaults,
  personalizationFormSchema,
  preferencesFormDefaults,
  preferencesFormSchema,
} from "./me-canonical-user-config";

describe("me-canonical-user-config", () => {
  describe("preferencesFormDefaults", () => {
    it("reads supported locale from stored map", () => {
      expect(preferencesFormDefaults({ locale: "es" })).toEqual({ locale: "es" });
    });

    it("falls back to default locale when missing or unsupported", () => {
      expect(preferencesFormDefaults({})).toEqual({ locale: "en" });
      expect(preferencesFormDefaults({ locale: "xx" })).toEqual({ locale: "en" });
      expect(preferencesFormDefaults({ locale: 1 })).toEqual({ locale: "en" });
    });
  });

  describe("buildPreferencesPutPayload", () => {
    it("preserves keys outside structured locale", () => {
      const payload = buildPreferencesPutPayload(
        { locale: "en", obsoleteTheme: "dark", nested: { a: 1 } },
        { locale: "es" },
      );
      expect(payload).toEqual({
        locale: "es",
        obsoleteTheme: "dark",
        nested: { a: 1 },
      });
    });
  });

  describe("isStoredPreferenceLocaleUnsupported", () => {
    it("detects non-en/es locale values", () => {
      expect(isStoredPreferenceLocaleUnsupported({})).toBe(false);
      expect(isStoredPreferenceLocaleUnsupported({ locale: "en" })).toBe(false);
      expect(isStoredPreferenceLocaleUnsupported({ locale: "fr" })).toBe(true);
      expect(isStoredPreferenceLocaleUnsupported({ locale: 3 })).toBe(true);
    });
  });

  describe("preferencesFormSchema", () => {
    it("accepts en and es only", () => {
      expect(preferencesFormSchema.safeParse({ locale: "en" }).success).toBe(true);
      expect(preferencesFormSchema.safeParse({ locale: "xx" }).success).toBe(false);
    });
  });

  describe("personalization", () => {
    it("defaults theme when unsupported and reads global persona", () => {
      expect(personalizationFormDefaults({ theme: "dark" })).toEqual({
        theme: "dark",
        globalPersonaPrompt: "",
      });
      expect(personalizationFormDefaults({ theme: "fancy", globalPersonaPrompt: "Be concise" })).toEqual({
        theme: "system",
        globalPersonaPrompt: "Be concise",
      });
    });

    it("merges structured theme and persona while preserving extras", () => {
      expect(
        buildPersonalizationPutPayload(
          { accent: "blue", theme: "light" },
          { theme: "dark", globalPersonaPrompt: "  helpful  " },
        ),
      ).toEqual({ accent: "blue", theme: "dark", globalPersonaPrompt: "helpful" });
      expect(
        buildPersonalizationPutPayload({ theme: "light" }, { theme: "dark", globalPersonaPrompt: "   " }),
      ).toEqual({ theme: "dark", globalPersonaPrompt: null });
    });

    it("detects unsupported stored theme", () => {
      expect(isStoredPersonalizationThemeUnsupported({ theme: "dark" })).toBe(false);
      expect(isStoredPersonalizationThemeUnsupported({ theme: "fancy" })).toBe(true);
    });

    it("validates theme enum", () => {
      expect(personalizationFormSchema.safeParse({ theme: "system" }).success).toBe(true);
      expect(personalizationFormSchema.safeParse({ theme: "fancy" }).success).toBe(false);
      expect(personalizationFormSchema.safeParse({ theme: "system", globalPersonaPrompt: "x".repeat(50_001) }).success).toBe(false);
    });
  });
});
