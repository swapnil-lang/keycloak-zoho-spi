# Contributing

Thank you for considering contributing to keycloak-zoho-spi!

## How to contribute

1. **Fork** the repository and create a feature branch from `main`.
2. **Make changes** following the coding conventions below.
3. **Test** your changes against a real Keycloak instance (see Testing below).
4. **Open a pull request** with a clear description of the change and why it is needed.

## Coding conventions

- Follow the existing code style (4-space indentation, Javadoc on public methods).
- All Keycloak dependencies must use `provided` scope in `pom.xml` — never bundle them.
- Use `getJsonProperty(node, field)` (inherited from `AbstractIdentityProvider`) for
  null-safe JSON field access — never call `node.get(field).asText()` directly.
- The federated identity ID must always be `ZAID`, not email.

## Building

```bash
mvn clean package
```

The output JAR is `target/keycloak-zoho-spi-<version>.jar`.

## Testing locally

1. Start Keycloak in dev mode:
   ```bash
   # Docker
   docker run -p 8080:8080 \
     -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin \
     -v $(pwd)/target/keycloak-zoho-spi-1.0.0.jar:/opt/keycloak/providers/keycloak-zoho-spi.jar \
     quay.io/keycloak/keycloak:26.0.5 start-dev
   ```
2. Open http://localhost:8080/admin, create a realm, and add the Zoho identity provider.
3. Register your Keycloak redirect URI in the [Zoho API Console](https://api-console.zoho.com/).
4. Test the full login flow.

## Reporting bugs

Please open a GitHub issue with:
- Keycloak version
- SPI version
- Steps to reproduce
- Relevant Keycloak server logs
