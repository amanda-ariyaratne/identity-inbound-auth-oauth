/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.carbon.identity.oauth.scope.endpoint.impl;

import org.apache.commons.logging.Log;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import org.wso2.carbon.identity.core.ServiceURL;
import org.wso2.carbon.identity.core.ServiceURLBuilder;
import org.wso2.carbon.identity.core.URLBuilderException;
import org.wso2.carbon.identity.oauth.scope.endpoint.dto.ErrorDTO;
import org.wso2.carbon.identity.oauth.scope.endpoint.dto.ScopeDTO;
import org.wso2.carbon.identity.oauth.scope.endpoint.dto.ScopeToUpdateDTO;
import org.wso2.carbon.identity.oauth.scope.endpoint.exceptions.ScopeEndpointException;
import org.wso2.carbon.identity.oauth.scope.endpoint.util.ScopeUtils;
import org.wso2.carbon.identity.oauth2.IdentityOAuth2ScopeClientException;
import org.wso2.carbon.identity.oauth2.IdentityOAuth2ScopeException;
import org.wso2.carbon.identity.oauth2.IdentityOAuth2ScopeServerException;
import org.wso2.carbon.identity.oauth2.OAuth2ScopeService;
import org.wso2.carbon.identity.oauth2.Oauth2ScopeConstants;
import org.wso2.carbon.identity.oauth2.bean.Scope;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Response;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.wso2.carbon.identity.oauth.scope.endpoint.Constants.SERVER_API_PATH_COMPONENT;

@Listeners(MockitoTestNGListener.class)
public class ScopesApiServiceImplTest {

    private ScopesApiServiceImpl scopesApiService = new ScopesApiServiceImpl();
    private String someScopeName;
    private String someScopeDescription;

    @Mock
    private OAuth2ScopeService oAuth2ScopeService;

    private MockedStatic<ScopeUtils> scopeUtils;

    @BeforeMethod
    public void setUp() throws Exception {

        someScopeName = "scope";
        someScopeDescription = "some description";
        scopeUtils = mockStatic(ScopeUtils.class, Mockito.CALLS_REAL_METHODS);
        scopeUtils.when(ScopeUtils::getOAuth2ScopeService).thenReturn(oAuth2ScopeService);
    }

    @AfterMethod
    public void tearDown() {

        scopeUtils.close();
    }

    @DataProvider(name = "BuildUpdateScope")
    public Object[][] buildUpdateApplication() {

        IdentityOAuth2ScopeClientException identityOAuth2ScopeClientException =
                new IdentityOAuth2ScopeClientException("Oauth2 Scope Client Exception");
        IdentityOAuth2ScopeException identityOAuth2ScopeException = new IdentityOAuth2ScopeException("Oauth2 Scope " +
                "Exception");
        return new Object[][]{
                {Response.Status.OK, null},
                {Response.Status.BAD_REQUEST, identityOAuth2ScopeClientException},
                {Response.Status.NOT_FOUND, identityOAuth2ScopeClientException},
                {Response.Status.INTERNAL_SERVER_ERROR, identityOAuth2ScopeException}
        };
    }

