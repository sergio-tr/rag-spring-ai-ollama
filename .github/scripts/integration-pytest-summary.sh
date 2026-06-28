#!/usr/bin/env bash
# Print integration pytest outcomes to the job log and GitHub Actions step summary.
set -euo pipefail

LOG_PATH="${1:-}"
if [[ -z "${LOG_PATH}" || ! -f "${LOG_PATH}" ]]; then
  echo "integration-pytest-summary: no log file at '${LOG_PATH:-<unset>}'"
  exit 0
fi

SUMMARY_LINE="$(
  grep -E '=+ .* (passed|failed|skipped|error).*( in |s$)' "${LOG_PATH}" | tail -n 1 || true
)"
SKIPPED_LINES="$(
  grep -E '^SKIPPED \[| skipped ' "${LOG_PATH}" || true
)"

echo "### Integration pytest summary"
echo ""
if [[ -n "${SUMMARY_LINE}" ]]; then
  echo "**Result:** \`${SUMMARY_LINE}\`"
else
  echo "**Result:** _(could not parse summary line)_"
fi

if [[ -n "${SKIPPED_LINES}" ]]; then
  echo ""
  echo "**Skipped tests:**"
  echo '```'
  echo "${SKIPPED_LINES}" | head -n 40
  local_count="$(echo "${SKIPPED_LINES}" | wc -l | tr -d ' ')"
  if [[ "${local_count}" -gt 40 ]]; then
    echo "... (${local_count} skip lines total; truncated)"
  fi
  echo '```'
else
  echo ""
  echo "**Skipped tests:** none logged (or pytest -rs not used)."
fi

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo "### Integration pytest summary"
    echo ""
    if [[ -n "${SUMMARY_LINE}" ]]; then
      echo "**Result:** \`${SUMMARY_LINE}\`"
    fi
    if [[ -n "${SKIPPED_LINES}" ]]; then
      echo ""
      echo "<details><summary>Skipped tests (up to 40 lines)</summary>"
      echo ""
      echo '```'
      echo "${SKIPPED_LINES}" | head -n 40
      echo '```'
      echo "</details>"
    fi
  } >> "${GITHUB_STEP_SUMMARY}"
fi
