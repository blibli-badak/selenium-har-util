package com.blibli.oss.qa.util.model;

import lombok.Getter;
import org.openqa.selenium.devtools.v137.network.model.LoadingFailed;
import org.openqa.selenium.devtools.v137.network.model.Request;
import org.openqa.selenium.devtools.v137.network.model.Response;
import org.openqa.selenium.devtools.v137.network.model.ResponseReceivedExtraInfo;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Getter
public class RequestResponseStorage {
  private final List<RequestResponsePair> requestResponsePairs;

  public RequestResponseStorage() {
    this.requestResponsePairs = new ArrayList<>();
  }

  public void addRequest(Request request, Date requestOn) {
    requestResponsePairs.add(new RequestResponsePair(request, requestOn, null));
  }

  public void addRequest(String requestId, Request request, Date requestOn) {
    requestResponsePairs.add(new RequestResponsePair(requestId, request, requestOn, null));
  }

  public void addResponse(Response response, String responseBody) {
      for (RequestResponsePair requestResponsePair : requestResponsePairs) {
          if (requestResponsePair.getRequest().getUrl().equals(response.getUrl())) {
              requestResponsePair.setResponse(response);
              requestResponsePair.setResponseBody(responseBody);
              break;
          }
      }
  }

  public void addLoadingFailed(LoadingFailed loadingFailed){
      for (RequestResponsePair requestResponsePair : requestResponsePairs) {
          if (requestResponsePair.getRequestId().equals(loadingFailed.getRequestId().toString())) {
              requestResponsePair.setLoadingFailed(loadingFailed);
              break;
          }
      }
  }

  public void addresponseReceivedExtraInfo(ResponseReceivedExtraInfo responseReceivedExtraInfoConsumer) {
      for (RequestResponsePair requestResponsePair : requestResponsePairs) {
          if (requestResponsePair.getRequestId().equals(responseReceivedExtraInfoConsumer.getRequestId().toString())) {
              requestResponsePair.setResponseReceivedExtraInfo(responseReceivedExtraInfoConsumer);
              break;
          }
      }
  }

}