    @Test(dataProvider = "BuildUpdateScope")
    public void testUpdateScope(Response.Status expectation, Throwable throwable) throws Exception {

        ScopeToUpdateDTO scopeToUpdateDTO = new ScopeToUpdateDTO();
        scopeToUpdateDTO.setDescription("some description");
        scopeToUpdateDTO.setBindings(Collections.<String>emptyList());

        if (Response.Status.OK.equals(expectation)) {
            ScopeDTO scopeDTO = new ScopeDTO();
            scopeUtils.when(() -> ScopeUtils.getScopeDTO(any(Scope.class))).thenReturn(scopeDTO);
            Scope scope = new Scope(someScopeName, someScopeName, someScopeDescription);
            scopeUtils.when(() -> ScopeUtils.getUpdatedScope(any(ScopeToUpdateDTO.class), anyString()))
                    .thenReturn(scope);
            when(oAuth2ScopeService.updateScope(any(Scope.class))).thenReturn(scope);
            assertEquals(scopesApiService.updateScope(scopeToUpdateDTO, someScopeName).getStatus(),
                    Response.Status.OK.getStatusCode(), "Error occurred while updating scopes");
        } else if (Response.Status.BAD_REQUEST.equals(expectation)) {
            when(oAuth2ScopeService.updateScope(any(Scope.class))).thenThrow(IdentityOAuth2ScopeClientException.class);
            callRealMethod();
            try {
                scopesApiService.updateScope(scopeToUpdateDTO, someScopeName);
            } catch (ScopeEndpointException e) {
                assertEquals(e.getResponse().getStatus(), Response.Status.BAD_REQUEST.getStatusCode(),
                        "Cannot find HTTP Response, Bad Request in Case of " +
                                "IdentityOAuth2ScopeClientException");
                assertEquals(((ErrorDTO) (e.getResponse().getEntity())).getMessage(),
                        Response.Status.BAD_REQUEST.getReasonPhrase(), "Cannot find appropriate error message " +
                                "for HTTP Response, Bad Request");
            } finally {
                reset(oAuth2ScopeService);
            }
        } else if (Response.Status.NOT_FOUND.equals(expectation)) {
            ((IdentityOAuth2ScopeException) throwable).setErrorCode(Oauth2ScopeConstants.ErrorMessages.
                    ERROR_CODE_NOT_FOUND_SCOPE.getCode());
            when(oAuth2ScopeService.updateScope(any(Scope.class))).thenThrow(throwable);
            callRealMethod();
            try {
                scopesApiService.updateScope(scopeToUpdateDTO, someScopeName);
            } catch (ScopeEndpointException e) {
                assertEquals(e.getResponse().getStatus(), Response.Status.NOT_FOUND.getStatusCode(),
                        "Cannot find HTTP Response, Not Found in Case of " +
                                "IdentityOAuth2ScopeClientException");
                assertEquals(((ErrorDTO) (e.getResponse().getEntity())).getMessage(),
                        Response.Status.NOT_FOUND.getReasonPhrase(), "Cannot find appropriate error message " +
                                "for HTTP Response, Not Found");
            } finally {
                reset(oAuth2ScopeService);
            }
        } else if (Response.Status.INTERNAL_SERVER_ERROR.equals(expectation)) {
            when(oAuth2ScopeService.updateScope(any(Scope.class))).thenThrow(IdentityOAuth2ScopeException.class);
            callRealMethod();
            try {
                scopesApiService.updateScope(scopeToUpdateDTO, someScopeName);
            } catch (ScopeEndpointException e) {
                assertEquals(e.getResponse().getStatus(), Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                        "Cannot find HTTP Response, Internal Server Error in case of " +
                                "IdentityOAuth2ScopeException");
                assertNull(e.getResponse().getEntity(), "Do not include error message in case of " +
                        "Server Exception");
            } finally {
                reset(oAuth2ScopeService);
            }
        }
    }

    @DataProvider(name = "BuildGetScope")
    public Object[][] buildGetApplication() {

        IdentityOAuth2ScopeClientException identityOAuth2ScopeClientException = new IdentityOAuth2ScopeClientException
                ("Oauth2 Scope Client Exception");
        IdentityOAuth2ScopeException identityOAuth2ScopeException = new IdentityOAuth2ScopeException
                ("Oauth2 Scope Exception");
        return new Object[][]{
                {Response.Status.OK, null},
                {Response.Status.BAD_REQUEST, identityOAuth2ScopeClientException},
                {Response.Status.NOT_FOUND, identityOAuth2ScopeClientException},
                {Response.Status.INTERNAL_SERVER_ERROR, identityOAuth2ScopeException}
        };
    }

