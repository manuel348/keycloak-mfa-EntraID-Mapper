# Keycloak Entra ID MFA AuthnContext Mapper

This project adds two custom Keycloak providers for SAML clients:

- `Entra AuthnContext Login Response Mapper`
- `Set ACR Session Note`

Together they let Keycloak return an MFA `AuthnContextClassRef` for Microsoft Entra ID after a local OTP challenge succeeds.

## What It Does

The solution works in two steps:

1. During authentication, the custom authenticator writes a user session note such as `ACR=2`.
2. When Keycloak builds the SAML response, the custom protocol mapper reads that session note and rewrites the `AuthnStatement/AuthnContextClassRef`.

Default behavior:

- If MFA is detected, Keycloak returns `http://schemas.microsoft.com/claims/multipleauthn`
- If MFA is not detected, Keycloak returns `urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport`

## Requirements

- Java 21
- Maven 3.9+
- Keycloak 26.5.x

This project is currently built against Keycloak `26.5.3`.

## Project Providers

### Protocol Mapper

- Provider ID: `entra-authncontext-login-response-mapper`
- Display name in Keycloak: `Entra AuthnContext Login Response Mapper`

Mapper config fields:

- `session.note.name`
- `default.class.ref`
- `mfa.class.ref`
- `mfa.note.values`

Recommended values:

- `session.note.name = ACR`
- `default.class.ref = urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport`
- `mfa.class.ref = http://schemas.microsoft.com/claims/multipleauthn`
- `mfa.note.values = 2,mfa,multipleauthn`

### Authenticator

- Provider ID: `set-acr-session-note`
- Display name in Keycloak: `Set ACR Session Note`

Authenticator config fields:

- `session.note.name`
- `session.note.value`

Recommended values:

- `session.note.name = ACR`
- `session.note.value = 2`

## Build

From the project root:

```powershell
mvn clean package
```

The output JAR will be created in:

```text
target/entra-authncontext-mapper-1.0.0.jar
```

## Deploy To Keycloak

Copy the JAR to the Keycloak `providers` directory.

Example:

```powershell
copy target\entra-authncontext-mapper-1.0.0.jar C:\path\to\keycloak\providers\
```

Then rebuild and restart Keycloak.

Quarkus distribution examples:

```powershell
cd C:\path\to\keycloak\bin
.\kc.bat build
.\kc.bat start
```

If Keycloak is already optimized and running as a service/container, use the equivalent rebuild and restart process for that environment.

## Keycloak Configuration

## 1. Create Or Verify The SAML Client

Create the SAML client that Microsoft Entra ID will call.

Typical values:

- `Client ID`: `https://login.microsoftonline.com/<tenant-id>/`
- `Valid Redirect URI`: `https://login.microsoftonline.com/login.srf`
- `Master SAML Processing URL`: `https://login.microsoftonline.com/login.srf`
- `NameID format`: `persistent`
- `Force POST Binding`: enabled
- `Include AuthnStatement`: enabled

The client must be `protocol = saml`.

## 2. Add The Custom Protocol Mapper

Open:

- `Clients`
- your Entra SAML client
- `Client scopes` or `Mappers` depending on your Keycloak UI

Add mapper:

- Mapper type: `Entra AuthnContext Login Response Mapper`

Set these values:

- `Session note name`: `ACR`
- `Default AuthnContextClassRef`: `urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport`
- `MFA AuthnContextClassRef`: `http://schemas.microsoft.com/claims/multipleauthn`
- `MFA note values`: `2,mfa,multipleauthn`

## 3. Optional Attribute Mapper

If you also want the raw note value as a SAML attribute, add a standard Keycloak mapper:

- Mapper type: `User Session Note`
- Note: `ACR`
- Attribute name: `http://schemas.microsoft.com/claims/multipleauthn`

This is optional. It does not change the SAML `AuthnContext`. It only adds a SAML attribute.

## 4. Create The Authentication Flow

Create a custom browser flow for the client, for example:

- `browser-mfa-entra`

Recommended structure:

1. Username/password step
2. OTP step
3. `Set ACR Session Note` step

The important rule is:

- `Set ACR Session Note` must run after OTP succeeds

Example subflow:

1. `Username Password Form` = `REQUIRED`
2. `OTP Form` = `REQUIRED`
3. `Set ACR Session Note` = `REQUIRED`

Configure `Set ACR Session Note` with:

- `Session note name = ACR`
- `Session note value = 2`

## 5. Bind The Flow To The SAML Client

Open:

- `Clients`
- your Entra SAML client
- `Authentication flow overrides`

Set:

- `Browser Flow` = your custom flow

This ensures that requests sent to that specific SAML client use the MFA flow and write `ACR=2` before the SAML response is generated.

## End-To-End Result

After a successful login with OTP:

- the authenticator stores `ACR=2`
- the custom mapper reads `ACR`
- Keycloak rewrites the SAML `AuthnContextClassRef`

Expected result in the SAML response:

```xml
<saml:AuthnContext>
  <saml:AuthnContextClassRef>http://schemas.microsoft.com/claims/multipleauthn</saml:AuthnContextClassRef>
</saml:AuthnContext>
```

If the session note is missing or does not match an MFA value, the mapper uses:

```xml
<saml:AuthnContext>
  <saml:AuthnContextClassRef>urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport</saml:AuthnContextClassRef>
</saml:AuthnContext>
```

## Logging And Troubleshooting

This project includes runtime logging in both providers.

Useful log lines:

- `SetAcrSessionNoteAuthenticator stored session note...`
- `EntraAuthnContextMapper invoked...`
- `Rewrote AuthnContextClassRef...`

What each message means:

- `SetAcrSessionNoteAuthenticator stored session note...`
  Confirms that the authentication flow stored `ACR=2`.
- `EntraAuthnContextMapper invoked...`
  Confirms that the SAML login response mapper was executed for the client.
- `Rewrote AuthnContextClassRef...`
  Confirms that the outgoing SAML `AuthnStatement` was updated.

If you still get `unspecified`, check these points:

1. The latest JAR is copied into `providers`.
2. Keycloak was rebuilt after copying the JAR.
3. The client is using the correct custom browser flow.
4. OTP actually runs for that login.
5. The log contains both the authenticator message and the mapper message.

## Verify The Build Locally

Use:

```powershell
mvn clean compile
```

## Files Of Interest

- `src/main/java/com/example/keycloak/saml/EntraAuthnContextMapper.java`
- `src/main/java/com/example/keycloak/auth/SetAcrSessionNoteAuthenticator.java`
- `src/main/java/com/example/keycloak/auth/SetAcrSessionNoteAuthenticatorFactory.java`
- `src/main/resources/META-INF/services/org.keycloak.protocol.ProtocolMapper`
- `src/main/resources/META-INF/services/org.keycloak.authentication.AuthenticatorFactory`

## Notes

- This project is intended for Keycloak SAML clients used by Microsoft Entra ID.
- The custom protocol mapper changes the SAML `AuthnContext`, not just SAML attributes.
- The `User Session Note` mapper and the custom `AuthnContext` mapper are independent; you can use one or both.
