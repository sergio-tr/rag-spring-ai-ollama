const RESERVED_NAMES = new Set(["default", "null", "none", "undefined", "n/a"]);

export function normalizeRegisteredModelName(value: string): string {
  return value.trim();
}

export function isReservedRegisteredModelName(value: string): boolean {
  const normalized = normalizeRegisteredModelName(value).toLowerCase();
  return normalized.length === 0 || RESERVED_NAMES.has(normalized);
}

export function registeredModelNameError(value: string, maxLength = 80): string | null {
  const trimmed = normalizeRegisteredModelName(value);
  if (trimmed.length === 0) {
    return "required";
  }
  if (trimmed.length > maxLength) {
    return "tooLong";
  }
  if (isReservedRegisteredModelName(trimmed)) {
    return "reserved";
  }
  return null;
}