    @Test(dataProvider = "BuildGetScope")
    public void testGetScope(Response.Status expectation, Throwable throwable) throws Exception {

        if (Response.Status.OK.equals(expectation)) {
            when(oAuth2ScopeService.getScope(someScopeName))
                    .thenReturn(new Scope(someScopeName, someScopeName, someScopeDescription));
            assertEquals(scopesApiService.getScope(someScopeName).getStatus(), Response.Status.OK.getStatusCode(),
                    "Error occurred while getting a scope");
        } else if (Response.Status.BAD_REQUEST.equals(expectation)) {
            when(oAuth2ScopeService.getScope(someScopeName)).thenThrow(throwable);
            callRealMethod();
            try {
                scopesApiService.getScope(someScopeName);
            } catch (ScopeEndpointException e) {
                assertEquals(e.getResponse().getStatus(), Response.Status.BAD_REQUEST.getStatusCode(),
                        "Cannot find HTTP Response, Bad Request in Case of " +
                                "IdentityOAuth2ScopeClientException");
                assertEquals(((ErrorDTO) (e.getResponse().getEntity())).getMessage(),
                        Response.Status.BAD_REQUEST.getReasonPhrase(), "Cannot find appropriate error message " +
                                "for HTTP Response, Bad Request");
            } finally {
                reset(oAuth2ScopeService);
            }
        } else if (Response.Status.NOT_FOUND.equals(expectation)) {
            ((IdentityOAuth2ScopeException) throwable).setErrorCode(Oauth2ScopeConstants.ErrorMessages.
                    ERROR_CODE_NOT_FOUND_SCOPE.getCode());
            when(oAuth2ScopeService.getScope(someScopeName)).thenThrow(throwable);
            callRealMethod();
            try {
                scopesApiService.getScope(someScopeName);
            } catch (ScopeEndpointException e) {
                assertEquals(e.getResponse().getStatus(), Response.Status.NOT_FOUND.getStatusCode(),
                        "Cannot find HTTP Response, Not Found in Case of " +
                                "IdentityOAuth2ScopeClientException");
                assertEquals(((ErrorDTO) (e.getResponse().getEntity())).getMessage(),
                        Response.Status.NOT_FOUND.getReasonPhrase(), "Cannot find appropriate error message " +
                                "for HTTP Response, Not Found");
            } finally {
                reset(oAuth2ScopeService);
            }
        } else if (Response.Status.INTERNAL_SERVER_ERROR.equals(expectation)) {
            when(oAuth2ScopeService.getScope(someScopeName)).thenThrow(throwable);
            callRealMethod();
            try {
                scopesApiService.getScope(someScopeName);
            } catch (ScopeEndpointException e) {
                assertEquals(e.getResponse().getStatus(), Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                        "Cannot find HTTP Response, Internal Server Error in case of " +
                                "IdentityOAuth2ScopeException");
                assertNull(e.getResponse().getEntity(), "Do not include error message in case of " +
                        "Server Exception");
            } finally {
                reset(oAuth2ScopeService);
            }
        }
    }

    @DataProvider(name = "BuildgetScopes")
    public Object[][] getscopes() {

        return new Object[][]{
                {Response.Status.OK}, {Response.Status.INTERNAL_SERVER_ERROR}
        };
    }

    @Test(dataProvider = "BuildgetScopes")
    public void testGetScopes(Response.Status expectation) throws Exception {

        Set<Scope> scopes = new HashSet<>();
        scopes.add(new Scope(someScopeName, someScopeName, someScopeDescription));
        int startIndex = 0;
        int count = 1;

        if (Response.Status.OK.equals(expectation)) {
            when(oAuth2ScopeService.getScopes(any(Integer.class), any(Integer.class), any(Boolean.class),
                    isNull())).thenReturn(scopes);
            scopeUtils.when(() -> ScopeUtils.getScopeDTOs(any(Set.class))).thenCallRealMethod();
            Response response = scopesApiService.getScopes(startIndex, count);
            assertEquals(response.getStatus(), Response.Status.OK.getStatusCode(),
                    "Error occurred while getting scopes");
            assertEquals(((HashSet) response.getEntity()).size(), count, "Cannot Retrieve Expected Scopes");
        } else if (Response.Status.INTERNAL_SERVER_ERROR.equals(expectation)) {
            when(oAuth2ScopeService.getScopes(any(Integer.class), any(Integer.class), anyBoolean(),
                    nullable(String.class))).thenThrow(IdentityOAuth2ScopeServerException.class);
            callRealMethod();
            try {
                scopesApiService.getScopes(startIndex, count);
            } catch (ScopeEndpointException e) {
                assertEquals(e.getResponse().getStatus(), Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                        "Cannot find HTTP Response, Internal Server Error in case of " +
                                "IdentityOAuth2ScopeException");
                assertNull(e.getResponse().getEntity(), "Do not include error message in case of " +
                        "Server Exception");
            }
        }
    }

