import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

const withNextIntl = createNextIntlPlugin("./src/i18n/request.ts");

const nextConfig: NextConfig = {
  // Ensures next-intl time zone is never missing at build time (Docker/CI without .env).
  env: {
    NEXT_PUBLIC_TIMEZONE: process.env.NEXT_PUBLIC_TIMEZONE ?? "UTC",
    // Allow CI-like containers to configure the backend origin without requiring a second env var name.
    NEXT_PUBLIC_API_BASE_URL:
      process.env.NEXT_PUBLIC_API_BASE_URL ??
      process.env.API_BASE_URL ??
      process.env.INTEGRATION_BACKEND_URL ??
      "http://localhost:9000",
  },
};

export default withNextIntl(nextConfig);
