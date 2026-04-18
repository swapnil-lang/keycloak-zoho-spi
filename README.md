# keycloak-zoho-spi

A Keycloak Social Identity Provider SPI that adds **Zoho** as a sign-in option,
exactly like the built-in GitHub, Google, and LinkedIn providers.

Supports all **7 Zoho data centers** (US, EU, IN, AU, CN, JP, CA) with a simple
dropdown in the Keycloak Admin UI.

---

## Compatibility

| SPI Version | Keycloak Version | Java |
|---|---|---|
| 1.0.x | 26.0.x | 17+ |

> **Note:** Keycloak 26 is Quarkus-based. The legacy WildFly/JBoss deployment
> model (Keycloak < 17) is not supported.

---

## Quick Start

### 1. Register your application in the Zoho API Console

1. Go to [https://api-console.zoho.com/](https://api-console.zoho.com/) and sign in.
2. Click **Add Client** → select **Server-based Applications**.
3. Fill in the details:
   - **Client Name**: your application name (e.g., "My App – Keycloak")
   - **Homepage URL**: your Keycloak base URL (e.g., `https://auth.example.com`)
   - **Authorized Redirect URIs**:
     ```
     https://{keycloak-host}/realms/{realm-name}/broker/zoho/endpoint
     ```
     Replace `{keycloak-host}` with your Keycloak domain and `{realm-name}` with
     your realm. If you give the identity provider a custom alias in Keycloak,
     use that alias instead of `zoho`.
4. Click **Create** and note the **Client ID** and **Client Secret**.
5. Under **Scopes**, add: `AaaServer.profile.Read`

> **Multi-DC tip:** If your users belong to multiple Zoho data centers (e.g., some
> in the US and some in the EU), register your client as a **Multi DC** app in the
> Zoho API Console. A single-DC client only authenticates users from its own region.

### 2. Build the JAR

```bash
git clone https://github.com/swapnil-lang/keycloak-zoho-spi.git
cd keycloak-zoho-spi
mvn clean package
# Output: target/keycloak-zoho-spi-1.0.0.jar
```

Or download the pre-built JAR from the [Releases](../../releases) page.

### 3. Deploy to Keycloak

Copy the JAR into Keycloak's `providers/` directory:

```bash
cp target/keycloak-zoho-spi-1.0.0.jar /opt/keycloak/providers/
```

Then start (or restart) Keycloak. For **production optimized** mode, run a build step first:

```bash
# Optimized mode (recommended for production)
/opt/keycloak/bin/kc.sh build
/opt/keycloak/bin/kc.sh start --optimized

# Or for development (auto-detects new providers, no build step needed)
/opt/keycloak/bin/kc.sh start-dev
```

#### Docker

```bash
docker run -p 8080:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  -v /path/to/keycloak-zoho-spi-1.0.0.jar:/opt/keycloak/providers/keycloak-zoho-spi.jar \
  quay.io/keycloak/keycloak:26.0.5 start-dev
```

### 4. Configure in Keycloak Admin UI

1. Log in to the Keycloak Admin Console.
2. Select your realm → **Identity Providers** → **Add provider**.
3. Choose **Zoho** from the list.
4. Fill in:
   - **Client ID**: from the Zoho API Console
   - **Client Secret**: from the Zoho API Console
   - **Data Center Domain**: select the region matching your Zoho account
     (default: `accounts.zoho.com` for the US)
5. Click **Save**.

Your realm's login page now shows a **Sign in with Zoho** button.

---

## Data Centers

Zoho operates independent infrastructure per region. Select the domain
matching your Zoho organization's region:

| Region | Domain |
|---|---|
| US (default) | `accounts.zoho.com` |
| EU | `accounts.zoho.eu` |
| IN | `accounts.zoho.in` |
| AU | `accounts.zoho.com.au` |
| CN | `accounts.zoho.com.cn` |
| JP | `accounts.zoho.jp` |
| CA | `accounts.zohocloud.ca` |

---

## User Profile Mapping

The following Zoho profile fields are automatically mapped to the Keycloak user:

| Zoho Field | Keycloak Attribute |
|---|---|
| `ZUID` | Federated identity ID (internal, stable) |
| `Email` | Email + Username |
| `First_Name` | First name |
| `Last_Name` | Last name |

### Custom attribute mapping

Additional Zoho profile fields (e.g., `ZUID`, `Display_Name`) can be mapped to
Keycloak user attributes via the **Mappers** tab on the Zoho identity provider:

1. Open the Zoho identity provider → **Mappers** tab → **Add Mapper**.
2. Select type **Zoho User Attribute Mapper**.
3. Set **JSON Field Path** to the Zoho field name (e.g., `ZUID`).
4. Set **User Attribute** to your desired Keycloak attribute name (e.g., `zoho_id`).
5. Save.

---

## Custom Themes

This SPI bundles a minimal override of the built-in `keycloak` login theme that
adds the Zoho icon to the social login button.

**If you use a custom login theme**, you must add the icon configuration manually
to avoid theme conflicts:

1. Add to your theme's `theme.properties`:
   ```properties
   kcLogoIdP-zoho=zoho-idp-icon
   ```
   Then **append** `css/zoho-idp.css` to your existing `styles` line (do not replace it):
   ```properties
   styles=css/login.css ... css/zoho-idp.css
   ```
   > **Warning:** Do not set `styles=css/login.css css/zoho-idp.css` as a standalone line —
   > this replaces your entire stylesheet list and will break your theme.

2. Copy `src/main/resources/theme/keycloak/login/resources/css/zoho-idp.css`
   into your theme's `resources/css/` directory.

3. Copy `src/main/resources/theme/keycloak/login/resources/img/zoho-logo.svg`
   into your theme's `resources/img/` directory.

> For official Zoho brand assets, download from
> [https://www.zoho.com/branding/](https://www.zoho.com/branding/).

---

## Building from Source

```bash
# Build
mvn clean package

# Build and run tests
mvn clean verify

# Skip tests (faster)
mvn clean package -DskipTests
```

---

## How It Works

This SPI implements Keycloak's `SocialIdentityProviderFactory` interface using
the standard OAuth 2.0 authorization code flow:

```
User → Keycloak login page
     → (clicks "Sign in with Zoho")
     → https://{dc}/oauth/v2/auth   (Zoho authorization)
     → (user authenticates with Zoho)
     → https://{keycloak}/realms/{realm}/broker/zoho/endpoint  (callback)
     → https://{dc}/oauth/v2/token  (Keycloak exchanges code for token)
     → https://{dc}/oauth/user/info (Keycloak fetches profile)
     → Keycloak creates or links the user account
```

Key implementation files:

| File | Purpose |
|---|---|
| [ZohoIdentityProvider.java](src/main/java/org/keycloak/social/zoho/ZohoIdentityProvider.java) | OAuth 2.0 flow + profile mapping |
| [ZohoIdentityProviderConfig.java](src/main/java/org/keycloak/social/zoho/ZohoIdentityProviderConfig.java) | DC domain config property |
| [ZohoIdentityProviderFactory.java](src/main/java/org/keycloak/social/zoho/ZohoIdentityProviderFactory.java) | Keycloak SPI registration + Admin UI fields |
| [ZohoUserAttributeMapper.java](src/main/java/org/keycloak/social/zoho/ZohoUserAttributeMapper.java) | Custom attribute mapper |

---

## Zoho API References

- [Zoho OAuth 2.0 Documentation](https://www.zoho.com/accounts/protocol/oauth.html)
- [Authorization Code Flow](https://www.zoho.com/accounts/protocol/oauth/web-apps/authorization.html)
- [Scope Reference](https://www.zoho.com/accounts/protocol/oauth/scope.html)
- [Zoho API Console](https://api-console.zoho.com/)

---

## License

Apache License 2.0 — see [LICENSE](LICENSE).

This project is not affiliated with, endorsed by, or sponsored by Zoho Corporation
or Red Hat / Keycloak.
