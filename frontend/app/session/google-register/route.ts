import { NextRequest, NextResponse } from "next/server";
import { BUSINESS_TOKEN_COOKIE, COOKIE_MAX_AGE_SECONDS } from "@/lib/cookies";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8090";

export async function POST(request: NextRequest) {
  const body = await request.json();

  const res = await fetch(`${API_BASE}/api/auth/google/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

  const data = await res.json();

  if (!res.ok) {
    return NextResponse.json(data, { status: res.status });
  }

  const { token, ...clientSafeSession } = data;

  const response = NextResponse.json(clientSafeSession, { status: 201 });
  response.cookies.set(BUSINESS_TOKEN_COOKIE, token, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: COOKIE_MAX_AGE_SECONDS,
  });
  return response;
}
