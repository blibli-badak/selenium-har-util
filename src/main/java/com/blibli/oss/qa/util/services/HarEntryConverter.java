package com.blibli.oss.qa.util.services;

import de.sstoehr.harreader.model.*;
import org.openqa.selenium.devtools.v104.network.model.Request;
import org.openqa.selenium.devtools.v104.network.model.Response;

import java.util.Date;
import java.util.List;

public class HarEntryConverter {
    private final Request request;
    private final Response response;
    private final HarEntry harEntry;
    private final long time;

    public HarEntryConverter(Request request, Response response, long time) {
        harEntry = new HarEntry();
        this.request = request;
        this.response = response;
        this.time = time;
    }

    public void setup() {
        harEntry.setRequest(convertHarRequest());
        harEntry.setStartedDateTime(new Date(time));
        harEntry.setTime((int) time);
        harEntry.setRequest(convertHarRequest());
        harEntry.setResponse(convertHarResponse());
        harEntry.setTimings(convertHarTiming());
        harEntry.setPageref("Page"); // TODO: change to pageref
    }

    public HarTiming convertHarTiming() {
        HarTiming harTiming = new HarTiming();
        harTiming.setDns(response.getTiming().get().getDnsStart().intValue());
        harTiming.setConnect(response.getTiming().get().getConnectStart().intValue());
        harTiming.setSsl(response.getTiming().get().getSslStart().intValue());
        harTiming.setSend(response.getTiming().get().getSendStart().intValue());
        return harTiming;
    }

    private HarResponse convertHarResponse() {
        HarResponse harResponse = new HarResponse();
        harResponse.setStatus(response.getStatus());
        harResponse.setStatusText(response.getStatusText());
        harResponse.setHttpVersion("HTTP/1.1");
        harResponse.setRedirectURL("");
        harResponse.setHeaders(convertHarHeadersResponse());
        harResponse.setContent(setHarContentResponse());
        return harResponse;
    }

    private HarContent setHarContentResponse() {
        HarContent harContent = new HarContent();
        harContent.setSize((Long) response.getEncodedDataLength());
        harContent.setMimeType(response.getMimeType());
        return harContent;
    }

    private List<HarHeader> convertHarHeadersResponse() {
        List<HarHeader> harHeaders = new java.util.ArrayList<>();
        request.getHeaders().forEach((k, v) -> {
            HarHeader harHeader = new HarHeader();
            harHeader.setName(k);
            harHeader.setValue(String.valueOf(v));
            harHeaders.add(harHeader);
        });
        return harHeaders;
    }

    public HarRequest convertHarRequest() {
        HarRequest harRequest = new HarRequest();
        harRequest.setMethod(HttpMethod.valueOf(request.getMethod()));
        harRequest.setUrl(request.getUrl());
        harRequest.setComment("");
        harRequest.setHttpVersion("HTTP/1.1");
        harRequest.setBodySize((Long) response.getEncodedDataLength());
        return harRequest;
    }

    public HarEntry getHarEntry() {
        return harEntry;
    }

}
