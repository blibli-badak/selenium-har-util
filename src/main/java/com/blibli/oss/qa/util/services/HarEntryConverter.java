package com.blibli.oss.qa.util.services;

import de.sstoehr.harreader.model.*;
import org.openqa.selenium.remote.http.HttpRequest;
import org.openqa.selenium.remote.http.HttpResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public class HarEntryConverter {
    private HttpRequest httpRequest;
    private HttpResponse httpResponse;
    private int counter;
    private HarEntry harEntry;
    private long time;

    public HarEntryConverter(HttpRequest httpRequest, HttpResponse httpResponse , int counter , long time) {
        harEntry = new HarEntry();
        this.httpRequest = httpRequest;
        this.httpResponse = httpResponse;
        this.counter = counter;
        this.time = time;
    }
    public void setup(){
        harEntry.setRequest(convertHarRequest());
        harEntry.setStartedDateTime(new Date(time));
        harEntry.setTime((int) time);
        harEntry.setRequest(convertHarRequest());
        harEntry.setResponse(convertHarResponse());
        harEntry.setTimings(convertHarTiming());
        harEntry.setPageref("Page_0"); // TODO: change to pageref
    }
    public HarTiming convertHarTiming(){
        HarTiming harTiming = new HarTiming();
        harTiming.setBlocked(0);
        harTiming.setDns(0);
        harTiming.setConnect(0);
        harTiming.setSend(0);
        harTiming.setWait(0);
        harTiming.setReceive(0);
        return harTiming;
    }

    private HarResponse convertHarResponse() {
        HarResponse harResponse = new HarResponse();
        harResponse.setStatus(httpResponse.getStatus());
        harResponse.setStatusText((httpResponse.isSuccessful() ? "OK" : "FAILED"));
        harResponse.setHttpVersion("HTTP/1.1");
        harResponse.setRedirectURL("");
        harResponse.setHeaders(convertHarHeadersResponse());
        harResponse.setContent(setHarContentResponse());
        return harResponse;
    }
    private HarContent setHarContentResponse(){
        HarContent harContent = new HarContent();
        try {
            harContent.setSize((long) httpResponse.getContent().get().available());
        } catch (IOException e) {
            e.printStackTrace();
            harContent.setSize(0L);
        }
        harContent.setText(convertInputStreamtoString(httpResponse.getContent().get()));
        harContent.setMimeType(Optional.ofNullable(httpResponse.getHeader("Content-Type")).orElse("").equals("")? "application/x-www-form-urlencoded" : httpResponse.getHeader("Content-Type"));
        return harContent;
    }
    private String convertInputStreamtoString(InputStream inputStream){
        StringBuilder stringBuilder = new StringBuilder();
        try {
            int read;
            while ((read = inputStream.read()) != -1) {
                stringBuilder.append((char)read);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return stringBuilder.toString();
    }

    private List<HarHeader> convertHarHeadersResponse() {
        List<HarHeader> harHeaders = new java.util.ArrayList<>();
        httpResponse.getHeaderNames().forEach(s -> {
            HarHeader harHeader = new HarHeader();
            harHeader.setName(s);
            harHeader.setValue(httpResponse.getHeader(s));
            harHeaders.add(harHeader);
        });
        return harHeaders;
    }


    public HarRequest convertHarRequest() {
        HarRequest harRequest = new HarRequest();
        harRequest.setMethod(HttpMethodConverter.covertHttpMethod(httpRequest.getMethod()));
        harRequest.setUrl(httpRequest.getUri());
        harRequest.setComment("");
        harRequest.setHttpVersion("HTTP/1.1");
        try {
            harRequest.setBodySize((long) httpRequest.getContent().get().available());
        } catch (IOException e) {
            e.printStackTrace();
        }
        HarPostData harPostData = new HarPostData();
        harPostData.setText(convertInputStreamtoString(httpRequest.getContent().get()));
        List<HarPostDataParam> harPostDataParams = new ArrayList<>();
        httpRequest.getQueryParameterNames().forEach(s -> {
            HarPostDataParam harPostDataParam = new HarPostDataParam();
            harPostDataParam.setName(s);
            harPostDataParam.setValue(httpRequest.getQueryParameter(s));
            harPostDataParams.add(harPostDataParam);
        });
        harPostData.setParams(harPostDataParams);
        harPostData.setMimeType((Optional.ofNullable(httpRequest.getHeader("Content-Type")).orElse("").equalsIgnoreCase("") ? "application/x-www-form-urlencoded" : httpRequest.getHeader("Content-Type")));
        harRequest.setPostData(harPostData);
        return harRequest;
    }

    public HarEntry getHarEntry(){
        return harEntry;
    }

}
