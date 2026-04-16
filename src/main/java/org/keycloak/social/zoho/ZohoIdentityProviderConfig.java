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

import org.keycloak.broker.oidc.OAuth2IdentityProviderConfig;
import org.keycloak.models.IdentityProviderModel;

/**
 * Configuration for the Zoho Social Identity Provider.
 *
 * <p>Extends the standard {@link OAuth2IdentityProviderConfig} with a single
 * Zoho-specific property: the <em>data center domain</em>. Zoho operates
 * independent infrastructure in multiple geographic regions; each region has
 * its own accounts domain that must be used for the OAuth2 authorization,
 * token, and user-info endpoints.</p>
 *
 * <p>Supported data centers:</p>
 * <table>
 *   <tr><th>Region</th><th>Domain</th></tr>
 *   <tr><td>US (default)</td><td>{@code accounts.zoho.com}</td></tr>
 *   <tr><td>EU</td><td>{@code accounts.zoho.eu}</td></tr>
 *   <tr><td>IN</td><td>{@code accounts.zoho.in}</td></tr>
 *   <tr><td>AU</td><td>{@code accounts.zoho.com.au}</td></tr>
 *   <tr><td>CN</td><td>{@code accounts.zoho.com.cn}</td></tr>
 *   <tr><td>JP</td><td>{@code accounts.zoho.jp}</td></tr>
 *   <tr><td>CA</td><td>{@code accounts.zohocloud.ca}</td></tr>
 * </table>
 *
 * @see <a href="https://www.zoho.com/accounts/protocol/oauth.html">Zoho OAuth 2.0 Documentation</a>
 */
public class ZohoIdentityProviderConfig extends OAuth2IdentityProviderConfig {

    /**
     * Key under which the data-center domain is stored in the persisted
     * config map ({@code IdentityProviderModel#getConfig()}).
     */
    static final String DATA_CENTER_DOMAIN_KEY = "zohoDataCenterDomain";

    /**
     * Default domain used when no data-center domain has been configured.
     * Corresponds to the Zoho US data center.
     */
    static final String DEFAULT_DC_DOMAIN = "accounts.zoho.com";

    /**
     * No-arg constructor used by {@link ZohoIdentityProviderFactory#createConfig()}.
     * Keycloak calls this when initializing a new (unsaved) provider config
     * in the admin UI.
     */
    public ZohoIdentityProviderConfig() {
        super();
    }

    /**
     * Constructor used by {@link ZohoIdentityProviderFactory#create(org.keycloak.models.KeycloakSession, IdentityProviderModel)}.
     * Keycloak calls this when loading a previously saved provider config from the database,
     * wrapping the stored {@link IdentityProviderModel}.
     *
     * @param model the persisted identity provider model
     */
    public ZohoIdentityProviderConfig(IdentityProviderModel model) {
        super(model);
    }

    // -------------------------------------------------------------------------
    // Data-center domain property
    // -------------------------------------------------------------------------

    /**
     * Returns the configured Zoho accounts domain for the selected data center.
     * Falls back to {@link #DEFAULT_DC_DOMAIN} ({@code accounts.zoho.com}) if
     * the property has not been set.
     *
     * @return a Zoho accounts domain such as {@code accounts.zoho.eu}
     */
    public String getDataCenterDomain() {
        String domain = getConfig().get(DATA_CENTER_DOMAIN_KEY);
        return (domain == null || domain.isBlank()) ? DEFAULT_DC_DOMAIN : domain;
    }

    /**
     * Sets the Zoho accounts domain for the selected data center.
     *
     * @param domain a Zoho accounts domain such as {@code accounts.zoho.eu}
     */
    public void setDataCenterDomain(String domain) {
        getConfig().put(DATA_CENTER_DOMAIN_KEY, domain);
    }

    // -------------------------------------------------------------------------
    // Convenience URL builders — called from ZohoIdentityProvider constructor
    // -------------------------------------------------------------------------

    /**
     * Returns the OAuth2 authorization endpoint for the configured data center.
     *
     * @return e.g. {@code https://accounts.zoho.eu/oauth/v2/auth}
     */
    public String getZohoAuthorizationUrl() {
        return "https://" + getDataCenterDomain() + "/oauth/v2/auth";
    }

    /**
     * Returns the OAuth2 token endpoint for the configured data center.
     *
     * @return e.g. {@code https://accounts.zoho.eu/oauth/v2/token}
     */
    public String getZohoTokenUrl() {
        return "https://" + getDataCenterDomain() + "/oauth/v2/token";
    }

    /**
     * Returns the user-info endpoint for the configured data center.
     * This endpoint returns the authenticated user's Zoho profile fields.
     *
     * @return e.g. {@code https://accounts.zoho.eu/oauth/user/info}
     */
    public String getZohoUserInfoUrl() {
        return "https://" + getDataCenterDomain() + "/oauth/user/info";
    }
}