    @DataProvider(name = "BuildDeleteScope")
    public Object[][] buildDeleteApplication() {

        IdentityOAuth2ScopeClientException identityOAuth2ScopeClientException = new IdentityOAuth2ScopeClientException
                ("Oauth2 Scope Client Exception");
        IdentityOAuth2ScopeException identityOAuth2ScopeException = new IdentityOAuth2ScopeException
                ("Oauth2 Scope Exception");
        return new Object[][]{
                {Response.Status.OK, null},
                {Response.Status.BAD_REQUEST, identityOAuth2ScopeClientException},
                {Response.Status.NOT_FOUND, identityOAuth2ScopeClientException}
        };
    }

    @Test(dataProvider = "BuildDeleteScope")
    public void testDeleteScope(Response.Status expectation, Throwable throwable) throws Exception {

        if (Response.Status.OK.equals(expectation)) {
            doNothing().when(oAuth2ScopeService).deleteScope(any(String.class));
            assertEquals(scopesApiService.deleteScope(someScopeName).getStatus(),
                    Response.Status.OK.getStatusCode());
        } else if (Response.Status.BAD_REQUEST.equals(expectation)) {
            doThrow(throwable).when(oAuth2ScopeService).deleteScope(any(String.class));
            callRealMethod();
            try {
                scopesApiService.deleteScope(someScopeName);
            } catch (ScopeEndpointException e) {
                assertEquals(e.getResponse().getStatus(), Response.Status.BAD_REQUEST.getStatusCode(),
                        "Cannot find HTTP Response, Bad Request in Case of " +
                                "IdentityOAuth2ScopeClientException");
                assertEquals(((ErrorDTO) (e.getResponse().getEntity())).getMessage(),
                        Response.Status.BAD_REQUEST.getReasonPhrase(), "Cannot find appropriate error message " +
                                "for HTTP Response, Bad Request");
            } finally {
                reset(oAuth2ScopeService);
            }
        } else if (Response.Status.NOT_FOUND.equals(expectation)) {
            ((IdentityOAuth2ScopeException) throwable).setErrorCode(Oauth2ScopeConstants.ErrorMessages.
                    ERROR_CODE_NOT_FOUND_SCOPE.getCode());
            doThrow(throwable).when(oAuth2ScopeService).deleteScope(any(String.class));
            callRealMethod();
            try {
                scopesApiService.deleteScope(someScopeName);
            } catch (ScopeEndpointException e) {
                assertEquals(e.getResponse().getStatus(), Response.Status.NOT_FOUND.getStatusCode(),
                        "Cannot find HTTP Response, Not Found in Case of " +
                                "IdentityOAuth2ScopeClientException");
                assertEquals(((ErrorDTO) (e.getResponse().getEntity())).getMessage(),
                        Response.Status.NOT_FOUND.getReasonPhrase(), "Cannot find appropriate error message " +
                                "for HTTP Response, Not Found");
            } finally {
                reset(oAuth2ScopeService);
            }
        }
    }

    @DataProvider(name = "BuildRegisterScope")
    public Object[][] buildRegisterApplication() {

        IdentityOAuth2ScopeClientException identityOAuth2ScopeClientException = new IdentityOAuth2ScopeClientException
                ("Oauth2 Scope Client Exception");
        IdentityOAuth2ScopeException identityOAuth2ScopeException = new IdentityOAuth2ScopeException
                ("Oauth2 Scope Exception");
        return new Object[][]{
                {Response.Status.OK, null},
                {Response.Status.BAD_REQUEST, identityOAuth2ScopeClientException},
                {Response.Status.CONFLICT, identityOAuth2ScopeClientException},
                {Response.Status.INTERNAL_SERVER_ERROR, identityOAuth2ScopeException}
        };
    }

