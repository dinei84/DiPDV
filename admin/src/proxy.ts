import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";

const PUBLIC_PATHS = ["/", "/login"];

export function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;

  if (PUBLIC_PATHS.some((path) => pathname === path || pathname.startsWith(`${path}/`))) {
    return NextResponse.next();
  }

  const adminToken = request.cookies.get("dipdv_admin_token")?.value;
  if (adminToken) {
    return NextResponse.next();
  }

  const loginUrl = new URL("/login", request.url);
  const requestedPath = `${pathname}${search}`;

  if (requestedPath !== "/login") {
    loginUrl.searchParams.set("from", requestedPath);
  }

  return NextResponse.redirect(loginUrl);
}

export const config = {
  matcher: [
    "/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp|ico)$).*)",
  ],
};
