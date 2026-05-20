"use client";

import { create } from "zustand";

const STORAGE_KEY = "chat-config-panel-open-v1";

type ChatConfigurationPanelState = {
  /** Desktop-only persistent open state. */
  open: boolean;
  hydrated: boolean;
  hydrateFromStorage: () => void;
  setOpen: (open: boolean) => void;
  toggle: () => void;
};

function readStoredOpen(): boolean {
  try {
    return window.localStorage.getItem(STORAGE_KEY) === "true";
  } catch {
    return false;
  }
}

function writeStoredOpen(open: boolean): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, open ? "true" : "false");
  } catch {
    // ignore
  }
}

export const useChatConfigurationPanelStore = create<ChatConfigurationPanelState>((set, get) => ({
  open: false,
  hydrated: false,
  hydrateFromStorage: () => {
    if (get().hydrated) return;
    const open = typeof window !== "undefined" ? readStoredOpen() : false;
    set({ open, hydrated: true });
  },
  setOpen: (open) => {
    set({ open });
    if (typeof window !== "undefined") writeStoredOpen(open);
  },
  toggle: () => {
    const next = !get().open;
    set({ open: next });
    if (typeof window !== "undefined") writeStoredOpen(next);
  },
}));