    @Test(dataProvider = "BuildRegisterScope")
    public void testRegisterScope(Response.Status expectation, Throwable throwable) throws Exception {

        try (MockedStatic<ServiceURLBuilder> serviceURLBuilder = mockStatic(ServiceURLBuilder.class);) {
            ScopeDTO scopeDTO = new ScopeDTO();
            scopeDTO.setDescription("some description");
            scopeDTO.setBindings(Collections.<String>emptyList());
            mockServiceURLBuilder(SERVER_API_PATH_COMPONENT + scopeDTO.getName(), serviceURLBuilder);
            if (Response.Status.OK.equals(expectation)) {
                Scope scope = new Scope(scopeDTO.getName(), scopeDTO.getName(), scopeDTO.getDescription());
                when(oAuth2ScopeService.registerScope(any(Scope.class))).thenReturn(scope);
                assertEquals(scopesApiService.registerScope(scopeDTO).getStatus(),
                        Response.Status.CREATED.getStatusCode(),
                        "Error occurred while registering scopes");
            } else if (Response.Status.BAD_REQUEST.equals(expectation)) {
                when(oAuth2ScopeService.registerScope(any(Scope.class))).thenThrow(throwable);
                callRealMethod();
                try {
                    scopesApiService.registerScope(scopeDTO);
                } catch (ScopeEndpointException e) {
                    assertEquals(e.getResponse().getStatus(), Response.Status.BAD_REQUEST.getStatusCode(),
                            "Cannot find HTTP Response, Bad Request in Case of " +
                                    "IdentityOAuth2ScopeClientException");
                    assertEquals(((ErrorDTO) (e.getResponse().getEntity())).getMessage(),
                            Response.Status.BAD_REQUEST.getReasonPhrase(), "Cannot find appropriate error message " +
                                    "for HTTP Response, Bad Request");
                } finally {
                    reset(oAuth2ScopeService);
                }
            } else if (Response.Status.CONFLICT.equals(expectation)) {
                ((IdentityOAuth2ScopeException) throwable).setErrorCode(Oauth2ScopeConstants.ErrorMessages.
                        ERROR_CODE_CONFLICT_REQUEST_EXISTING_SCOPE.getCode());
                when(oAuth2ScopeService.registerScope(any(Scope.class))).thenThrow(throwable);
                callRealMethod();
                try {
                    scopesApiService.registerScope(scopeDTO);
                } catch (ScopeEndpointException e) {
                    assertEquals(e.getResponse().getStatus(), Response.Status.CONFLICT.getStatusCode(),
                            "Cannot find HTTP Response, Conflict in Case of " +
                                    "IdentityOAuth2ScopeClientException");
                    assertEquals(((ErrorDTO) (e.getResponse().getEntity())).getMessage(),
                            Response.Status.CONFLICT.getReasonPhrase(), "Cannot find appropriate error message " +
                                    "for HTTP Response, Conflict");
                } finally {
                    reset(oAuth2ScopeService);
                }
            } else if (Response.Status.INTERNAL_SERVER_ERROR.equals(expectation)) {
                when(oAuth2ScopeService.registerScope(any(Scope.class))).thenThrow(IdentityOAuth2ScopeException.class);
                callRealMethod();
                try {
                    scopesApiService.registerScope(scopeDTO);
                } catch (ScopeEndpointException e) {
                    assertEquals(e.getResponse().getStatus(), Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                            "Cannot find HTTP Response, Internal Server Error in case of " +
                                    "IdentityOAuth2ScopeException");
                    assertNull(e.getResponse().getEntity(), "Do not include error message in case of " +
                            "Server Exception");
                } finally {
                    reset(oAuth2ScopeService);
                }
            }
        }
    }

    @DataProvider(name = "checkisScopeException")
    public Object[][] checkScopeException() {

        IdentityOAuth2ScopeClientException identityOAuth2ScopeClientException = new IdentityOAuth2ScopeClientException
                ("Oauth2 Scope Client Exception");
        IdentityOAuth2ScopeException identityOAuth2ScopeException = new IdentityOAuth2ScopeException
                ("Oauth2 Scope Exception");
        return new Object[][]{
                {Response.Status.OK, null},
                {Response.Status.NOT_FOUND, null},
                {Response.Status.BAD_REQUEST, identityOAuth2ScopeClientException},
                {Response.Status.INTERNAL_SERVER_ERROR, identityOAuth2ScopeException}
        };
    }

