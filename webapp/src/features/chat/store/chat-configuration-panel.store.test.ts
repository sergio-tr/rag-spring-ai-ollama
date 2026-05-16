import { beforeEach, describe, expect, it } from "vitest";
import { useChatConfigurationPanelStore } from "./chat-configuration-panel.store";

describe("chat configuration panel store", () => {
  beforeEach(() => {
    globalThis.localStorage.clear();
    useChatConfigurationPanelStore.setState({ open: false, hydrated: false });
  });

  it("hydrates the desktop panel state from localStorage once", () => {
    globalThis.localStorage.setItem("chat-config-panel-open-v1", "true");

    useChatConfigurationPanelStore.getState().hydrateFromStorage();
    expect(useChatConfigurationPanelStore.getState().open).toBe(true);
    expect(useChatConfigurationPanelStore.getState().hydrated).toBe(true);

    globalThis.localStorage.setItem("chat-config-panel-open-v1", "false");
    useChatConfigurationPanelStore.getState().hydrateFromStorage();
    expect(useChatConfigurationPanelStore.getState().open).toBe(true);
  });

  it("persists explicit and toggled open state", () => {
    useChatConfigurationPanelStore.getState().setOpen(true);
    expect(globalThis.localStorage.getItem("chat-config-panel-open-v1")).toBe("true");

    useChatConfigurationPanelStore.getState().toggle();
    expect(useChatConfigurationPanelStore.getState().open).toBe(false);
    expect(globalThis.localStorage.getItem("chat-config-panel-open-v1")).toBe("false");
  });
});
