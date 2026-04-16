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

import org.keycloak.broker.oidc.mappers.AbstractJsonUserAttributeMapper;

/**
 * Attribute mapper for the Zoho Social Identity Provider.
 *
 * <p>Extends {@link AbstractJsonUserAttributeMapper} to enable JSON-path-based
 * mapping of Zoho user profile fields to Keycloak user attributes. This mapper
 * is discoverable in the Keycloak Admin UI under the Zoho identity provider's
 * "Mappers" tab via "Add Mapper → zoho-user-attribute-mapper".</p>
 *
 * <h2>Available Zoho Profile Fields</h2>
 * <p>The following fields are returned by Zoho's {@code /oauth/user/info} endpoint
 * and can be mapped to Keycloak user attributes:</p>
 * <table>
 *   <tr><th>JSON Field</th><th>Description</th></tr>
 *   <tr><td>{@code ZAID}</td><td>Zoho Account ID — stable unique identifier</td></tr>
 *   <tr><td>{@code Display_Name}</td><td>User's display/full name</td></tr>
 *   <tr><td>{@code Email}</td><td>Primary email address</td></tr>
 *   <tr><td>{@code First_Name}</td><td>Given name</td></tr>
 *   <tr><td>{@code Last_Name}</td><td>Family name</td></tr>
 * </table>
 *
 * <h2>Example Use Case</h2>
 * <p>To store the Zoho Account ID as a Keycloak user attribute named {@code zoho_id}:
 * <ol>
 *   <li>Open Keycloak Admin UI → Identity Providers → Zoho → Mappers</li>
 *   <li>Click "Add Mapper"</li>
 *   <li>Select type "Zoho User Attribute Mapper"</li>
 *   <li>Set "User Attribute" to {@code zoho_id}</li>
 *   <li>Set "JSON Field Path" to {@code ZAID}</li>
 *   <li>Save</li>
 * </ol>
 * </p>
 *
 * @see AbstractJsonUserAttributeMapper
 * @see ZohoIdentityProvider#FIELD_ZAID
 */
public class ZohoUserAttributeMapper extends AbstractJsonUserAttributeMapper {

    /** The mapper type identifier shown in the Admin UI "Add Mapper" dropdown. */
    public static final String PROVIDER_ID = "zoho-user-attribute-mapper";

    /**
     * The set of identity provider IDs this mapper is compatible with.
     * Must match {@link ZohoIdentityProviderFactory#PROVIDER_ID}.
     */
    private static final String[] COMPATIBLE_PROVIDERS = {
            ZohoIdentityProviderFactory.PROVIDER_ID
    };

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String[] getCompatibleProviders() {
        return COMPATIBLE_PROVIDERS;
    }
}
