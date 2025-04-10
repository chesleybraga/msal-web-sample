package com.btg.moneymarket.msal.helper;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.text.ParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.annotation.PostConstruct;
import javax.naming.ServiceUnavailableException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.btg.moneymarket.msal.StateData;
import com.btg.moneymarket.msal.model.BasicConfiguration;
import com.microsoft.aad.msal4j.AuthorizationCodeParameters;
import com.microsoft.aad.msal4j.AuthorizationRequestUrlParameters;
import com.microsoft.aad.msal4j.ClientCredentialFactory;
import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.IConfidentialClientApplication;
import com.microsoft.aad.msal4j.Prompt;
import com.microsoft.aad.msal4j.PublicClientApplication;
import com.microsoft.aad.msal4j.ResponseMode;
import com.microsoft.aad.msal4j.SilentParameters;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.openid.connect.sdk.AuthenticationErrorResponse;
import com.nimbusds.openid.connect.sdk.AuthenticationResponse;
import com.nimbusds.openid.connect.sdk.AuthenticationResponseParser;
import com.nimbusds.openid.connect.sdk.AuthenticationSuccessResponse;

/**
 * Helpers for acquiring authorization codes and tokens from AAD
 */
@Component
public class AuthHelper {

    public static final String PRINCIPAL_SESSION_NAME = "principal";
    public static final String TOKEN_CACHE_SESSION_ATTRIBUTE = "token_cache";

    private String clientId;
    private String clientSecret;
    private String authority;
    private String redirectUriSignIn;

    @Autowired
    private BasicConfiguration configuration;

    @PostConstruct
    public void init() {
	clientId = configuration.getClientId();
	authority = configuration.getAuthority();
	clientSecret = configuration.getSecretKey();
	redirectUriSignIn = configuration.getRedirectUriSignin();
    }

    public void processAuthenticationCodeRedirect(HttpServletRequest httpRequest, String currentUri, String fullUrl)
	    throws Throwable {

	Map<String, List<String>> params = new HashMap<>();
	for (String key : httpRequest.getParameterMap().keySet()) {
	    params.put(key, Collections.singletonList(httpRequest.getParameterMap().get(key)[0]));
	}
	// validate that state in response equals to state in request
	StateData stateData = SessionManagementHelper.validateState(httpRequest.getSession(),
		params.get(SessionManagementHelper.STATE).get(0));

	AuthenticationResponse authResponse = AuthenticationResponseParser.parse(new URI(fullUrl), params);
	if (AuthHelper.isAuthenticationSuccessful(authResponse)) {
	    AuthenticationSuccessResponse oidcResponse = (AuthenticationSuccessResponse) authResponse;
	    // validate that OIDC Auth Response matches Code Flow (contains only requested
	    // artifacts)
	    validateAuthRespMatchesAuthCodeFlow(oidcResponse);

	    IAuthenticationResult result = getAuthResultByAuthCode(httpRequest, oidcResponse.getAuthorizationCode(),
		    currentUri);

	    // validate nonce to prevent reply attacks (code maybe substituted to one with
	    // broader access)
	    validateNonce(stateData, getNonceClaimValueFromIdToken(result.idToken()));

	    SessionManagementHelper.setSessionPrincipal(httpRequest, result);
	} else {
	    AuthenticationErrorResponse oidcResponse = (AuthenticationErrorResponse) authResponse;
	    throw new Exception(String.format("Request for auth code failed: %s - %s",
		    oidcResponse.getErrorObject().getCode(), oidcResponse.getErrorObject().getDescription()));
	}
    }

    public IAuthenticationResult getAuthResultBySilentFlow(HttpServletRequest httpRequest,
	    HttpServletResponse httpResponse) throws Throwable {

	IAuthenticationResult result = SessionManagementHelper.getAuthSessionObject(httpRequest);

	IConfidentialClientApplication app = createClientApplication();

	Object tokenCache = httpRequest.getSession().getAttribute("token_cache");
	if (tokenCache != null) {
	    app.tokenCache().deserialize(tokenCache.toString());
	}

	SilentParameters parameters = SilentParameters.builder(Collections.singleton("User.Read"), result.account())
		.build();

	CompletableFuture<IAuthenticationResult> future = app.acquireTokenSilently(parameters);
	IAuthenticationResult updatedResult = future.get();

	// update session with latest token cache
	SessionManagementHelper.storeTokenCacheInSession(httpRequest, app.tokenCache().serialize());

	return updatedResult;
    }

