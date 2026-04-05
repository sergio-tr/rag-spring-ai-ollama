/**
 * Builds a W3C Trace Context traceparent header value for outbound API calls.
 * @see https://www.w3.org/TR/trace-context/
 */
export function createTraceparent(): string {
  const version = "00";
  const traceId = crypto.randomUUID().replace(/-/g, "");
  const parentId = crypto.randomUUID().replace(/-/g, "").slice(0, 16);
  const flags = "01";
  return `${version}-${traceId}-${parentId}-${flags}`;
}
