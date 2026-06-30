/**
 * Removes model hidden-reasoning blocks from assistant text before user-facing render.
 * Preserves plain text when no blocks are present.
 */
export function stripHiddenReasoningBlocks(content: string): string {
  if (!content) return content;
  const thinkOpen = "<" + "think" + ">";
  const thinkClose = "<" + "/" + "think" + ">";
  const thinkPattern = new RegExp(`${thinkOpen}[\\s\\S]*?${thinkClose}`, "gi");
  return content
    .replace(thinkPattern, "")
    .replace(/<reasoning>[\s\S]*?<\/reasoning>/gi, "")
    .trim();
}
