"use client";

import { useProjectCreateFeedbackStore } from "@/features/projects/lib/project-create-feedback-state";

/** Global banner for non-fatal project creation warnings. */
export function ProjectCreateFeedbackBanner() {
  const feedback = useProjectCreateFeedbackStore((s) => s.feedback);

  if (!feedback) {
    return null;
  }

  return (
    <div className="mb-4 flex flex-col gap-2" data-testid="project-create-feedback">
      <output className="block text-sm text-amber-700 dark:text-amber-400">{feedback.warning}</output>
    </div>
  );
}
