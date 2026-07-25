export interface DecodedGoogleCredential {
  email: string;
  name: string;
}

/**
 * Decodes the payload of a Google ID token purely for display purposes
 * (e.g. "Signed in as jane@shop.com"). This does NOT verify the signature —
 * that only matters for security, and the backend re-verifies the token
 * against Google's public keys before trusting it for anything real.
 */
export function decodeGoogleCredential(idToken: string): DecodedGoogleCredential | null {
  try {
    const payload = idToken.split(".")[1];
    const normalized = payload.replace(/-/g, "+").replace(/_/g, "/");
    const json = JSON.parse(atob(normalized));
    return { email: json.email, name: json.name ?? json.email };
  } catch {
    return null;
  }
}
