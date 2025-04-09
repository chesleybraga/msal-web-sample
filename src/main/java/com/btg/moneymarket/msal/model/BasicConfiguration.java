package com.btg.moneymarket.msal.model;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Object containing configuration data for the application. Spring will
 * automatically wire the values by grabbing them from application.properties
 * file
 */
@Component
@ConfigurationProperties("msal")
public class BasicConfiguration {

    private String clientId;
    private String secretKey;
    private String redirectUriSignin;
    private String authority;

    public String getClientId() {
	return clientId;
    }

    public void setClientId(String clientId) {
	this.clientId = clientId;
    }

    public String getSecretKey() {
	return secretKey;
    }

    public void setSecretKey(String secretKey) {
	this.secretKey = secretKey;
    }

    public String getRedirectUriSignin() {
	return redirectUriSignin;
    }

    public void setRedirectUriSignin(String redirectUriSignin) {
	this.redirectUriSignin = redirectUriSignin;
    }

    public String getAuthority() {
	if (!authority.endsWith("/")) {
	    authority += "/";
	}
	return authority;
    }

    public void setAuthority(String authority) {
	this.authority = authority;
    }
}