import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { PasswordVisibilityToggle } from "./PasswordVisibilityToggle";

describe("PasswordVisibilityToggle", () => {
  it("reflects visibility with aria-pressed and calls onToggle", async () => {
    const onToggle = vi.fn();
    const user = userEvent.setup();
    const { rerender } = render(
      <PasswordVisibilityToggle
        visible={false}
        onToggle={onToggle}
        showPasswordLabel="Show password"
        hidePasswordLabel="Hide password"
      />,
    );

    const btn = screen.getByRole("button", { name: /show password/i });
    expect(btn).toHaveAttribute("aria-pressed", "false");
    await user.click(btn);
    expect(onToggle).toHaveBeenCalledTimes(1);

    rerender(
      <PasswordVisibilityToggle
        visible
        onToggle={onToggle}
        showPasswordLabel="Show password"
        hidePasswordLabel="Hide password"
      />,
    );
    expect(screen.getByRole("button", { name: /hide password/i })).toHaveAttribute("aria-pressed", "true");
  });
});
