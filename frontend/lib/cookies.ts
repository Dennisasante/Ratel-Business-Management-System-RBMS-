// Names of the httpOnly cookies that hold the raw JWTs. These are never
// readable by client JS — only middleware.ts (server-side, at the edge) and
// the /session/* route handlers (server-side, in Node) ever touch them.
export const BUSINESS_TOKEN_COOKIE = "rbms_token";
export const PLATFORM_TOKEN_COOKIE = "rbms_platform_token";

// Matches the backend's default JWT_EXPIRATION_MS (24h). Keep these in sync —
// if you change JWT_EXPIRATION_MS on the backend, update this too, or the
// cookie could outlive (or expire well before) the token it holds.
export const COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24;
