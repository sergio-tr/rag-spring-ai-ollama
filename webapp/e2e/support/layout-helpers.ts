import type { Page } from "@playwright/test";

export const LAYOUT_SMOKE_PROJECT_ID = "proj-layout-smoke";
export const LAYOUT_SMOKE_CONVERSATION_ID = "conv-layout-smoke";

export type AppScrollMetrics = {
  documentScrollable: boolean;
  bodyScrollable: boolean;
  mainScrollable: boolean;
  mainOverflowY: string;
  mainScrollMode: string | null;
  threadScrollable: boolean;
  voidBelowChatPx: number;
  voidBelowMainPx: number;
};

function isElementScrollable(el: Element | null): boolean {
  if (!el) return false;
  const node = el as HTMLElement;
  const style = getComputedStyle(node);
  const overflowY = style.overflowY;
  if (!/(auto|scroll|overlay)/.test(overflowY)) return false;
  return node.scrollHeight > node.clientHeight + 1;
}

/** Measures scroll owners and empty bands below chat or main content (1366×768 layout contract). */
export async function measureAppScrollOwners(page: Page): Promise<AppScrollMetrics> {
  return page.evaluate(() => {
    const doc = document.documentElement;
    const body = document.body;
    const main = document.querySelector('[data-testid="app-main-scroll"]');
    const chatPage = document.querySelector('[data-testid="chat-page"]');
    const thread = document.querySelector('[data-testid="chat-thread-dropzone"]');

    const isScrollable = (el: Element | null) => {
      if (!el) return false;
      const node = el as HTMLElement;
      const style = getComputedStyle(node);
      const overflowY = style.overflowY;
      if (!/(auto|scroll|overlay)/.test(overflowY)) return false;
      return node.scrollHeight > node.clientHeight + 1;
    };

    let voidBelowChatPx = 0;
    if (chatPage && main) {
      const chatRect = chatPage.getBoundingClientRect();
      const mainRect = (main as HTMLElement).getBoundingClientRect();
      voidBelowChatPx = Math.max(0, Math.round(mainRect.bottom - chatRect.bottom));
    }

    let voidBelowMainPx = 0;
    if (main) {
      const mainRect = (main as HTMLElement).getBoundingClientRect();
      const viewportBottom = window.innerHeight;
      voidBelowMainPx = Math.max(0, Math.round(viewportBottom - mainRect.bottom));
    }

    const mainStyle = main ? getComputedStyle(main as Element) : null;

    return {
      documentScrollable: doc.scrollHeight > doc.clientHeight + 1,
      bodyScrollable: body.scrollHeight > body.clientHeight + 1,
      mainScrollable: isScrollable(main),
      mainOverflowY: mainStyle?.overflowY ?? "",
      mainScrollMode: main?.getAttribute("data-scroll-mode") ?? null,
      threadScrollable: isScrollable(thread),
      voidBelowChatPx,
      voidBelowMainPx,
    };
  });
}
