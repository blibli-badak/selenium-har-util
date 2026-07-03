package com.blibli.oss.qa.util.model;

import lombok.Getter;
import lombok.Setter;
import org.openqa.selenium.devtools.v137.network.model.*;


import java.util.Date;

@Getter
@Setter
public class RequestResponsePair {
  private String requestId;
  private Request request;
  private RequestWillBeSent requestWillBeSent;
  private RequestWillBeSentExtraInfo requestWillBeSentExtraInfo;
  private Response response;
  private ResponseReceivedExtraInfo responseReceivedExtraInfo;
  private LoadingFailed loadingFailed;
  private LoadingFinished loadingFinished;
  private Date requestOn;
  private String responseBody;

  public RequestResponsePair(Request request, Date requestOn, String responseBody) {
    this.request = request;
    this.requestOn = requestOn;
    this.responseBody = responseBody;
  }

  public RequestResponsePair(String requestId, Request request, Date requestOn, String responseBody) {
    this.requestId = requestId;
    this.request = request;
    this.requestOn = requestOn;
    this.responseBody = responseBody;
  }

}
