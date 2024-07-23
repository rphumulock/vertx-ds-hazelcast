package com.example.cluster_project.utils;

import com.example.cluster_project.ui.Partials;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;

import java.util.UUID;

public class DatastarUtils {

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

    message.append("data: vt false").append("\n");

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
    String withFragment,
    Boolean withVT
  ) {
    return new SSEConfig.Builder()
      .withId(withId)
      .withSelector(withSelector)
      .withMergeType(withMergeType)
      .withSettle(withSettle)
      .withFragment(withFragment)
      .withVT(withVT)
      .build();
  }

  public static void heatSensorsContainer(HttpServerResponse response, String clusterID) {
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorsContainer-" + clusterID,
      MergeTypes.DELETE_ELEMENT.getType(),
      0,
      "<div></div>",
      false
    ));
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#sensorsContainer",
      MergeTypes.APPEND_ELEMENT.getType(),
      0,
      Partials.heatSensorsContainerTemplate(clusterID).render(),
      false
    ));
  }

  public static void manageHeatSensors(HttpServerResponse response) {
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#manageHeatSensorsButton",
      MergeTypes.DELETE_ELEMENT.getType(),
      0,
      "<div></div>",
      false
    ));
  }

  /*****************************************************************************************
   * HEAT SENSOR SUBSCRIBE
   *****************************************************************************************/

  public static void addHeatSensor(HttpServerResponse response, String clusterID, String deploymentID, boolean active) {
    sendSSE(response, DatastarUtils.buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorsContainer-" + clusterID,
      DatastarUtils.MergeTypes.APPEND_ELEMENT.getType(),
      0,
      Partials.heatSensorTemplate(clusterID, deploymentID, active).render(),
      false
    ));
  }

  public static void addHeatSensorPayload(HttpServerResponse response, JsonObject payload) {
    String clusterID = payload.getString("clusterID");
    String deploymentID = payload.getString("deploymentID");
    sendSSE(response, DatastarUtils.buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorsContainer-" + clusterID,
      DatastarUtils.MergeTypes.APPEND_ELEMENT.getType(),
      0,
      Partials.heatSensorTemplate(clusterID, deploymentID, false).render(),
      false
    ));
  }

  public static void removeHeatSensor(HttpServerResponse response, JsonObject payload) {
    String deploymentID = payload.getString("deploymentID");
    sendSSE(response, DatastarUtils.buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorContainer-" + deploymentID,
      MergeTypes.DELETE_ELEMENT.getType(),
      0,
      "<div></div>",
      false

    ));
  }

  /*****************************************************************************************
   * HEAT SENSOR SUBSCRIBE / UNSUBSCRIBE
   *****************************************************************************************/

  public static void heatSensorStartUpdates(HttpServerResponse response, JsonObject payload) {
    String clusterID = payload.getString("clusterID");
    String deploymentID = payload.getString("deploymentID");
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorStartUpdatesButton-" + deploymentID,
      MergeTypes.MORPH_ELEMENT.getType(),
      0,
      Partials.heatSensorStopUpdates(clusterID, deploymentID).render(),
      false
    ));
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorContainer-" + deploymentID,
      MergeTypes.APPEND_ELEMENT.getType(),
      0,
      Partials.heatSensorUpdatesTemplate(deploymentID).render(),
      false
    ));
  }

  public static void heatSensorStopUpdates(HttpServerResponse response, JsonObject payload) {
    String clusterID = payload.getString("clusterID");
    String deploymentID = payload.getString("deploymentID");
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorUpdatesContainer-" + deploymentID,
      MergeTypes.DELETE_ELEMENT.getType(),
      0,
      null,
      false
    ));
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorStopUpdatesButton-" + deploymentID,
      MergeTypes.MORPH_ELEMENT.getType(),
      0,
      Partials.heatSensorStartUpdates(clusterID, deploymentID).render(),
      false
    ));
  }

  public static void consumeSensorData(HttpServerResponse response, JsonObject payload) {
    String deploymentID = payload.getString("deploymentID");
    String temperature = payload.getString("temperature");
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorUpdates-" + deploymentID,
      DatastarUtils.MergeTypes.MORPH_ELEMENT.getType(),
      0,
      Partials.heatSensorDataTemplate(deploymentID, temperature).render(),
      false
    ));
  }

}
