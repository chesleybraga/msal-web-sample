package com.btg.moneymarket.msal.controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * Endpoint where errors will be redirected
 */
@Controller
public class ErrorHandlerController implements ErrorController {

    private static final String PATH = "/error";

    @RequestMapping(value = PATH)
    public ModelAndView returnErrorPage(HttpServletRequest req, HttpServletResponse response) {
	ModelAndView mav = new ModelAndView("error");
	mav.addObject("error", req.getAttribute("error"));
	return mav;
    }

//    @Override
    public String getErrorPath() {
	return PATH;
    }
}
