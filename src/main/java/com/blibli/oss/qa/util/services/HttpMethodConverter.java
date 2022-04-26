package com.blibli.oss.qa.util.services;

import org.openqa.selenium.remote.http.HttpMethod;

public class HttpMethodConverter {

    public static de.sstoehr.harreader.model.HttpMethod covertHttpMethod(HttpMethod seleniumHttpMethod){
        switch (seleniumHttpMethod){
            case POST:
                return de.sstoehr.harreader.model.HttpMethod.POST;
            case DELETE:
                return de.sstoehr.harreader.model.HttpMethod.DELETE;
            case OPTIONS:
                return de.sstoehr.harreader.model.HttpMethod.OPTIONS;
            default:
                return de.sstoehr.harreader.model.HttpMethod.GET;
        }
    }

}
