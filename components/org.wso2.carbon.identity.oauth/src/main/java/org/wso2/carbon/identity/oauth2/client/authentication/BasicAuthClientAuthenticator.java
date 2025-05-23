/*
 * Copyright (c) 2018-2025, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.oauth2.client.authentication;

import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.commons.io.Charsets;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.oltu.oauth2.common.OAuth;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.identity.core.util.IdentityTenantUtil;
import org.wso2.carbon.identity.oauth.IdentityOAuthAdminException;
import org.wso2.carbon.identity.oauth.common.OAuth2ErrorCodes;
import org.wso2.carbon.identity.oauth.common.exception.InvalidOAuthClientException;
import org.wso2.carbon.identity.oauth.internal.OAuthComponentServiceHolder;
import org.wso2.carbon.identity.oauth2.IdentityOAuth2Exception;
import org.wso2.carbon.identity.oauth2.bean.OAuthClientAuthnContext;
import org.wso2.carbon.identity.oauth2.model.ClientAuthenticationMethodModel;
import org.wso2.carbon.identity.oauth2.util.OAuth2Util;
import org.wso2.carbon.identity.organization.management.service.exception.OrganizationManagementException;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * This class is responsible for authenticating OAuth clients which are client id and secret to authenticate. This
 * authenticator will handle basic client authentication where client id and secret are present either as
 * Authorization header or in the body of the request.
 */
public class BasicAuthClientAuthenticator extends AbstractOAuthClientAuthenticator {

    private static final Log log = LogFactory.getLog(BasicAuthClientAuthenticator.class);
    private static final String CREDENTIAL_SEPARATOR = ":";
    private static final String SIMPLE_CASE_AUTHORIZATION_HEADER = "authorization";
    private static final String BASIC_PREFIX = "Basic";
    private static final int CREDENTIAL_LENGTH = 2;
    private static final String CLIENT_SECRET_BASIC = "client_secret_basic";
    private static final String CLIENT_SECRET_POST = "client_secret_post";
    private static final String CLIENT_SECRET_BASIC_DISPLAY_NAME = "Client Secret Basic";
    private static final String CLIENT_SECRET_POST_DISPLAY_NAME = "Client Secret Post";

    /**
     * Returns the execution order of this authenticator
     *
     * @return Execution place within the order
     */
    @Override
    public int getPriority() {

        return 100;
    }

    /**
     * @param request                 HttpServletRequest which is the incoming request.
     * @param bodyParams              Body parameter map of the request.
     * @param oAuthClientAuthnContext OAuth client authentication context.
     * @return Whether the authentication is successful or not.
     * @throws OAuthClientAuthnException
     */
    @Override
    public boolean authenticateClient(HttpServletRequest request, Map<String, List> bodyParams, OAuthClientAuthnContext
            oAuthClientAuthnContext) throws OAuthClientAuthnException {

        validateAuthenticationInfo(request, bodyParams);
        // In a case if client id is not set from canAuthenticate
        if (StringUtils.isEmpty(oAuthClientAuthnContext.getClientId())) {
            oAuthClientAuthnContext.setClientId(getClientId(request, bodyParams, oAuthClientAuthnContext));
        }

        try {
            if (log.isDebugEnabled()) {
                log.debug("Authenticating client : " + oAuthClientAuthnContext.getClientId() + " with client " +
                        "secret.");
            }
            String tenantDomain = IdentityTenantUtil.resolveTenantDomain();
            String appOrgId = PrivilegedCarbonContext.getThreadLocalCarbonContext()
                    .getApplicationResidentOrganizationId();
            /*
             If appOrgId is not empty, then the request comes for an application which is registered directly in the
             organization of the appOrgId. Therefore, we need to resolve the tenant domain of the organization.
            */
            if (StringUtils.isNotEmpty(appOrgId)) {
                try {
                    tenantDomain = OAuthComponentServiceHolder.getInstance().getOrganizationManager()
                            .resolveTenantDomain(appOrgId);
                } catch (OrganizationManagementException e) {
                    throw new InvalidOAuthClientException("Error while resolving tenant domain for the organization " +
                            "ID: " + appOrgId, e);
                }
            }
            // Authenticating the client with the client id, the client secret and the extracted tenant domain.
            return OAuth2Util.authenticateClient(oAuthClientAuthnContext.getClientId(),
                    (String) oAuthClientAuthnContext.getParameter(OAuth.OAUTH_CLIENT_SECRET), tenantDomain);
        } catch (IdentityOAuthAdminException e) {
            throw new OAuthClientAuthnException("Error while authenticating client",
                    OAuth2ErrorCodes.INVALID_CLIENT, e);
        } catch (InvalidOAuthClientException | IdentityOAuth2Exception e) {
            throw new OAuthClientAuthnException("Invalid Client : " + oAuthClientAuthnContext.getClientId(),
                    OAuth2ErrorCodes.INVALID_CLIENT, e);
        }

    }

