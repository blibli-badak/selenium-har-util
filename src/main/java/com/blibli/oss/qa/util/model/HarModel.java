package com.blibli.oss.qa.util.model;

import lombok.Data;
import org.openqa.selenium.devtools.v106.network.model.Request;
import org.openqa.selenium.devtools.v106.network.model.Response;

@Data
public class HarModel {
    private Request request;

    private Response response;

    public HarModel(Request request, Response response) {
        this.request = request;
        this.response = response;
    }

    public HarModel(Request request) {
        this.request = request;
    }

    public HarModel(Response response) {
        this.response = response;
    }
}
