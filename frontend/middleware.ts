import { NextRequest, NextResponse } from "next/server";
import { BUSINESS_TOKEN_COOKIE, PLATFORM_TOKEN_COOKIE } from "@/lib/cookies";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8090";

// Every request the browser makes to /api/* or /uploads/* looks same-origin
// to it — this middleware is what actually forwards it to the real backend,
// swapping the httpOnly cookie for the Authorization header the backend expects.
// Login/logout/register live under /session/* instead, deliberately outside
// this matcher, so they're handled by their own route handlers rather than
// being proxied straight through.
export function middleware(request: NextRequest) {
  const { pathname, search } = request.nextUrl;

  const isPlatformRoute = pathname.startsWith("/api/platform/");
  const cookieName = isPlatformRoute ? PLATFORM_TOKEN_COOKIE : BUSINESS_TOKEN_COOKIE;
  const token = request.cookies.get(cookieName)?.value;

  const destination = new URL(pathname + search, API_BASE);

  const headers = new Headers(request.headers);
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  // The Host header should reflect the backend, not the Next.js app, once forwarded.
  headers.set("host", destination.host);

  return NextResponse.rewrite(destination, { request: { headers } });
}

export const config = {
  matcher: ["/api/:path*", "/uploads/:path*"],
};
