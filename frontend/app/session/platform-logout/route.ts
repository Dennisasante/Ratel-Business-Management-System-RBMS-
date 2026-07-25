import { NextResponse } from "next/server";
import { PLATFORM_TOKEN_COOKIE } from "@/lib/cookies";

export async function POST() {
  const response = NextResponse.json({ ok: true });
  response.cookies.set(PLATFORM_TOKEN_COOKIE, "", {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: 0,
  });
  return response;
}
