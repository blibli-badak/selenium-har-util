package com.blibli.oss.qa.util.model;

import lombok.Getter;
import lombok.Setter;
import org.openqa.selenium.devtools.v137.network.model.Request;
import org.openqa.selenium.devtools.v137.network.model.Response;


import java.util.Date;

@Getter
@Setter
public class RequestResponsePair {
  private Request request;
  private Response response;
  private Date requestOn;
  private String responseBody;

  public RequestResponsePair(Request request, Date requestOn, String responseBody) {
    this.request = request;
    this.requestOn = requestOn;
    this.responseBody = responseBody;
  }

}
