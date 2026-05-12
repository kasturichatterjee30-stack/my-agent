package com.agent;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.CalendarScopes;

import java.util.List;

/**
 * One-time setup script to obtain a Google OAuth2 refresh token.
 *
 * Run with:
 *   export GOOGLE_CLIENT_ID=...
 *   export GOOGLE_CLIENT_SECRET=...
 *   mvn compile exec:java -Dexec.mainClass="com.agent.GoogleAuthSetup"
 *
 * Copy the printed refresh token into your .env file as GOOGLE_REFRESH_TOKEN.
 */
public class GoogleAuthSetup {

    private static final List<String> SCOPES = List.of(CalendarScopes.CALENDAR_READONLY);

    public static void main(String[] args) throws Exception {
        String clientId = System.getenv("GOOGLE_CLIENT_ID");
        String clientSecret = System.getenv("GOOGLE_CLIENT_SECRET");

        if (clientId == null || clientId.isBlank()) {
            System.err.println("Error: GOOGLE_CLIENT_ID environment variable is not set.");
            System.exit(1);
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            System.err.println("Error: GOOGLE_CLIENT_SECRET environment variable is not set.");
            System.exit(1);
        }

        var httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        var jsonFactory = GsonFactory.getDefaultInstance();

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, jsonFactory, clientId, clientSecret, SCOPES)
                .setAccessType("offline")   // required to receive a refresh token
                .build();

        // Starts a local server on port 8888 to receive the OAuth callback
        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                .setPort(8888)
                .build();

        System.out.println("\nOpening your browser for Google authorization...");
        System.out.println("If it doesn't open automatically, check your browser.\n");

        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

        String refreshToken = credential.getRefreshToken();
        if (refreshToken == null) {
            System.err.println("\nNo refresh token received. This can happen if you've authorized this");
            System.err.println("app before. To force a new refresh token:");
            System.err.println("  1. Go to https://myaccount.google.com/permissions");
            System.err.println("  2. Remove access for your app");
            System.err.println("  3. Run this script again.");
            System.exit(1);
        }

        System.out.println("========================================");
        System.out.println("SUCCESS! Add this to your .env file:");
        System.out.println();
        System.out.println("GOOGLE_REFRESH_TOKEN=" + refreshToken);
        System.out.println("========================================");
    }
}
