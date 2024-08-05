package com.blibli.oss.qa.util.services;

import de.sstoehr.harreader.model.HarContent;
import de.sstoehr.harreader.model.HarEntry;
import de.sstoehr.harreader.model.HarHeader;
import de.sstoehr.harreader.model.HarPostData;
import de.sstoehr.harreader.model.HarRequest;
import de.sstoehr.harreader.model.HarResponse;
import de.sstoehr.harreader.model.HarTiming;
import de.sstoehr.harreader.model.HttpMethod;
import org.openqa.selenium.devtools.v125.network.model.Request;
import org.openqa.selenium.devtools.v125.network.model.ResourceTiming;
import org.openqa.selenium.devtools.v125.network.model.Response;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public class HarEntryConverter {
    private final Request request;
    private final Response response;
    private final HarEntry harEntry;
    private final long startTime;
    private final String pageRef;
    private final String responseBody;
    private long endTime = 0;
    private Optional<ResourceTiming> timing;

    public HarEntryConverter(Request request,
                             Response response,
                             List<Long> time,
                             String pageRef,
                             String responseBody) {
        if (time.size() == 0) {
            time.add(0L);
        }
        harEntry = new HarEntry();
        this.request = request;
        this.startTime = time.get(0);
        this.response = response;
        this.responseBody = responseBody;
        if (response != null) {
            this.timing = response.getTiming();
            if (timing.isPresent()) {
                ResourceTiming resourceTiming = timing.get();
                Number receiveHeadersEnd = resourceTiming.getReceiveHeadersEnd();
                this.endTime = time.get(0) + receiveHeadersEnd.longValue();
            }
        }
        this.pageRef = pageRef;
    }

    public void setup() {
        harEntry.setRequest(convertHarRequest());
        harEntry.setStartedDateTime(new Date(startTime));
        harEntry.setTime((int) (endTime - startTime));
        harEntry.setRequest(convertHarRequest());
        if (response != null) {
            harEntry.setResponse(convertHarResponse());
        }
        harEntry.setTimings(convertHarTiming());
        harEntry.setPageref(pageRef);
    }

    public HarTiming convertHarTiming() {
        HarTiming harTiming = new HarTiming();
        if (timing == null || timing.isEmpty()) {
            return harTiming;
        }
        harTiming.setDns(timing.get().getDnsStart().intValue());
        harTiming.setConnect(timing.get().getConnectStart().intValue());
        harTiming.setSend(timing.get().getSendStart().intValue());
        harTiming.setWait(
                timing.get().getSendEnd().intValue() - timing.get().getSendStart().intValue());
        harTiming.setReceive(
                timing.get().getReceiveHeadersEnd().intValue() - timing.get().getSendEnd().intValue());
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

    private String convertInputStreamtoString(InputStream inputStream) {
        StringBuilder stringBuilder = new StringBuilder();
        try {
            int read;
            while ((read = inputStream.read()) != -1) {
                stringBuilder.append((char) read);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return stringBuilder.toString();
    }

    private HarContent setHarContentResponse() {
        HarContent harContent = new HarContent();
        harContent.setSize((long) response.getEncodedDataLength());
        harContent.setText(responseBody);
        harContent.setMimeType(response.getMimeType());
        return harContent;
    }

    private List<HarHeader> convertHarHeadersResponse() {
        List<HarHeader> harHeaders = new java.util.ArrayList<>();
        response.getHeaders().forEach((key, value) -> {
            HarHeader harHeader = new HarHeader();
            harHeader.setName(key);
            harHeader.setValue(value.toString());
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
        HarPostData harPostData = new HarPostData();
        harRequest.setPostData(harPostData);
        List<HarHeader> headers = new ArrayList<>();
        request.getHeaders().entrySet().forEach(entry -> {
            HarHeader header = new HarHeader();
            header.setName(entry.getKey());
            header.setValue(entry.getValue().toString());
            headers.add(header);
        });
        harRequest.setHeaders(headers);
        return harRequest;
    }

    public HarEntry getHarEntry() {
        return harEntry;
    }

}
