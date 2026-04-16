# Keycloak Zoho Social Identity Provider SPI

## Context

The user wants to create an open-source Keycloak SPI (Service Provider Interface) that adds Zoho as a social login option — exactly like the built-in GitHub/Google/LinkedIn providers — and publish it for the community.

The project directory `e:/Claude/keycloak-zoho-spi` is completely empty. This is a greenfield Maven project targeting **Keycloak 26.0.5** (Quarkus-based, Java 17).

---

## Project Structure

```
e:/Claude/keycloak-zoho-spi/
├── pom.xml
├── README.md
├── LICENSE                             (Apache 2.0)
├── .gitignore
├── CONTRIBUTING.md
└── src/main/
    ├── java/org/keycloak/social/zoho/
    │   ├── ZohoIdentityProviderConfig.java
    │   ├── ZohoIdentityProvider.java
    │   ├── ZohoIdentityProviderFactory.java
    │   └── ZohoUserAttributeMapper.java
    └── resources/
        ├── META-INF/services/
        │   ├── org.keycloak.broker.social.SocialIdentityProviderFactory
        │   └── org.keycloak.broker.provider.IdentityProviderMapper
        └── theme/keycloak/login/
            ├── theme.properties
            └── resources/
                ├── css/zoho-idp.css
                └── img/zoho-logo.svg
```

---

## Implementation Plan

### Step 1 — `pom.xml`

Maven build, Java 17, Keycloak 26.0.5. All Keycloak deps are `provided` scope (never bundled — Keycloak supplies them at runtime).

Key deps:
- `org.keycloak:keycloak-core` (provided)
- `org.keycloak:keycloak-server-spi` (provided)
- `org.keycloak:keycloak-server-spi-private` (provided) — contains `AbstractOAuth2IdentityProvider`, `AbstractIdentityProviderFactory`
- `org.keycloak:keycloak-services` (provided) — contains `AbstractJsonUserAttributeMapper`
- `com.fasterxml.jackson.core:jackson-databind` (provided)
- `jakarta.ws.rs:jakarta.ws.rs-api` (provided)
- `org.junit.jupiter:junit-jupiter:5.10.1` (test)

Managed via `org.keycloak:keycloak-parent:26.0.5` BOM import.

---

### Step 2 — `ZohoIdentityProviderConfig.java`

Extends `OAuth2IdentityProviderConfig` to add one custom property: `zohoDataCenterDomain`.

```java
package org.keycloak.social.zoho;
// extends OAuth2IdentityProviderConfig

static final String DATA_CENTER_DOMAIN_KEY = "zohoDataCenterDomain";
static final String DEFAULT_DC_DOMAIN      = "accounts.zoho.com";

// Getters/setters backed by getConfig().get/put(key)
// Convenience URL builders:
getZohoAuthorizationUrl() -> "https://{dc}/oauth/v2/auth"
getZohoTokenUrl()         -> "https://{dc}/oauth/v2/token"
getZohoUserInfoUrl()      -> "https://{dc}/oauth/user/info"
```

**Why a subclass:** custom property `zohoDataCenterDomain` must survive round-trips through Keycloak's DB. The factory's `createConfig()` must return this type (not the base) so Keycloak marshals it correctly.

---

### Step 3 — `ZohoIdentityProvider.java`

```java
public class ZohoIdentityProvider
    extends AbstractOAuth2IdentityProvider<ZohoIdentityProviderConfig>
    implements SocialIdentityProvider<ZohoIdentityProviderConfig>
```

**Constructor:** wires DC-aware URLs into parent fields:
```java
config.setAuthorizationUrl(config.getZohoAuthorizationUrl());
config.setTokenUrl(config.getZohoTokenUrl());
config.setUserInfoUrl(config.getZohoUserInfoUrl());
```

