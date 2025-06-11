package com.blibli.oss.qa.util.model;

import lombok.Getter;
import org.openqa.selenium.devtools.v137.network.model.Request;
import org.openqa.selenium.devtools.v137.network.model.Response;

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

  public void addResponse(Response response, String responseBody) {
    for (int i = 0; i < requestResponsePairs.size(); i++) {
      if (requestResponsePairs.get(i).getRequest().getUrl().equals(response.getUrl())) {
        requestResponsePairs.get(i).setResponse(response);
        requestResponsePairs.get(i).setResponseBody(responseBody);
        break;
      }
    }
  }
}

