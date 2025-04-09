package com.btg.moneymarket.msal;

import java.util.Date;

public class StateData {

    private String nonce;
    private Date expirationDate;

    public StateData(String nonce, Date expirationDate) {
	this.nonce = nonce;
	this.expirationDate = expirationDate;
    }

    public String getNonce() {
	return nonce;
    }

    public Date getExpirationDate() {
	return expirationDate;
    }
}