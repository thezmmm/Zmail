import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Static export required for Tauri — no Node.js server in desktop mode
  output: "export",
  // Disable image optimization (not available in static export)
  images: { unoptimized: true },
  // Tauri handles its own protocol; disable trailing slash
  trailingSlash: false,
};

export default nextConfig;