    @Test(dataProvider = "checkisScopeException")
    public void testIsScopeExists(Response.Status expectation, Throwable throwable) throws Exception {

        if (Response.Status.OK.equals(expectation)) {
            when(oAuth2ScopeService.isScopeExists(someScopeName)).thenReturn(Boolean.TRUE);
            assertEquals(scopesApiService.isScopeExists(someScopeName).getStatus(), Response.Status.OK.getStatusCode(),
                    "Error occurred while checking is scope exist");
        } else if (Response.Status.NOT_FOUND.equals(expectation)) {
            when(oAuth2ScopeService.isScopeExists(someScopeName)).thenReturn(Boolean.FALSE);
            assertEquals(scopesApiService.isScopeExists(someScopeName).getStatus(),
                    Response.Status.NOT_FOUND.getStatusCode(),
                    "Given scope does not exist but error while checking isExist");
        } else if (Response.Status.BAD_REQUEST.equals(expectation)) {
            when(oAuth2ScopeService.isScopeExists(someScopeName)).thenThrow(throwable);
            callRealMethod();
            try {
                scopesApiService.isScopeExists(someScopeName);
            } catch (ScopeEndpointException e) {
                assertEquals(e.getResponse().getStatus(), Response.Status.BAD_REQUEST.getStatusCode(),
                        "Cannot find HTTP Response, Bad Request in Case of " +
                                "IdentityOAuth2ScopeClientException");
                assertEquals(((ErrorDTO) (e.getResponse().getEntity())).getMessage(),
                        Response.Status.BAD_REQUEST.getReasonPhrase(), "Cannot find appropriate error message " +
                                "for HTTP Response, Bad Request");
            } finally {
                reset(oAuth2ScopeService);
            }
        } else if (Response.Status.INTERNAL_SERVER_ERROR.equals(expectation)) {
            when(oAuth2ScopeService.isScopeExists("scope")).thenThrow(throwable);
            callRealMethod();
            try {
                scopesApiService.isScopeExists(someScopeName);
            } catch (ScopeEndpointException e) {
                assertEquals(e.getResponse().getStatus(), Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                        "Cannot find HTTP Response, Internal Server Error in case of " +
                                "IdentityOAuth2ScopeException");
                assertNull(e.getResponse().getEntity(), "Do not include error message in case of " +
                        "Server Exception");
            } finally {
                reset(oAuth2ScopeService);
            }
        }
    }

    private void callRealMethod() throws Exception {

        scopeUtils.when(() -> ScopeUtils.handleErrorResponse(any(Response.Status.class), any(String.class),
                any(Throwable.class), any(boolean.class), any(Log.class))).thenCallRealMethod();
//        scopeUtils.when(() -> ScopeUtils.buildScopeEndpointException(any(Response.Status.class),
//                any(String.class), any(String.class), any(String.class), any(boolean.class))).thenCallRealMethod();
        scopeUtils.when(() -> ScopeUtils.getErrorDTO(any(String.class), any(String.class),
                any(String.class))).thenCallRealMethod();
    }

    private void mockServiceURLBuilder(String url, MockedStatic<ServiceURLBuilder> serviceURLBuilder)
            throws URLBuilderException {

        ServiceURLBuilder mockServiceURLBuilder = mock(ServiceURLBuilder.class);
        serviceURLBuilder.when(ServiceURLBuilder::create).thenReturn(mockServiceURLBuilder);
        lenient().when(mockServiceURLBuilder.addPath(any())).thenReturn(mockServiceURLBuilder);

        ServiceURL serviceURL = mock(ServiceURL.class);
        lenient().when(serviceURL.getAbsolutePublicURL()).thenReturn(url);
        lenient().when(mockServiceURLBuilder.build()).thenReturn(serviceURL);
    }

}

