import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

const withNextIntl = createNextIntlPlugin("./src/i18n/request.ts");

const nextConfig: NextConfig = {
  // Ensures next-intl time zone is never missing at build time (Docker/CI without .env).
  env: {
    NEXT_PUBLIC_TIMEZONE: process.env.NEXT_PUBLIC_TIMEZONE ?? "UTC",
  },
};

export default withNextIntl(nextConfig);
