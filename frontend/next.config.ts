import type { NextConfig } from "next";

const isProd = process.env.NODE_ENV === "production";

const nextConfig: NextConfig = {
  // Static export is only needed for Tauri desktop packaging (production build).
  // In dev mode, Tauri connects to the Next.js dev server via devUrl, so
  // output: 'export' must be off to allow dynamic routes to work normally.
  ...(isProd ? { output: "export" } : {}),
  // Image optimization is unavailable in static export mode
  images: { unoptimized: true },
  trailingSlash: false,
};

export default nextConfig;