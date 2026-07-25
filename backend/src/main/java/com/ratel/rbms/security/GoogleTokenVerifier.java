package com.ratel.rbms.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.ratel.rbms.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * Verifies the ID token the frontend gets back from Google Identity Services
 * (the "Sign in with Google" button). Verification checks the token's
 * signature against Google's public keys and confirms it was issued for our
 * own OAuth client ID — so a token from some other Google app can't be used
 * to log into RBMS.
 */
@Component
public class GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(@Value("${app.google.client-id}") String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    public GoogleUser verify(String idTokenString) {
        if (idTokenString == null || idTokenString.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Missing Google credential.");
        }

        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(idTokenString);
        } catch (GeneralSecurityException | java.io.IOException | IllegalArgumentException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Couldn't verify Google credential.");
        }

        if (idToken == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired Google credential.");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();

        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Google account email isn't verified.");
        }

        String email = payload.getEmail();
        String name = (String) payload.get("name");
        if (name == null || name.isBlank()) {
            name = email; // fallback if Google didn't return a display name
        }

        return new GoogleUser(email, name);
    }

    public record GoogleUser(String email, String name) {
    }
}
