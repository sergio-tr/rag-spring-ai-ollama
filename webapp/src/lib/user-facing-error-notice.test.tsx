import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { UserFacingErrorNotice } from "./user-facing-error-notice";

const t = (key: string) => `i18n:${key}`;

describe("UserFacingErrorNotice", () => {
  it("keeps technical code in collapsed details only", () => {
    render(
      <UserFacingErrorNotice
        raw="NO_READY_DOCUMENTS"
        fallback="fb"
        t={t}
        testId="notice"
      />,
    );
    expect(screen.getByTestId("notice-primary")).toHaveTextContent("i18n:userError_NO_READY_DOCUMENTS");
    expect(screen.getByTestId("notice-primary")).not.toHaveTextContent(/^NO_READY_DOCUMENTS$/);
    const details = screen.getByTestId("user-facing-error-technical");
    expect(details).not.toHaveAttribute("open");
    expect(screen.getByTestId("user-facing-error-technical-code")).toHaveTextContent("NO_READY_DOCUMENTS");
  });
});
