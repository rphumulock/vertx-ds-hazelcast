package com.example.cluster_project.utils;

import com.example.cluster_project.ui.partials.Partials;
import com.example.cluster_project.ui.templates.Index;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class DatastarUtils {

  private static final Logger logger = LoggerFactory.getLogger(DatastarUtils.class);

  /*****************************************************************************************
   *  DATASTAR MERGE TYPES
   *****************************************************************************************/

  public enum MergeTypes {
    MORPH_ELEMENT("morph_element"),
    PREPEND_ELEMENT("prepend_element"),
    APPEND_ELEMENT("append_element"),
    DELETE_ELEMENT("delete_element"),
    AFTER_ELEMENT("after_element"),
    INNER_HTML("inner_html");

    private final String type;

    MergeTypes(String type) {
      this.type = type;
    }

    public String getType() {
      return type;
    }
  }

  /*****************************************************************************************
   *  DATASTAR HELPERS
   *****************************************************************************************/

  public static void setHeaders(HttpServerResponse response) {
    response
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

    response.write(message.toString());
  }

  public static SSEConfig buildConfig(
    String withId,
    String withSelector,
    String withMergeType,
    Number withSettle,
    String withFragment
  ) {
    return new SSEConfig.Builder()
      .withId(withId)
      .withSelector(withSelector)
      .withMergeType(withMergeType)
      .withSettle(withSettle)
      .withFragment(withFragment)
      .build();
  }

  /*****************************************************************************************
   *  NODE CONTAINERS
   *****************************************************************************************/


  /*****************************************************************************************
   * HEAT SENSOR SUBSCRIBE
   *****************************************************************************************/

  public static void addHeatSensor(HttpServerResponse response, String clusterNodeID, String heatSensorID) {
    sendSSE(response, DatastarUtils.buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorsContainer-" + clusterNodeID,
      DatastarUtils.MergeTypes.APPEND_ELEMENT.getType(),
      0,
      Partials.heatSensorTemplate(clusterNodeID, heatSensorID).render()
    ));
  }


  /*****************************************************************************************
   * HEAT SENSOR SUBSCRIBE / UNSUBSCRIBE
   *****************************************************************************************/

  public static void heatSensorStartUpdates(HttpServerResponse response, String clusterNodeID, String heatSensorID) {
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorActionsContainer-" + heatSensorID,
      MergeTypes.PREPEND_ELEMENT.getType(),
      0,
      Partials.heatSensorSubscribeButton(heatSensorID).render()
    ));
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorStartUpdatesButton-" + heatSensorID,
      MergeTypes.MORPH_ELEMENT.getType(),
      0,
      Partials.heatSensorStopUpdates(heatSensorID).render()
    ));
  }

  public static void heatSensorStopUpdates(HttpServerResponse response, String heatSensorID) {
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorSubscribeButton-" + heatSensorID,
      MergeTypes.DELETE_ELEMENT.getType(),
      0,
      "<div></div>"
    ));
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorUnsubscribeButton-" + heatSensorID,
      MergeTypes.DELETE_ELEMENT.getType(),
      0,
      "<div></div>"
    ));
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorUpdatesContainer-" + heatSensorID,
      MergeTypes.DELETE_ELEMENT.getType(),
      0,
      "<div></div>"
    ));
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorStopUpdatesButton-" + heatSensorID,
      MergeTypes.MORPH_ELEMENT.getType(),
      0,
      Partials.heatSensorStartUpdates(heatSensorID).render()
    ));
  }

  public static void heatSensorSubscribe(HttpServerResponse response, String heatSensorID) {
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorSubscribeButton-" + heatSensorID,
      MergeTypes.MORPH_ELEMENT.getType(),
      0,
      Partials.heatSensorUnsubscribeButton(heatSensorID).render()
    ));
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorDataContainer-" + heatSensorID,
      MergeTypes.APPEND_ELEMENT.getType(),
      0,
      Partials.heatSensorUpdatesTemplate(heatSensorID).render()
    ));
  }

  public static void heatSensorUnsubscribe(HttpServerResponse response, String heatSensorID) {
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorUpdatesContainer-" + heatSensorID,
      MergeTypes.DELETE_ELEMENT.getType(),
      0,
      "<div></div>"
    ));
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorUnsubscribeButton-" + heatSensorID,
      DatastarUtils.MergeTypes.MORPH_ELEMENT.getType(),
      0,
      Partials.heatSensorSubscribeButton(heatSensorID).render()
    ));

  }

  public static void consumeSensorData(HttpServerResponse response, JsonObject payload) {
    String heatSensorID = payload.getString("heatSensorID");
    String temp = payload.getString("temp");
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorUpdates-" + heatSensorID,
      DatastarUtils.MergeTypes.MORPH_ELEMENT.getType(),
      0,
      Partials.heatSensorUpdatesTemplate(heatSensorID, temp).render()
    ));
  }

}
