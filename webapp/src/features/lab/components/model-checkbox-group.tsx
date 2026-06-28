"use client";

export type ModelCheckboxGroupProps = Readonly<{
  id: string;
  label: string;
  availableModelIds: string[];
  selectedIds: string[];
  disabled?: boolean;
  onChange: (selectedIds: string[]) => void;
  testIdPrefix: string;
  hint?: string;
}>;

/**
 * Checkbox list for Lab model comparison (LLM or embedding tags).
 */
export function ModelCheckboxGroup({
  id,
  label,
  availableModelIds,
  selectedIds,
  disabled = false,
  onChange,
  testIdPrefix,
  hint,
}: ModelCheckboxGroupProps) {
  const staleIds = selectedIds.filter((mid) => mid.trim() !== "" && !availableModelIds.includes(mid.trim()));

  function toggle(modelId: string, checked: boolean) {
    const trimmed = modelId.trim();
    if (!trimmed) return;
    if (checked) {
      onChange(Array.from(new Set([...selectedIds, trimmed])));
      return;
    }
    onChange(selectedIds.filter((x) => x.trim() !== trimmed));
  }

  return (
    <fieldset
      id={id}
      className="m-0 min-w-0 space-y-2 border-0 p-0"
      data-testid={`${testIdPrefix}-group`}
    >
      <legend className="text-sm leading-none font-medium">{label}</legend>
      <div
        className="max-h-60 space-y-2 overflow-auto rounded-md border p-2"
        data-testid={`${testIdPrefix}-list`}
      >
        {staleIds.map((name) => (
          <label key={`stale-${name}`} className="flex items-center gap-2 rounded border px-2 py-1 text-sm opacity-60">
            <input type="checkbox" checked disabled data-testid={`${testIdPrefix}-stale-${name}`} readOnly />
            <span>{name}</span>
            <span className="text-muted-foreground text-xs">(unavailable)</span>
          </label>
        ))}
        {availableModelIds.map((name) => {
          const checked = selectedIds.includes(name);
          return (
            <label key={name} className="flex items-center gap-2 rounded border px-2 py-1 text-sm">
              <input
                type="checkbox"
                data-testid={`${testIdPrefix}-${name}`}
                disabled={disabled}
                checked={checked}
                onChange={(e) => toggle(name, e.target.checked)}
              />
              <span>{name}</span>
            </label>
          );
        })}
        {availableModelIds.length === 0 && staleIds.length === 0 ? (
          <p className="text-muted-foreground text-xs">No models available.</p>
        ) : null}
      </div>
      {hint ? <p className="text-muted-foreground text-xs">{hint}</p> : null}
    </fieldset>
  );
}
