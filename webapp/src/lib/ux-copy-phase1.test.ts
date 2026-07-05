import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const messagesDir = join(dirname(fileURLToPath(import.meta.url)), "../../messages");
const en = JSON.parse(readFileSync(join(messagesDir, "en.json"), "utf8")) as {
  Settings: Record<string, string>;
  Chat: Record<string, string>;
};

describe("S3 Phase 1 UX copy", () => {
  it("uses neutral model registry copy in Settings General", () => {
    expect(en.Settings.modelRegistryCardTitle).toBe("Recommended models");
    expect(en.Settings.modelRegistryCardTitleConfigured).toBe("Configured models");
    expect(en.Settings.modelRegistryCardDescriptionConfigured).not.toMatch(/download missing models/i);
    expect(en.Settings.modelRegistryCardDescriptionConfigured).toMatch(/LiteLLM\/OpenAI-compatible catalog/i);
    expect(en.Settings.modelRegistryCardDescription).not.toMatch(/Ollama/i);
    expect(en.Settings.modelRegistryServerUnreachable).not.toMatch(/Ollama/i);
    expect(en.Settings.modelRegistryServerUnreachable).toMatch(/model server is not reachable/i);
  });

  it("uses plain user config descriptions without JSON or server schema", () => {
    expect(en.Settings.userConfigDescription).not.toMatch(/JSON/i);
    expect(en.Settings.userConfigDescription).toMatch(/Models, prompts, and defaults/i);
    expect(en.Settings.userConfigFormDescription).not.toMatch(/server schema/i);
    expect(en.Settings.userConfigFormDescription).toMatch(/apply to your account/i);
  });

  it("shows answer quality checks label in chat metadata", () => {
    expect(en.Chat.chatMoreInformationLabel).toBe("Answer quality checks");
    expect(en.Chat.runtimeCurrentSettingsTitle).toBe("Current settings");
    expect(en.Chat.configCompactIndex).toBe("Search index");
    expect(en.Chat.configCompactIndexReady).toBe("Ready");
    expect(en.Chat.configCompactIndexNeedsRebuild).toBe("Needs rebuild");
    expect(en.Chat.configCompactIndexNotAvailable).toBe("Not available");
  });
});
