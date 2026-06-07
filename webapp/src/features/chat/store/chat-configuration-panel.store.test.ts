import { beforeEach, describe, expect, it, vi } from "vitest";
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

  it("treats storage read failures as closed", () => {
    const getItem = vi.spyOn(globalThis.localStorage, "getItem").mockImplementation(() => {
      throw new Error("storage blocked");
    });
    useChatConfigurationPanelStore.setState({ open: true, hydrated: false });
    useChatConfigurationPanelStore.getState().hydrateFromStorage();
    expect(useChatConfigurationPanelStore.getState().open).toBe(false);
    getItem.mockRestore();
  });

  it("ignores storage write failures", () => {
    const setItem = vi.spyOn(globalThis.localStorage, "setItem").mockImplementation(() => {
      throw new Error("quota");
    });
    expect(() => useChatConfigurationPanelStore.getState().setOpen(true)).not.toThrow();
    expect(useChatConfigurationPanelStore.getState().open).toBe(true);
    setItem.mockRestore();
  });
});
