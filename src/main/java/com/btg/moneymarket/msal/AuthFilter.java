package com.btg.moneymarket.msal;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.btg.moneymarket.msal.helper.AuthHelper;
import com.btg.moneymarket.msal.helper.SessionManagementHelper;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.MsalException;

/**
 * Processes incoming requests based on auth status
 */
@Component
public class AuthFilter implements Filter {

//    private List<String> excludedUrls = Arrays.asList("/", "/msal4jsample/");
    private List<String> excludedUrls = Arrays.asList("/");

    @Autowired
    private AuthHelper authHelper;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
	    throws IOException, ServletException {
	if (request instanceof HttpServletRequest) {
	    HttpServletRequest httpRequest = (HttpServletRequest) request;
	    HttpServletResponse httpResponse = (HttpServletResponse) response;

	    try {
		String currentUri = httpRequest.getRequestURL().toString();
		String path = httpRequest.getServletPath();
		String queryStr = httpRequest.getQueryString();
		String fullUrl = currentUri + (queryStr != null ? "?" + queryStr : "");

		// exclude home page
		if (excludedUrls.contains(path)) {
		    chain.doFilter(request, response);
		    return;
		}

		if (containsAuthenticationCode(httpRequest)) {
		    // response should have authentication code, which will be used to acquire
		    // access token
		    authHelper.processAuthenticationCodeRedirect(httpRequest, currentUri, fullUrl);

		    // remove query params so that containsAuthenticationCode will not be true on
		    // future requests
		    ((HttpServletResponse) response).sendRedirect(currentUri);

		    chain.doFilter(request, response);
		    return;
		}

		// check if user has a AuthData in the session
		if (!isAuthenticated(httpRequest)) {
		    // not authenticated, redirecting to login.microsoft.com so user can
		    // authenticate
		    authHelper.sendAuthRedirect(httpRequest, httpResponse, null, authHelper.getRedirectUriSignIn());
		    return;
		}

		if (isAccessTokenExpired(httpRequest)) {
		    updateAuthDataUsingSilentFlow(httpRequest, httpResponse);
		}
	    } catch (MsalException authException) {
		// something went wrong (like expiration or revocation of token)
		// we should invalidate AuthData stored in session and redirect to Authorization
		// server
		SessionManagementHelper.removePrincipalFromSession(httpRequest);
		authHelper.sendAuthRedirect(httpRequest, httpResponse, null, authHelper.getRedirectUriSignIn());
		return;
	    } catch (Throwable exc) {
		httpResponse.setStatus(500);
		System.out.println(exc.getMessage());
		request.setAttribute("error", exc.getMessage());
		request.getRequestDispatcher("/error").forward(request, response);
		return;
	    }
	}
	chain.doFilter(request, response);
    }

    private boolean containsAuthenticationCode(HttpServletRequest httpRequest) {
	Map<String, String[]> httpParameters = httpRequest.getParameterMap();

	boolean isPostRequest = httpRequest.getMethod().equalsIgnoreCase("POST");
	boolean containsErrorData = httpParameters.containsKey("error");
	boolean containIdToken = httpParameters.containsKey("id_token");
	boolean containsCode = httpParameters.containsKey("code");

	return isPostRequest && containsErrorData || containsCode || containIdToken;
    }

    private boolean isAccessTokenExpired(HttpServletRequest httpRequest) {
	IAuthenticationResult result = SessionManagementHelper.getAuthSessionObject(httpRequest);
	return result.expiresOnDate().before(new Date());
    }

    private boolean isAuthenticated(HttpServletRequest request) {
	return request.getSession().getAttribute(AuthHelper.PRINCIPAL_SESSION_NAME) != null;
    }

    private void updateAuthDataUsingSilentFlow(HttpServletRequest httpRequest, HttpServletResponse httpResponse)
	    throws Throwable {
	IAuthenticationResult authResult = authHelper.getAuthResultBySilentFlow(httpRequest, httpResponse);
	SessionManagementHelper.setSessionPrincipal(httpRequest, authResult);
    }
}