    private void validateAuthenticationInfo(HttpServletRequest request, Map<String, List> contentMap)
            throws OAuthClientAuthnException {

        if (isBasicAuthorizationHeaderExists(request)) {
            if (log.isErrorEnabled()) {
                log.debug("Authorization header exists. Hence validating whether body params also present");
            }
            validateDuplicatedBasicAuthInfo(request, contentMap);
        }
    }

    /**
     * Returns whether the incoming request can be authenticated or not using the given inputs.
     *
     * @param request    HttpServletRequest which is the incoming request.
     * @param bodyParams Body parameters present in the request.
     * @param context    OAuth2 client authentication context.
     * @return
     */
    @Override
    public boolean canAuthenticate(HttpServletRequest request, Map<String, List> bodyParams, OAuthClientAuthnContext
            context) {

        if (isBasicAuthorizationHeaderExists(request)) {
            if (log.isDebugEnabled()) {
                log.debug("Basic auth credentials exists as Authorization header. Hence returning true.");
            }
            return true;
        } else if (isClientCredentialsExistsAsParams(bodyParams)) {
            if (log.isDebugEnabled()) {
                log.debug("Basic auth credentials present as body params. Hence returning true");
            }
            return true;
        }
        if (log.isDebugEnabled()) {
            log.debug("Client id and secret neither present as Authorization header nor as body params. Hence " +
                    "returning false");
        }
        return false;
    }

    /**
     * Get the name of the OAuth2 client authenticator.
     *
     * @return The name of the OAuth2 client authenticator.
     */
    @Override
    public String getName() {

        return "BasicOAuthClientCredAuthenticator";
    }

    /**
     * Retrives the client ID which is extracted from incoming request.
     *
     * @param request                 HttpServletRequest.
     * @param bodyParams              Body paarameter map of the incoming request.
     * @param oAuthClientAuthnContext OAuthClientAuthentication context.
     * @return Client ID of the OAuth2 client.
     * @throws OAuthClientAuthnException OAuth client authentication context.
     */
    @Override
    public String getClientId(HttpServletRequest request, Map<String, List> bodyParams, OAuthClientAuthnContext
            oAuthClientAuthnContext) throws OAuthClientAuthnException {

        if (isBasicAuthorizationHeaderExists(request)) {
            validateDuplicatedBasicAuthInfo(request, bodyParams);
            String[] credentials = extractCredentialsFromAuthzHeader(getAuthorizationHeader(request),
                    oAuthClientAuthnContext);
            oAuthClientAuthnContext.setClientId(credentials[0]);
            oAuthClientAuthnContext.addParameter(OAuth.OAUTH_CLIENT_SECRET, credentials[1]);

        } else {
            setClientCredentialsFromParam(bodyParams, oAuthClientAuthnContext);
        }
        return oAuthClientAuthnContext.getClientId();
    }

    /**
     * Validates that basic authentication information is only present either in body or as authorization headers.
     *
     * @param request      HttpServletRequest which is the incoming request.
     * @param bodyParams Parameter map of the body content.
     * @throws OAuthClientAuthnException
     */
    protected void validateDuplicatedBasicAuthInfo(HttpServletRequest request, Map<String, List> bodyParams) throws
            OAuthClientAuthnException {

        // The client MUST NOT use more than one authentication method in each request.
        if (isClientCredentialsExistsAsParams(bodyParams)) {
            if (log.isDebugEnabled()) {
                log.debug("Client Id and Client Secret found in request body and Authorization header" +
                        ". Credentials should be sent in either request body or Authorization header, not both");
            }
            throw new OAuthClientAuthnException("Request body and headers contain authorization information",
                    OAuth2ErrorCodes.INVALID_REQUEST);
        }
    }

