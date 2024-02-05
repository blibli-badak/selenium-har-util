package com.blibli.oss.qa.util;

import org.openqa.selenium.devtools.v121.network.Network;
import org.openqa.selenium.devtools.v121.network.model.ResponseReceived;

public class ResponseModel {
    ResponseReceived responseReceived;
    Network.GetResponseBodyResponse getResponseBodyResponse;

    public ResponseReceived getResponseReceived() {
        return responseReceived;
    }

    public void setResponseReceived(ResponseReceived responseReceived) {
        this.responseReceived = responseReceived;
    }

    public Network.GetResponseBodyResponse getGetResponseBodyResponse() {
        return getResponseBodyResponse;
    }

    public void setGetResponseBodyResponse(Network.GetResponseBodyResponse getResponseBodyResponse) {
        this.getResponseBodyResponse = getResponseBodyResponse;
    }
}
