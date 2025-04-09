package com.btg.moneymarket.msal.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = { "com.btg.moneymarket" })
public class MsalWebSampleApplication {

    public static void main(String[] args) {
	SpringApplication.run(MsalWebSampleApplication.class, args);
    }
}