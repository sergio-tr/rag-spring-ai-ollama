"use client";

import * as React from "react";

import { cn } from "@/lib/utils";

function containsFormControlDirect(children: React.ReactNode): boolean {
  return React.Children.toArray(children).some((child) => {
    if (!React.isValidElement(child)) {
      return false;
    }
    if (typeof child.type === "string") {
      return child.type === "input" || child.type === "select" || child.type === "textarea";
    }
    return false;
  });
}

function Label({ className, children, htmlFor, ...rest }: React.ComponentProps<"label">) {
  const styles = cn(
    "flex items-center gap-2 text-sm leading-none font-medium select-none group-data-[disabled=true]:pointer-events-none group-data-[disabled=true]:opacity-50 peer-disabled:cursor-not-allowed peer-disabled:opacity-50",
    className,
  );
  const useLabel =
    (htmlFor != null && htmlFor !== "") || containsFormControlDirect(children);
  if (useLabel) {
    return (
      <label data-slot="label" className={styles} htmlFor={htmlFor} {...rest}>
        {children}
      </label>
    );
  }
  return (
    <span data-slot="label" className={styles} {...(rest as React.ComponentProps<"span">)}>
      {children}
    </span>
  );
}

export { Label };
