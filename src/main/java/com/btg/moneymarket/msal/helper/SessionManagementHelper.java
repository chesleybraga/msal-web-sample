package com.btg.moneymarket.msal.helper;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import com.btg.moneymarket.msal.StateData;
import com.microsoft.aad.msal4j.IAuthenticationResult;

/**
 * Helpers for managing session
 */
@SuppressWarnings("unchecked")
public class SessionManagementHelper {

    public static final String STATE = "state";
    private static final String STATES = "states";
    private static final Integer STATE_TTL = 3600;

    public static final String FAILED_TO_VALIDATE_MESSAGE = "Failed to validate data received from Authorization service - ";

    public static StateData validateState(HttpSession session, String state) throws Exception {
	if (!isEmpty(state)) {
	    StateData stateDataInSession = removeStateFromSession(session, state);
	    if (stateDataInSession != null) {
		return stateDataInSession;
	    }
	}
	throw new Exception(FAILED_TO_VALIDATE_MESSAGE + "could not validate state");
    }

    private static boolean isEmpty(final CharSequence cs) {
	return cs == null || cs.length() == 0;
    }

    private static StateData removeStateFromSession(HttpSession session, String state) {
	Map<String, StateData> states = (Map<String, StateData>) session.getAttribute(STATES);
	if (states != null) {
	    eliminateExpiredStates(states);
	    StateData stateData = states.get(state);
	    if (stateData != null) {
		states.remove(state);
		return stateData;
	    }
	}
	return null;
    }

    private static void eliminateExpiredStates(Map<String, StateData> map) {
	Iterator<Map.Entry<String, StateData>> it = map.entrySet().iterator();

	Date currTime = new Date();
	while (it.hasNext()) {
	    Map.Entry<String, StateData> entry = it.next();
	    long diffInSeconds = TimeUnit.MILLISECONDS
		    .toSeconds(currTime.getTime() - entry.getValue().getExpirationDate().getTime());

	    if (diffInSeconds > STATE_TTL) {
		it.remove();
	    }
	}
    }

    public static void storeStateAndNonceInSession(HttpSession session, String state, String nonce) {
	// state parameter to validate response from Authorization server and nonce
	// parameter to validate idToken
	if (session.getAttribute(STATES) == null) {
	    session.setAttribute(STATES, new HashMap<String, StateData>());
	}
	((Map<String, StateData>) session.getAttribute(STATES)).put(state, new StateData(nonce, new Date()));
    }

    public static void storeTokenCacheInSession(HttpServletRequest httpServletRequest, String tokenCache) {
	httpServletRequest.getSession().setAttribute(AuthHelper.TOKEN_CACHE_SESSION_ATTRIBUTE, tokenCache);
    }

    public static void setSessionPrincipal(HttpServletRequest httpRequest, IAuthenticationResult result) {
	httpRequest.getSession().setAttribute(AuthHelper.PRINCIPAL_SESSION_NAME, result);
    }

    public static void removePrincipalFromSession(HttpServletRequest httpRequest) {
	httpRequest.getSession().removeAttribute(AuthHelper.PRINCIPAL_SESSION_NAME);
    }

    public static IAuthenticationResult getAuthSessionObject(HttpServletRequest request) {
	Object principalSession = request.getSession().getAttribute(AuthHelper.PRINCIPAL_SESSION_NAME);
	if (principalSession instanceof IAuthenticationResult) {
	    return (IAuthenticationResult) principalSession;
	} else {
	    throw new IllegalStateException("Session does not contain principal session name");
	}
    }
}