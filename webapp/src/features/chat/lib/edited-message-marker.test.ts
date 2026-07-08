import { afterEach, describe, expect, it } from "vitest";
import {
  clearEditedMessageMarkers,
  isUserMessageMarkedEdited,
  markUserMessageEdited,
  readEditedUserMessageIds,
} from "./edited-message-marker";

const CONV = "33333333-3333-4333-8333-333333333333";

describe("edited-message-marker @ChatEditing", () => {
  afterEach(() => {
    clearEditedMessageMarkers(CONV);
  });

  it("marks and reads edited user messages", () => {
    markUserMessageEdited(CONV, "u1");
    expect(isUserMessageMarkedEdited(CONV, "u1")).toBe(true);
    expect(readEditedUserMessageIds(CONV).has("u1")).toBe(true);
  });
});
