/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.keycloak.social.zoho;

import com.fasterxml.jackson.databind.JsonNode;
import org.jboss.logging.Logger;
import org.keycloak.broker.oidc.AbstractOAuth2IdentityProvider;
import org.keycloak.broker.oidc.mappers.AbstractJsonUserAttributeMapper;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.broker.provider.IdentityBrokerException;
import org.keycloak.broker.provider.util.SimpleHttp;
import org.keycloak.broker.social.SocialIdentityProvider;
import org.keycloak.models.KeycloakSession;

import java.io.IOException;

/**
 * Keycloak Social Identity Provider for Zoho OAuth 2.0.
 *
 * <p>Implements the Zoho OAuth 2.0 authorization code flow and maps the
 * resulting user profile to a Keycloak {@link BrokeredIdentityContext}.
 * Supports all Zoho data centers (US, EU, IN, AU, CN, JP, CA) via the
 * {@code zohoDataCenterDomain} configuration property.</p>
 *
 * <h2>OAuth 2.0 Flow</h2>
 * <ol>
 *   <li>User clicks "Login with Zoho" on the Keycloak login page.</li>
 *   <li>Keycloak redirects to {@code https://{dc}/oauth/v2/auth}.</li>
 *   <li>User authenticates with Zoho and grants consent.</li>
 *   <li>Zoho redirects back to the Keycloak broker endpoint with an auth code.</li>
 *   <li>Keycloak exchanges the code for an access token at {@code https://{dc}/oauth/v2/token}.</li>
 *   <li>This class fetches the user profile from {@code https://{dc}/oauth/user/info}.</li>
 *   <li>The profile is mapped to a Keycloak user via {@link BrokeredIdentityContext}.</li>
 * </ol>
 *
 * <h2>Zoho User Profile Fields</h2>
 * <pre>
 * {
 *   "ZAID":         "12345678901",   // Zoho Account ID — stable unique identifier
 *   "Display_Name": "Jane Doe",
 *   "Email":        "jane@example.com",
 *   "First_Name":   "Jane",
 *   "Last_Name":    "Doe"
 * }
 * </pre>
 *
 * <h2>Required Zoho Scope</h2>
 * <p>{@code AaaServer.profile.Read} — grants access to the user info endpoint.</p>
 *
 * @see ZohoIdentityProviderConfig
 * @see ZohoIdentityProviderFactory
 * @see <a href="https://www.zoho.com/accounts/protocol/oauth.html">Zoho OAuth 2.0 Documentation</a>
 * @see <a href="https://www.zoho.com/accounts/protocol/oauth/web-apps/authorization.html">Authorization Code Flow</a>
 */
