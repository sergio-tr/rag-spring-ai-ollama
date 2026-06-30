import { describe, expect, it } from "vitest";
import { dedupeChatSourcesForDisplay } from "./dedupe-chat-sources";

describe("dedupeChatSourcesForDisplay @SourceDedup", () => {
  it("groups duplicate filenames into one principal source", () => {
    const groups = dedupeChatSourcesForDisplay([
      { filename: "acta.pdf", chunkIndex: 1, distance: 0.1 },
      { filename: "acta.pdf", chunkIndex: 2, distance: 0.2 },
      { filename: "other.pdf", chunkIndex: 0, distance: 0.3 },
    ]);
    expect(groups).toHaveLength(2);
    const acta = groups.find((g) => g.displayName === "acta.pdf");
    expect(acta?.chunks).toHaveLength(2);
  });

  it("keys groups by document id when available", () => {
    const groups = dedupeChatSourcesForDisplay([
      { documentId: "d1", filename: "a.pdf", chunkIndex: 1 },
      { documentId: "d1", filename: "a.pdf", chunkIndex: 2 },
    ]);
    expect(groups).toHaveLength(1);
    expect(groups[0].key).toBe("d1");
  });
});