    private void validateNonce(StateData stateData, String nonce) throws Exception {
	if (isEmpty(nonce) || !nonce.equals(stateData.getNonce())) {
	    throw new Exception(SessionManagementHelper.FAILED_TO_VALIDATE_MESSAGE + "could not validate nonce");
	}
    }

    private boolean isEmpty(final CharSequence cs) {
	return cs == null || cs.length() == 0;
    }

    private String getNonceClaimValueFromIdToken(String idToken) throws ParseException {
	return (String) JWTParser.parse(idToken).getJWTClaimsSet().getClaim("nonce");
    }

    private void validateAuthRespMatchesAuthCodeFlow(AuthenticationSuccessResponse oidcResponse) throws Exception {
	if (oidcResponse.getIDToken() != null || oidcResponse.getAccessToken() != null
		|| oidcResponse.getAuthorizationCode() == null) {
	    throw new Exception(
		    SessionManagementHelper.FAILED_TO_VALIDATE_MESSAGE + "unexpected set of artifacts received");
	}
    }

    public void sendAuthRedirect(HttpServletRequest httpRequest, HttpServletResponse httpResponse, String scope,
	    String redirectURL) throws IOException {

	// state parameter to validate response from Authorization server and nonce
	// parameter to validate idToken
	String state = UUID.randomUUID().toString();
	String nonce = UUID.randomUUID().toString();

	SessionManagementHelper.storeStateAndNonceInSession(httpRequest.getSession(), state, nonce);

	httpResponse.setStatus(302);
	String authorizationCodeUrl = getAuthorizationCodeUrl(httpRequest.getParameter("claims"), scope, redirectURL,
		state, nonce);
	httpResponse.sendRedirect(authorizationCodeUrl);
    }

    public String getAuthorizationCodeUrl(String claims, String scope, String registeredRedirectURL, String state,
	    String nonce) throws MalformedURLException {

	String updatedScopes = scope == null ? "" : scope;

	PublicClientApplication pca = PublicClientApplication.builder(clientId).authority(authority).build();

	AuthorizationRequestUrlParameters parameters = AuthorizationRequestUrlParameters
		.builder(registeredRedirectURL, Collections.singleton(updatedScopes)).responseMode(ResponseMode.QUERY)
		.prompt(Prompt.SELECT_ACCOUNT).state(state).nonce(nonce).claimsChallenge(claims).build();

	return pca.getAuthorizationRequestUrl(parameters).toString();
    }

    private IAuthenticationResult getAuthResultByAuthCode(HttpServletRequest httpServletRequest,
	    AuthorizationCode authorizationCode, String currentUri) throws Throwable {

	IAuthenticationResult result;
	ConfidentialClientApplication app;
	try {
	    app = createClientApplication();

	    String authCode = authorizationCode.getValue();
	    AuthorizationCodeParameters parameters = AuthorizationCodeParameters.builder(authCode, new URI(currentUri))
		    .build();

	    Future<IAuthenticationResult> future = app.acquireToken(parameters);

	    result = future.get();
	} catch (ExecutionException e) {
	    throw e.getCause();
	}

	if (result == null) {
	    throw new ServiceUnavailableException("authentication result was null");
	}

	SessionManagementHelper.storeTokenCacheInSession(httpServletRequest, app.tokenCache().serialize());

	return result;
    }

    private ConfidentialClientApplication createClientApplication() throws MalformedURLException {
	return ConfidentialClientApplication.builder(clientId, ClientCredentialFactory.createFromSecret(clientSecret))
		.authority(authority).build();
    }

    private static boolean isAuthenticationSuccessful(AuthenticationResponse authResponse) {
	return authResponse instanceof AuthenticationSuccessResponse;
    }

    public String getRedirectUriSignIn() {
	return redirectUriSignIn;
    }
}