**`buildUserInfoRequest()`** — override to send `Zoho-oauthtoken {token}` header (Zoho's native auth scheme) instead of the parent's default `Bearer`:
```java
return SimpleHttp.doGet(userInfoUrl, session)
    .header("Authorization", "Zoho-oauthtoken " + subjectToken);
```

**`doGetFederatedIdentity()`** — calls `getZohoUserInfoUrl()`, parses JSON, delegates to `extractIdentityFromProfile()`.

**`extractIdentityFromProfile()`** — maps Zoho JSON fields:
| Zoho JSON field | Mapped to |
|---|---|
| `ZAID` | federated identity ID (stable unique ID — NOT email) |
| `Email` | `setEmail()`, `setUsername()` (fallback: `Display_Name`, then `ZAID`) |
| `First_Name` | `setFirstName()` |
| `Last_Name`  | `setLastName()` |

Also calls `AbstractJsonUserAttributeMapper.storeUserProfileForMapper(user, profile, alias)` so all Zoho fields are available in the Admin UI attribute mapper.

**`getDefaultScopes()`** → `"AaaServer.profile.Read"`

---

### Step 4 — `ZohoIdentityProviderFactory.java`

```java
public class ZohoIdentityProviderFactory
    extends AbstractIdentityProviderFactory<ZohoIdentityProvider>
    implements SocialIdentityProviderFactory<ZohoIdentityProvider>

PROVIDER_ID = "zoho"
getName()   → "Zoho"
getId()     → "zoho"
create()    → new ZohoIdentityProvider(session, new ZohoIdentityProviderConfig(model))
createConfig() → new ZohoIdentityProviderConfig()  // MUST return subtype
```

**`getConfigProperties()`** — declares one extra field for the Admin UI:
- Name: `zohoDataCenterDomain`
- Type: `LIST_TYPE`
- Options: 7 DC domains (US/EU/IN/AU/CN/JP/CA)
- Default: `accounts.zoho.com`

---

### Step 5 — `ZohoUserAttributeMapper.java`

```java
public class ZohoUserAttributeMapper extends AbstractJsonUserAttributeMapper
    getId()                  → "zoho-user-attribute-mapper"
    getCompatibleProviders() → ["zoho"]
```

Enables admins to map any Zoho JSON field (e.g., `ZAID`) to a Keycloak user attribute via the "Mappers" tab in Admin UI.

---

### Step 6 — SPI Service Descriptor Files

`src/main/resources/META-INF/services/org.keycloak.broker.social.SocialIdentityProviderFactory`:
```
org.keycloak.social.zoho.ZohoIdentityProviderFactory
```

`src/main/resources/META-INF/services/org.keycloak.broker.provider.IdentityProviderMapper`:
```
org.keycloak.social.zoho.ZohoUserAttributeMapper
```

---

### Step 7 — Theme Resources (Login Button Icon)

`theme/keycloak/login/theme.properties`:
```properties
parent=keycloak
import=common/keycloak
kcLogoIdP-zoho=zoho-idp-icon
```

`theme/keycloak/login/resources/css/zoho-idp.css`:
```css
.zoho-idp-icon {
    display: inline-block;
    width: 24px; height: 24px;
    background-image: url('../img/zoho-logo.svg');
    background-size: contain;
    background-repeat: no-repeat;
    background-position: center;
}
```

`zoho-logo.svg` — Zoho "Z" logo SVG (open brand asset).

README notes that users with custom themes must add the `kcLogoIdP-zoho` line to their own `theme.properties`.

---

### Step 8 — Open Source Files

- `README.md` — build/deploy steps, Zoho API Console registration, redirect URI format (`{keycloak}/realms/{realm}/broker/zoho/endpoint`), multi-DC guide
- `LICENSE` — Apache 2.0
- `.gitignore` — Maven standard
- `CONTRIBUTING.md` — brief contribution guide

---

## Key Gotchas

| # | Gotcha | Fix |
|---|---|---|
| 1 | `createConfig()` must return `ZohoIdentityProviderConfig` | Already accounted for |
| 2 | `user.setIdp(this)` is mandatory or callback NPEs | Always call it |
| 3 | Use `ZAID` (not email) as federated identity ID | ZAID is stable; email is mutable |
| 4 | `getJsonProperty()` from grandparent returns null-safe | Don't use `profile.get("x").asText()` directly |
| 5 | `kc.sh build` required after JAR deploy in optimized mode | Document in README |
| 6 | PKCE disabled by default (Zoho doesn't document support) | Don't enable; admin can override |
| 7 | Zoho requires Multi DC client registration in API Console | Document in README |
| 8 | Theme override may conflict with custom themes | Document workaround in README |

---

## Verification (End-to-End Test)

1. `mvn clean package` → `target/keycloak-zoho-spi-1.0.0.jar` (no errors)
2. Copy JAR to `$KEYCLOAK_HOME/providers/`
3. Run `bin/kc.sh start --dev`
4. Admin UI → Identity Providers → Add → "Zoho" appears in the list
5. Configure with Zoho API Console Client ID/Secret, select DC domain
6. Open realm login page → Zoho button appears with icon
7. Click → redirect to `accounts.zoho.com/oauth/v2/auth`
8. Authenticate → redirect back → Keycloak creates user
9. Verify user: email, firstName, lastName populated from Zoho profile
10. Verify "Add Mapper" shows `zoho-user-attribute-mapper` for ZAID etc.
