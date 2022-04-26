package com.blibli.oss.qa.util.model;

import lombok.Data;
import org.openqa.selenium.remote.http.HttpRequest;
import org.openqa.selenium.remote.http.HttpResponse;

@Data
public class HarModel {
    private HttpRequest httpRequest;
    private HttpResponse httpResponse;

    public HarModel(HttpRequest httpRequest, HttpResponse httpResponse) {
        this.httpRequest = httpRequest;
        this.httpResponse = httpResponse;
    }
}
