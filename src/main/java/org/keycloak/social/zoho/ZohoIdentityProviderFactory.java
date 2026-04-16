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

import org.keycloak.broker.provider.AbstractIdentityProviderFactory;
import org.keycloak.broker.social.SocialIdentityProviderFactory;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

import java.util.List;

/**
 * Factory that registers the Zoho Social Identity Provider with Keycloak's SPI system.
 *
 * <p>This class is discovered by Keycloak at startup via the Java
 * {@link java.util.ServiceLoader} mechanism. The service descriptor file
 * {@code META-INF/services/org.keycloak.broker.social.SocialIdentityProviderFactory}
 * must contain the fully-qualified name of this class.</p>
 *
 * <p>The {@link #PROVIDER_ID} value ({@code "zoho"}) is the canonical identifier
 * used throughout Keycloak for this provider. It appears in:
 * <ul>
 *   <li>The Keycloak Admin UI "Add Identity Provider" dropdown (as the option key)</li>
 *   <li>The {@code theme.properties} icon key: {@code kcLogoIdP-zoho}</li>
 *   <li>The {@link ZohoUserAttributeMapper}'s compatible providers list</li>
 *   <li>The federated identity broker endpoint URL:
 *       {@code /realms/{realm}/broker/zoho/endpoint}</li>
 * </ul>
 * </p>
 *
 * @see ZohoIdentityProvider
 * @see ZohoIdentityProviderConfig
 */
public class ZohoIdentityProviderFactory
        extends AbstractIdentityProviderFactory<ZohoIdentityProvider>
        implements SocialIdentityProviderFactory<ZohoIdentityProvider> {

    /**
     * The canonical provider identifier. Must be unique across all identity providers
     * registered in a Keycloak instance.
     */
    public static final String PROVIDER_ID = "zoho";

    // -------------------------------------------------------------------------
    // Identity and display name
    // -------------------------------------------------------------------------

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getName() {
        return "Zoho";
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Creates a new {@link ZohoIdentityProvider} instance from the given session
     * and persisted model.
     *
     * <p>Called by Keycloak every time a user initiates a Zoho login flow.</p>
     *
     * @param session the current Keycloak session
     * @param model   the persisted identity provider model loaded from the database
     * @return a new {@link ZohoIdentityProvider}
     */
    @Override
    public ZohoIdentityProvider create(KeycloakSession session, IdentityProviderModel model) {
        return new ZohoIdentityProvider(session, new ZohoIdentityProviderConfig(model));
    }

    /**
     * Returns a new, empty {@link ZohoIdentityProviderConfig} instance.
     *
     * <p><strong>Important:</strong> This method MUST return {@link ZohoIdentityProviderConfig}
     * (not the base {@code OAuth2IdentityProviderConfig}). Keycloak uses the returned
     * type when marshaling config properties to and from the Admin REST API. Returning
     * the wrong type would cause the custom {@code zohoDataCenterDomain} property to
     * be silently lost on save/reload cycles.</p>
     *
     * @return a new {@link ZohoIdentityProviderConfig}
     */
    @Override
    public ZohoIdentityProviderConfig createConfig() {
        return new ZohoIdentityProviderConfig();
    }

    // -------------------------------------------------------------------------
    // Admin UI configuration properties
    // -------------------------------------------------------------------------

    /**
     * Declares the additional configuration fields shown in the Keycloak Admin UI
     * when creating or editing a Zoho identity provider.
     *
     * <p>The {@code name} of each property must exactly match the key used in
     * {@link ZohoIdentityProviderConfig} (the string passed to
     * {@code getConfig().get(name)}/{@code getConfig().put(name, value)}). Keycloak
     * uses these names as the keys when persisting to the database.</p>
     *
     * <p>Currently declares one extra property beyond the standard OAuth2 fields:
     * <dl>
     *   <dt>{@code zohoDataCenterDomain}</dt>
     *   <dd>A dropdown listing all seven Zoho data-center account domains.
     *   Defaults to {@code accounts.zoho.com} (US data center).</dd>
     * </dl>
     * </p>
     *
     * @return the list of provider-specific configuration properties
     */
    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ProviderConfigurationBuilder.create()
                .property()
                    .name(ZohoIdentityProviderConfig.DATA_CENTER_DOMAIN_KEY)
                    .label("Data Center Domain")
                    .helpText(
                        "The Zoho accounts domain for your organization's data center. " +
                        "Select the region where your Zoho account is hosted. " +
                        "Using the wrong data center will result in authentication errors.\n\n" +
                        "• US (default): accounts.zoho.com\n" +
                        "• EU: accounts.zoho.eu\n" +
                        "• IN: accounts.zoho.in\n" +
                        "• AU: accounts.zoho.com.au\n" +
                        "• CN: accounts.zoho.com.cn\n" +
                        "• JP: accounts.zoho.jp\n" +
                        "• CA: accounts.zohocloud.ca\n\n" +
                        "Note: For users across multiple data centers, register your " +
                        "application as a 'Multi DC' client in the Zoho API Console."
                    )
                    .type(ProviderConfigProperty.LIST_TYPE)
                    .options(List.of(
                            "accounts.zoho.com",
                            "accounts.zoho.eu",
                            "accounts.zoho.in",
                            "accounts.zoho.com.au",
                            "accounts.zoho.com.cn",
                            "accounts.zoho.jp",
                            "accounts.zohocloud.ca"
                    ))
                    .defaultValue(ZohoIdentityProviderConfig.DEFAULT_DC_DOMAIN)
                    .add()
                .build();
    }
}
