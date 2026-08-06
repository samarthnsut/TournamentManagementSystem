/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  output: 'export',
  basePath: '/TournamentManagementSystem',
  images: {
    unoptimized: true,
  },
  // `next dev` and `next build` both write to .next by default, so running a build while the dev
  // server is up replaces the dev output underneath it. The dev server keeps serving chunk paths
  // that no longer exist, and the page loads with no CSS until it is restarted. The `dev` script
  // sets NEXT_DIST_DIR so the two never share a directory.
  distDir: process.env.NEXT_DIST_DIR || '.next',
}

module.exports = nextConfig
