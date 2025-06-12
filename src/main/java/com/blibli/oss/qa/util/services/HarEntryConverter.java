package com.blibli.oss.qa.util.services;

import de.sstoehr.harreader.model.*;
import lombok.Getter;
import org.openqa.selenium.devtools.v137.network.model.Request;
import org.openqa.selenium.devtools.v137.network.model.ResourceTiming;
import org.openqa.selenium.devtools.v137.network.model.Response;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class HarEntryConverter {
    private final Request request;
    private final Response response;
    @Getter
    private HarEntry harEntry;
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
        if (time.isEmpty()) {
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
        harEntry = HarEntry.builder()
                .request(convertHarRequest())
                .startedDateTime(ZonedDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneId.systemDefault()))
                .time((int) (endTime - startTime))
                .response(response != null ? convertHarResponse() : emptyHarResponse())
                .timings(convertHarTiming())
                .pageref(pageRef)
                .build();
    }

    public HarTiming convertHarTiming() {
        HarTiming harTiming;
        if (timing == null || timing.isEmpty()) {
            harTiming = HarTiming.builder()
                    .dns(-1L)
                    .connect(-1L)
                    .send(-1L)
                    .waitTime(-1L)
                    .receive(-1L)
                    .build();
            return harTiming;
        }
        harTiming = HarTiming.builder()
                .dns(timing.get().getDnsStart().longValue())
                .connect(timing.get().getConnectStart().longValue())
                .send(timing.get().getSendStart().longValue())
                .waitTime(timing.get().getSendEnd().longValue() - timing.get().getSendStart().longValue())
                .receive(timing.get().getReceiveHeadersEnd().longValue() - timing.get().getSendEnd().longValue())
                .build();
        return harTiming;
    }

    private HarResponse convertHarResponse() {
        return HarResponse.builder()
                .status(response.getStatus())
                .statusText(response.getStatusText())
                .httpVersion("HTTP/1.1")
                .redirectURL("")
                .headers(convertHarHeadersResponse())
                .content(setHarContentResponse())
                .build();
    }

    private HarResponse emptyHarResponse() {
        return HarResponse.builder()
                .status(0)
                .statusText("")
                .httpVersion("HTTP/1.1")
                .redirectURL("")
                .headers(new ArrayList<>())
                .content(setEmptyHarContentResponse())
                .build();
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
        return HarContent.builder()
                .size((long) response.getEncodedDataLength())
                .text(responseBody)
                .mimeType(response.getMimeType())
                .build();
    }

    private HarContent setEmptyHarContentResponse() {
        return HarContent.builder()
                .size((long) 0)
                .mimeType("text/plain")
                .build();
    }

    private List<HarHeader> convertHarHeadersResponse() {
        List<HarHeader> harHeaders = new java.util.ArrayList<>();
        response.getHeaders().forEach((key, value) -> {
            HarHeader harHeader = HarHeader.builder()
                    .name(key)
                    .value(value.toString())
                    .build();
            harHeaders.add(harHeader);
        });
        return harHeaders;
    }

    public HarRequest convertHarRequest() {
        List<HarHeader> headers = new ArrayList<>();
        request.getHeaders().forEach((key, value) -> {
            HarHeader header = HarHeader.builder()
                    .name(key)
                    .value(value.toString())
                    .build();
            headers.add(header);
        });
        return HarRequest.builder()
                .method(String.valueOf(HttpMethod.valueOf(request.getMethod())))
                .url(request.getUrl())
                .comment("")
                .httpVersion("HTTP/1.1")
                .postData(request.getHasPostData().orElse(false) ? setHarPostData() : new HarPostData())
                .headers(headers)
                .build();
    }

    private HarPostData setHarPostData() {
        return HarPostData.builder()
                .text(request.getPostData().get())
                .build();
    }

}