public class ZohoIdentityProvider
        extends AbstractOAuth2IdentityProvider<ZohoIdentityProviderConfig>
        implements SocialIdentityProvider<ZohoIdentityProviderConfig> {

    private static final Logger logger = Logger.getLogger(ZohoIdentityProvider.class);

    /**
     * The OAuth 2.0 scope required to read the Zoho user profile.
     * This is the minimum scope needed for social login.
     */
    public static final String DEFAULT_SCOPE = "AaaServer.profile.Read";

    // -------------------------------------------------------------------------
    // Zoho user-info response field names
    // -------------------------------------------------------------------------

    /** Zoho Account ID — a stable numeric string used as the federated identity ID. */
    public static final String FIELD_ZUID         = "ZUID";

    /** User's display name (may include middle names or honorifics). */
    public static final String FIELD_DISPLAY_NAME = "Display_Name";

    /** User's primary email address. */
    public static final String FIELD_EMAIL        = "Email";

    /** User's given (first) name. */
    public static final String FIELD_FIRST_NAME   = "First_Name";

    /** User's family (last) name. */
    public static final String FIELD_LAST_NAME    = "Last_Name";

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Creates a new Zoho identity provider instance.
     *
     * <p>Wires the data-center-aware endpoint URLs into the parent class fields
     * ({@code authorizationUrl}, {@code tokenUrl}, {@code userInfoUrl}) so that
     * the parent's standard OAuth 2.0 flow uses the correct regional endpoints.</p>
     *
     * @param session the current Keycloak session
     * @param config  the provider configuration (contains DC domain and OAuth credentials)
     */
    public ZohoIdentityProvider(KeycloakSession session, ZohoIdentityProviderConfig config) {
        super(session, config);
        // Wire DC-aware URLs into the parent's inherited config fields.
        // The parent's createAuthorizationUrl() and generateTokenRequest() read these.
        config.setAuthorizationUrl(config.getZohoAuthorizationUrl());
        config.setTokenUrl(config.getZohoTokenUrl());
        config.setUserInfoUrl(config.getZohoUserInfoUrl());
    }

    // -------------------------------------------------------------------------
    // HTTP: override Authorization header for Zoho's proprietary auth scheme
    // -------------------------------------------------------------------------

    /**
     * Builds the HTTP request used to fetch the Zoho user profile.
     *
     * <p>Zoho uses a proprietary authorization header scheme —
     * {@code Zoho-oauthtoken {token}} — rather than the standard
     * {@code Bearer {token}} used by most OAuth 2.0 providers. While Zoho
     * accepts both forms, using the native scheme is unambiguous and preferred
     * per Zoho's official API documentation.</p>
     *
     * @param subjectToken the access token obtained from the token endpoint
     * @param userInfoUrl  the full user-info URL for the configured data center
     * @return a configured {@link SimpleHttp} GET request
     */
    @Override
    protected SimpleHttp buildUserInfoRequest(String subjectToken, String userInfoUrl) {
        return SimpleHttp.doGet(userInfoUrl, session)
                .header("Authorization", "Zoho-oauthtoken " + subjectToken);
    }

    // -------------------------------------------------------------------------
    // Core: fetch and map the federated identity
    // -------------------------------------------------------------------------

    /**
     * Fetches the authenticated user's profile from Zoho and returns it as a
     * {@link BrokeredIdentityContext} that Keycloak uses to create or link the
     * user account.
     *
     * @param accessToken the OAuth 2.0 access token
     * @return the populated identity context
     * @throws IdentityBrokerException if the Zoho user-info request fails or
     *                                  returns an unexpected response
     */
    @Override
    protected BrokeredIdentityContext doGetFederatedIdentity(String accessToken) {
        logger.debugf("Fetching Zoho user profile from %s", getConfig().getZohoUserInfoUrl());
        try {
            String userInfoUrl = getConfig().getZohoUserInfoUrl();
            JsonNode profile = buildUserInfoRequest(accessToken, userInfoUrl).asJson();

            if (profile == null) {
                throw new IdentityBrokerException(
                        "No response received from Zoho user info endpoint: " + userInfoUrl);
            }

            logger.infof("Zoho user profile raw response: %s", profile.toString());
            logger.infof("Zoho user profile received for ZAID: %s",
                    profile.has(FIELD_ZUID) ? profile.get(FIELD_ZUID).asText() : "<missing>");

            return extractIdentityFromProfile(profile);

        } catch (IOException e) {
            throw new IdentityBrokerException(
                    "Could not obtain user profile from Zoho user info endpoint. " +
                    "Verify that the access token is valid and the scope " +
                    "'AaaServer.profile.Read' was granted. Error: " + e.getMessage(), e);
        }
    }

    /**
     * Maps Zoho user profile JSON fields to a {@link BrokeredIdentityContext}.
     *
     * <h3>Identity ID choice</h3>
     * <p>The {@code ZAID} (Zoho Account ID) is used as the federated identity ID —
     * the first argument to {@link BrokeredIdentityContext}. This value is stored in
     * Keycloak's {@code federated_identity} table and is used for all subsequent
     * account-linking lookups. It must be <em>stable</em>. Email addresses are
     * explicitly avoided as the identity ID because Zoho users can change their
     * email, which would create duplicate or orphaned Keycloak accounts.</p>
     *
     * <h3>Username selection</h3>
     * <p>Keycloak uses the username in many authentication flows and displays.
     * This implementation follows the Google and GitHub provider convention:
     * prefer email as the username (most recognizable to users), fall back to
     * {@code Display_Name}, then to {@code ZAID} if neither is available.</p>
     *
     * @param profile the JSON response from Zoho's {@code /oauth/user/info} endpoint
     * @return a fully populated {@link BrokeredIdentityContext}
     * @throws IdentityBrokerException if the required {@code ZAID} field is absent
     */
    private BrokeredIdentityContext extractIdentityFromProfile(JsonNode profile) {
        // ZAID is mandatory — it is the only stable, unique identifier Zoho provides.
        // getJsonProperty() (inherited from AbstractIdentityProvider) returns null
        // safely if the field is absent, rather than throwing NullPointerException.
        String zaid = getJsonProperty(profile, FIELD_ZUID);
        if (zaid == null || zaid.isBlank()) {
            throw new IdentityBrokerException(
                    "The 'ZUID' field is missing from the Zoho user info response. " +
                    "Ensure the OAuth scope 'AaaServer.profile.Read' is granted and " +
                    "that the Zoho application has the 'User Info' permission enabled " +
                    "in the Zoho API Console.");
        }

        BrokeredIdentityContext user = new BrokeredIdentityContext(zaid, getConfig());

        String email       = getJsonProperty(profile, FIELD_EMAIL);
        String displayName = getJsonProperty(profile, FIELD_DISPLAY_NAME);
        String firstName   = getJsonProperty(profile, FIELD_FIRST_NAME);
        String lastName    = getJsonProperty(profile, FIELD_LAST_NAME);

        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);

        // Username: email preferred, then display name, then ZAID as last resort.
        if (email != null && !email.isBlank()) {
            user.setUsername(email);
        } else if (displayName != null && !displayName.isBlank()) {
            user.setUsername(displayName);
        } else {
            user.setUsername(zaid);
        }

        // Store the raw profile so ZohoUserAttributeMapper (and any custom
        // JSON attribute mappers) can extract additional fields from the Admin UI.
        AbstractJsonUserAttributeMapper.storeUserProfileForMapper(
                user, profile, getConfig().getAlias());

        // Required: links this context back to the provider for callback routing.
        user.setIdp(this);

        return user;
    }

    // -------------------------------------------------------------------------
    // Default OAuth 2.0 scopes
    // -------------------------------------------------------------------------

    /**
     * Returns the default OAuth 2.0 scopes requested during authorization.
     *
     * <p>{@code AaaServer.profile.Read} is the minimum scope required to access
     * the {@code /oauth/user/info} endpoint. Administrators can override this
     * in the Keycloak Admin UI if additional Zoho scopes are needed.</p>
     *
     * @return the default scope string
     */
    @Override
    protected String getDefaultScopes() {
        return DEFAULT_SCOPE;
    }
}
