package com.btg.moneymarket.msal.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.text.ParseException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import com.btg.moneymarket.msal.helper.SessionManagementHelper;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.nimbusds.jwt.JWTParser;

/**
 * Controller exposing application endpoints
 */
@Controller
public class AuthPageController {

//    @RequestMapping("/")
//    public String homepage() {
//	return "index";
//    }

//    @RequestMapping("/msal4jsample/secure/aad")
    @RequestMapping("/authSignIn")
    public ModelAndView securePage(HttpServletRequest httpRequest) throws ParseException {
	ModelAndView mav = new ModelAndView("auth_page");

	IAuthenticationResult auth = SessionManagementHelper.getAuthSessionObject(httpRequest);
	String tenantId = JWTParser.parse(auth.idToken()).getJWTClaimsSet().getStringClaim("tid");
	mav.addObject("tenantId", tenantId);

	mav.addObject("account", SessionManagementHelper.getAuthSessionObject(httpRequest).account());

	return mav;
    }

//    @RequestMapping("/msal4jsample/sign_out")
    @RequestMapping("/authSignOut")
    public void signOut(HttpServletRequest httpRequest, HttpServletResponse response) throws IOException {
	httpRequest.getSession().invalidate();
	String endSessionEndpoint = "https://login.microsoftonline.com/common/oauth2/v2.0/logout";
//	String redirectUrl = "http://localhost:8443/msal4jsample/";
	String redirectUrl = "http://localhost:8443/";
	response.sendRedirect(
		endSessionEndpoint + "?post_logout_redirect_uri=" + URLEncoder.encode(redirectUrl, "UTF-8"));
    }
}