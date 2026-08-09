/** @type {import('next').NextConfig} */
// Proxying /api/* and /uploads/* to the backend now happens in middleware.ts
// instead of here — rewrites alone can't inject the Authorization header
// from the httpOnly cookie, which is the whole point of this file existing.
const nextConfig = {
  // Traces the minimal server + dependency set into .next/standalone, so the
  // production Docker image doesn't need the full node_modules tree.
  output: "standalone",
};

module.exports = nextConfig;
