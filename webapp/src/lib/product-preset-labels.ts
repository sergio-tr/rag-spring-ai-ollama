/** Product-safe display names for seeded system presets (DB `name` stays internal). */
const SYSTEM_PRESET_DISPLAY: Record<string, string> = {
  demo_best: "Production assistant configuration",
  demo_worst: "Basic baseline configuration",
  demo_naivefullcorpus: "Full-context baseline",
};

export function toProductPresetDisplayName(name: string): string {
  const key = name.trim().toLowerCase();
  return SYSTEM_PRESET_DISPLAY[key] ?? name;
}
