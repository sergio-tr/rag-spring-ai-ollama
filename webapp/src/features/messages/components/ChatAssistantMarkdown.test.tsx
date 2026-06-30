import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ChatAssistantMarkdown } from "./ChatAssistantMarkdown";

describe("ChatAssistantMarkdown @ChatMarkdown", () => {
  it("renders bullet list markdown", () => {
    render(<ChatAssistantMarkdown content={"- one\n- two"} />);
    const root = screen.getByTestId("chat-assistant-markdown");
    expect(root.querySelector("ul li")).toBeTruthy();
    expect(root).toHaveTextContent("one");
    expect(root).toHaveTextContent("two");
  });

  it("renders fenced code block", () => {
    render(<ChatAssistantMarkdown content={"```js\nconst x = 1;\n```"} />);
    expect(screen.getByTestId("chat-assistant-markdown")).toHaveTextContent("const x = 1;");
  });

  it("renders simple table", () => {
    render(<ChatAssistantMarkdown content={"| a | b |\n| --- | --- |\n| 1 | 2 |"} />);
    const root = screen.getByTestId("chat-assistant-markdown");
    expect(root.querySelector("table")).toBeTruthy();
    expect(root).toHaveTextContent("1");
  });

  it("does not render unsafe HTML", () => {
    render(<ChatAssistantMarkdown content={'Hello <script>alert("x")</script> world'} />);
    const root = screen.getByTestId("chat-assistant-markdown");
    expect(root.querySelector("script")).toBeNull();
    expect(root).toHaveTextContent("Hello");
    expect(root).toHaveTextContent("world");
  });

  it("wraps long lines without breaking layout class hooks", () => {
    render(<ChatAssistantMarkdown content={"x".repeat(400)} />);
    expect(screen.getByTestId("chat-assistant-markdown")).toHaveClass("break-words");
  });
});