    protected boolean isBasicAuthorizationHeaderExists(HttpServletRequest request) {

        String authorizationHeader = getAuthorizationHeader(request);
        // authorizationHeader should be case-insensitive according to the
        // "The 'Basic' HTTP Authentication Scheme" spec (https://tools.ietf.org/html/rfc7617#page-3),
        // "Note that both scheme and parameter names are matched case-insensitively."
        boolean isBasicAuthorizationHeaderExist = StringUtils.isNotEmpty(authorizationHeader) &&
                authorizationHeader.toUpperCase().startsWith(BASIC_PREFIX.toUpperCase());
        if (!isBasicAuthorizationHeaderExist) {
            if (log.isDebugEnabled()) {
                log.debug("Basic authorization does not exist");
            }
        }
        return isBasicAuthorizationHeaderExist;
    }

    protected String getAuthorizationHeader(HttpServletRequest request) {

        String authorizationHeader = request.getHeader(HTTPConstants.HEADER_AUTHORIZATION);
        if (StringUtils.isEmpty(authorizationHeader)) {
            authorizationHeader = request.getHeader(SIMPLE_CASE_AUTHORIZATION_HEADER);
        }
        if (StringUtils.isBlank(authorizationHeader)) {
            if (log.isDebugEnabled()) {
                log.debug("Authorization header is empty");
            }
        }
        return authorizationHeader;
    }

    protected boolean isClientCredentialsExistsAsParams(Map<String, List> contentParam) {

        Map<String, String> stringContent = getBodyParameters(contentParam);
        return (StringUtils.isNotEmpty(stringContent.get(OAuth.OAUTH_CLIENT_ID)) && StringUtils.isNotEmpty
                (stringContent.get(OAuth.OAUTH_CLIENT_SECRET)));
    }

    /**
     * Extracts client id and secret from Authorization header.
     *
     * @param authorizationHeader     Authroization header.
     * @param oAuthClientAuthnContext OAuth Client Authentication context.
     * @return An array which has client id as the first element and secret as the second element.
     * @throws OAuthClientAuthnException
     */
    protected static String[] extractCredentialsFromAuthzHeader(String authorizationHeader, OAuthClientAuthnContext
            oAuthClientAuthnContext) throws OAuthClientAuthnException {

        try {
            String[] authHeaderComponents = authorizationHeader.trim().split(" ");
            if (authHeaderComponents.length == CREDENTIAL_LENGTH) {
                byte[] decodedBytes = Base64.getDecoder().decode(authHeaderComponents[1].trim());
                String userNamePassword = new String(decodedBytes, Charsets.UTF_8);
                String[] credentials = userNamePassword.split(CREDENTIAL_SEPARATOR);
                if (credentials.length == CREDENTIAL_LENGTH) {
                    return credentials;
                }
            }
        } catch (IllegalArgumentException e) {
            throw new OAuthClientAuthnException("Error decoding authorization header. " + e.getMessage(),
                    OAuth2ErrorCodes.INVALID_CLIENT);
        }

        String errMsg = "Error decoding authorization header. Space delimited \"<authMethod> <base64Hash>\" format " +
                "violated.";
        throw new OAuthClientAuthnException(errMsg, OAuth2ErrorCodes.INVALID_CLIENT);
    }

    /**
     * Sets client id to the OAuth client authentication context.
     *
     * @param bodyParams Body parameters of the incoming request.
     * @param context      OAuth client authentication context.
     */
    protected void setClientCredentialsFromParam(Map<String, List> bodyParams, OAuthClientAuthnContext context) {

        Map<String, String> stringContent = getBodyParameters(bodyParams);
        context.setClientId(stringContent.get(OAuth.OAUTH_CLIENT_ID));
        context.addParameter(OAuth.OAUTH_CLIENT_SECRET, stringContent.get(OAuth.OAUTH_CLIENT_SECRET));
    }

    /**
     * Retrieve the authentication methods supported by the authenticator.
     *
     * @return      Authentication methods supported by the authenticator.
     */
    @Override
    public List<ClientAuthenticationMethodModel> getSupportedClientAuthenticationMethods() {

        List<ClientAuthenticationMethodModel> supportedAuthMethods = new ArrayList<>();
        supportedAuthMethods.add(new ClientAuthenticationMethodModel(CLIENT_SECRET_BASIC,
                CLIENT_SECRET_BASIC_DISPLAY_NAME));
        supportedAuthMethods.add(new ClientAuthenticationMethodModel(CLIENT_SECRET_POST,
                CLIENT_SECRET_POST_DISPLAY_NAME));
        return supportedAuthMethods;
    }

}
