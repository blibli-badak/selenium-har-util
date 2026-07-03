package com.blibli.oss.qa.util.services;

import de.sstoehr.harreader.model.HarContent;
import de.sstoehr.harreader.model.HarEntry;
import de.sstoehr.harreader.model.HarHeader;
import de.sstoehr.harreader.model.HarPostData;
import de.sstoehr.harreader.model.HarRequest;
import de.sstoehr.harreader.model.HarResponse;
import de.sstoehr.harreader.model.HarTiming;
import de.sstoehr.harreader.model.HttpMethod;
import org.openqa.selenium.devtools.v148.network.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public class HarEntryConverter {
    private final Request request;
    private final Response response;
    private final LoadingFailed loadingFailed;
    private final ResponseReceivedExtraInfo responseReceivedExtraInfo;
    private final LoadingFinished loadingFinished;

    private final HarEntry harEntry;
    private final long startTime;
    private final String pageRef;
    private final String responseBody;
    private long endTime = 0;
    private Optional<ResourceTiming> timing;

    public HarEntryConverter(Request request,
                             Response response,
                             LoadingFailed loadingFailed,
                             ResponseReceivedExtraInfo responseReceivedExtraInfo,
                             List<Long> time,
                             String pageRef,
                             String responseBody,
                             LoadingFinished loadingFinished) {
        this.loadingFailed = loadingFailed;
        this.responseReceivedExtraInfo = responseReceivedExtraInfo;
        this.loadingFinished = loadingFinished;
        if (time.size() == 0) {
            time.add(0L);
        }
        harEntry = new HarEntry();
        this.request = request;
        this.startTime = time.get(0);
        this.endTime = this.startTime;
        this.response = response;
        this.responseBody = responseBody;
        if (response != null) {
            this.timing = response.getTiming();
            if (timing.isPresent()) {
                ResourceTiming resourceTiming = timing.get();
                if (loadingFinished != null) {
                    double durationSeconds = loadingFinished.getTimestamp().toJson().doubleValue() - resourceTiming.getRequestTime().doubleValue();
                    this.endTime = this.startTime + (long)(durationSeconds * 1000);
                } else {
                    Number receiveHeadersEnd = resourceTiming.getReceiveHeadersEnd();
                    this.endTime = this.startTime + receiveHeadersEnd.longValue();
                }
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
        } else {
            harEntry.setResponse(emptyHarResponse());
            harEntry.setAdditionalField("_resourceType", "other");
        }
        harEntry.setTimings(convertHarTiming());
        harEntry.setPageref(pageRef);
    }

    public HarTiming convertHarTiming() {
        HarTiming harTiming = new HarTiming();
        if (timing == null || timing.isEmpty()) {
            harTiming.setDns(-1);
            harTiming.setConnect(-1);
            harTiming.setSend(0);
            harTiming.setWait(0);
            harTiming.setReceive(0);
            return harTiming;
        }
        
        ResourceTiming t = timing.get();
        long dns = t.getDnsStart().longValue() != -1 && t.getDnsEnd().longValue() != -1
                ? t.getDnsEnd().longValue() - t.getDnsStart().longValue() : -1;
        harTiming.setDns((int) dns);

        long connect = t.getConnectStart().longValue() != -1 && t.getConnectEnd().longValue() != -1
                ? t.getConnectEnd().longValue() - t.getConnectStart().longValue() : -1;
        harTiming.setConnect((int) connect);

        long send = t.getSendStart().longValue() != -1 && t.getSendEnd().longValue() != -1
                ? t.getSendEnd().longValue() - t.getSendStart().longValue() : 0;
        harTiming.setSend((int) Math.max(0, send));

        long sendEnd = t.getSendEnd().longValue() != -1 ? t.getSendEnd().longValue() : 0;
        long receiveHeadersEnd = t.getReceiveHeadersEnd().longValue() != -1 ? t.getReceiveHeadersEnd().longValue() : sendEnd;
        long wait = receiveHeadersEnd - sendEnd;
        harTiming.setWait((int) Math.max(0, wait));

        long receive = 0;
        if (loadingFinished != null) {
            double durationSeconds = loadingFinished.getTimestamp().toJson().doubleValue() - t.getRequestTime().doubleValue();
            long totalOffset = (long)(durationSeconds * 1000);
            receive = totalOffset - receiveHeadersEnd;
        }
        harTiming.setReceive((int) Math.max(0, receive));

        return harTiming;
    }

    private HarResponse convertHarResponse() {
        HarResponse harResponse = new HarResponse();
        harResponse.setStatus(response.getStatus());
        harResponse.setStatusText(response.getStatusText());
        harResponse.setHttpVersion("HTTP/1.1");
        harResponse.setRedirectURL("");
        harResponse.setHeaders(convertHarHeadersResponse(response.getHeaders()));
        harResponse.setContent(setHarContentResponse());
        return harResponse;
    }

    private HarResponse emptyHarResponse() {
        HarResponse harResponse = new HarResponse();
        if (responseReceivedExtraInfo != null) {
            harResponse.setStatus(responseReceivedExtraInfo.getStatusCode());
            harResponse.setStatusText("");
            harResponse.setHttpVersion("");
            harResponse.setRedirectURL("");
            harResponse.setHeaders(convertHarHeadersResponse(responseReceivedExtraInfo.getHeaders()));
            harResponse.setContent(setEmptyHarContentResponse());
            if (loadingFailed != null) {
                harResponse.setAdditionalField("_error", loadingFailed.getErrorText());
                harResponse.setAdditionalField("_fetchedViaServiceWorker", false);
                harResponse.setAdditionalField("_transferSize", 0);
            }
        }else {
            harResponse.setStatus(0);
            harResponse.setStatusText("");
            harResponse.setHttpVersion("HTTP/1.1");
            harResponse.setRedirectURL("");
            harResponse.setHeaders(new ArrayList<>());
            harResponse.setContent(setEmptyHarContentResponse());
        }
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

    private HarContent setEmptyHarContentResponse() {
        HarContent harContent = new HarContent();
        harContent.setSize((long) 0);
        harContent.setMimeType("x-unknown");
        return harContent;
    }

    private List<HarHeader> convertHarHeadersResponse(Headers headers) {
        List<HarHeader> harHeaders = new java.util.ArrayList<>();
        headers.forEach((key, value) -> {
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
        harRequest.setPostData(request.getHasPostData().orElse(false) ? setHarPostData() : null);
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

    private HarPostData setHarPostData() {
        HarPostData harPostData = new HarPostData();
        String postDataText = request.getPostDataEntries()
                .flatMap(entries -> entries.isEmpty() ? java.util.Optional.empty() : entries.get(0).getBytes())
                .orElse("");
        harPostData.setText(postDataText);
        return harPostData;
    }

    public HarEntry getHarEntry() {
        return harEntry;
    }

}
