package com.example.cluster_project.utils;

import io.vertx.core.http.HttpServerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class DatastarUtils {

  private static final Logger logger = LoggerFactory.getLogger(DatastarUtils.class);

  public enum MergeTypes {
    MORPH_ELEMENT("morph_element"),
    APPEND_ELEMENT("append_element"),
    DELETE_ELEMENT("delete_element"),
    INNER_HTML("inner_html");

    private final String type;

    MergeTypes(String type) {
      this.type = type;
    }

    public String getType() {
      return type;
    }
  }

  public static HttpServerResponse setHeaders(HttpServerResponse response) {
    return response
      .putHeader("Cache-Control", "no-cache")
      .putHeader("Content-Type", "text/event-stream")
      .putHeader("Connection", "keep-alive")
      .setChunked(true);
  }

  public static void sendHtmlResponse(HttpServerResponse response, String htmlContent) {
    response.putHeader("Content-Type", "text/html; charset=utf-8") // Set the content type to HTML
      .end(htmlContent); // Send the provided HTML content and close the response
  }

  public static void sendSSE(HttpServerResponse response, SSEConfig config) {
    StringBuilder message = new StringBuilder();
    message.append("event: datastar-fragment\n");

    String id = config.getId();
    if (id != null) {
      message.append("id: ").append(UUID.randomUUID()).append("\n");
    }

    String selector = config.getSelector();
    if (selector != null && !selector.isEmpty()) {
      message.append("data: selector ").append(selector).append("\n");
    }

    String mergeType = config.getMergeType();
    if (mergeType != null && !mergeType.isEmpty()) {
      message.append("data: merge ").append(mergeType).append("\n");
    }

    Number settle = config.getSettle();
    if (settle != null) {
      message.append("data: settle ").append(settle).append("\n");
    }

    String fragment = config.getFragment();
    if (fragment != null) {
      message.append("data: fragment ").append(fragment);
    }

    message.append("\n\n");

//    logger.info("\n\n{}", message);

    response.write(message.toString());

    if (config.isEnd()) {
      response.end();
    }
  }

}
