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
    private final HarEntry harEntry;
    private final long startTime;
    private final long endTime;

    private final String CONTENT_TYPE = "Content-Type";

    public HarEntryConverter(HttpRequest httpRequest, HttpResponse httpResponse , List<Long> time) {
        harEntry = new HarEntry();
        this.httpRequest = httpRequest;
        this.httpResponse = httpResponse;
        this.startTime = time.get(0);
        this.endTime = time.get(1);
    }

    public void setup() {
        harEntry.setRequest(convertHarRequest());
        harEntry.setStartedDateTime(new Date(startTime));
        harEntry.setTime((int) (endTime - startTime));
        harEntry.setRequest(convertHarRequest());
        harEntry.setResponse(convertHarResponse());
        harEntry.setTimings(convertHarTiming());
        harEntry.setPageref("Page"); // TODO: change to pageref
    }

    public HarTiming convertHarTiming() {
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

    private HarContent setHarContentResponse() {
        HarContent harContent = new HarContent();
        try {
            harContent.setSize((long) httpResponse.getContent().get().available());
        } catch (IOException e) {
            e.printStackTrace();
            harContent.setSize(0L);
        }
        harContent.setText(convertInputStreamtoString(httpResponse.getContent().get()));
        harContent.setMimeType(Optional.ofNullable(httpResponse.getHeader(CONTENT_TYPE)).orElse("").equals("")? "application/x-www-form-urlencoded" : httpResponse.getHeader(CONTENT_TYPE));

        return harContent;
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
        harRequest.setMethod(HttpMethod.valueOf(httpRequest.getMethod().toString()));
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
        harPostData.setMimeType((Optional.ofNullable(httpRequest.getHeader(CONTENT_TYPE)).orElse("").equalsIgnoreCase("") ? "application/x-www-form-urlencoded" : httpRequest.getHeader(CONTENT_TYPE)));
        harRequest.setPostData(harPostData);
        harRequest.setAdditionalField("content", httpRequest.getContent());
        List<HarHeader> headers = new ArrayList<>();
        httpRequest.getHeaderNames().forEach(h -> {
            HarHeader header = new HarHeader();
            header.setName(h);
            header.setValue(httpRequest.getHeader(h));
            headers.add(header);
        });
        harRequest.setHeaders(headers);
        return harRequest;
    }

    public HarEntry getHarEntry() {
        return harEntry;
    }

}
