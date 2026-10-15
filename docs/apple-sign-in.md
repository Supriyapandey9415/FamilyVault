# Sign in with Apple configuration

The Apple authentication implementation is disabled by default. Existing local and Google authentication continue to work when Apple credentials are absent.

## Apple Developer setup

1. Enable **Sign in with Apple** on a primary App ID.
2. Create a Services ID for the FamilyVault website.
3. Register the public HTTPS domain and return URI.
4. Create a Sign in with Apple key and download its `.p8` file once.

Apple does not accept `localhost` or an IP address as a web return URI. For local development, use a stable HTTPS development domain or tunnel that is registered in the Apple Developer portal. The React application can remain on `http://localhost:5173`.

## Environment variables

```text
APPLE_SIGN_IN_ENABLED=true
APPLE_CLIENT_ID=com.familyvault.web
APPLE_TEAM_ID=YOUR_TEAM_ID
APPLE_KEY_ID=YOUR_KEY_ID
APPLE_PRIVATE_KEY_PATH=C:\\secure\\AuthKey_YOUR_KEY_ID.p8
APPLE_REDIRECT_URI=https://your-stable-dev-domain.example/apple/callback
```

Alternatively, `APPLE_PRIVATE_KEY_BASE64` may contain the Base64 encoding of the complete PEM `.p8` file. Never expose either private-key value to Vite or commit it to Git.

The configured return URI must exactly match the URI registered for the Services ID. The backend exchanges the one-time authorization code, validates Apple's signed identity token, consumes a one-time state/nonce session, and then issues the normal FamilyVault JWT.

## Private email relay

Users may select **Hide My Email**. Register the production outbound mail domain/address with Apple's Private Email Relay and configure SPF/DKIM so invitations and password-reset emails can reach relay addresses.
