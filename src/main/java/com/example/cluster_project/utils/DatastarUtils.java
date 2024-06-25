package com.example.cluster_project.utils;

import com.example.cluster_project.ui.partials.Partials;
import io.vertx.core.http.HttpServerResponse;
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

  /*****************************************************************************************
   *  NODE CONTAINERS
   *****************************************************************************************/


  /*****************************************************************************************
   * HEAT SENSOR SUBSCRIBE
   *****************************************************************************************/

  public static void addHeatSensor(HttpServerResponse response, JsonObject payload) {
    String clusterNodeID = payload.getString("clusterNodeID");
    String deploymentID = payload.getString("deploymentID");
    sendSSE(response, DatastarUtils.buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorsContainer-" + clusterNodeID,
      DatastarUtils.MergeTypes.APPEND_ELEMENT.getType(),
      0,
      Partials.heatSensorTemplate(clusterNodeID, deploymentID).render(),
      false
    ));
  }

  public static void removeHeatSensor(HttpServerResponse response, JsonObject payload) {
    String clusterNodeID = payload.getString("clusterNodeID");
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
    String clusterNodeID = payload.getString("clusterNodeID");
    String deploymentID = payload.getString("deploymentID");
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorActionsContainer-" + deploymentID,
      MergeTypes.PREPEND_ELEMENT.getType(),
      0,
      Partials.heatSensorSubscribeButton(clusterNodeID, deploymentID).render(),
      false
    ));
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorStartUpdatesButton-" + deploymentID,
      MergeTypes.MORPH_ELEMENT.getType(),
      0,
      Partials.heatSensorStopUpdates(clusterNodeID, deploymentID).render(),
      false
    ));
  }

  public static void heatSensorStopUpdates(HttpServerResponse response, JsonObject payload) {
    String clusterNodeID = payload.getString("clusterNodeID");
    String deploymentID = payload.getString("deploymentID");
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorSubscribeButton-" + deploymentID,
      MergeTypes.DELETE_ELEMENT.getType(),
      0,
      "<div></div>",
      false
    ));
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorUnsubscribeButton-" + deploymentID,
      MergeTypes.DELETE_ELEMENT.getType(),
      0,
      "<div></div>",
      false
    ));
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorUpdatesContainer-" + deploymentID,
      MergeTypes.DELETE_ELEMENT.getType(),
      0,
      "<div></div>",
      false
    ));
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorStopUpdatesButton-" + deploymentID,
      MergeTypes.MORPH_ELEMENT.getType(),
      0,
      Partials.heatSensorStartUpdates(clusterNodeID, deploymentID).render(),
      false
    ));
  }

  public static void heatSensorSubscribe(HttpServerResponse response, String clusterNodeID, String deploymentID) {
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorSubscribeButton-" + deploymentID,
      MergeTypes.MORPH_ELEMENT.getType(),
      0,
      Partials.heatSensorUnsubscribeButton(clusterNodeID, deploymentID).render(),
      false
    ));
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorDataContainer-" + deploymentID,
      MergeTypes.APPEND_ELEMENT.getType(),
      0,
      Partials.heatSensorUpdatesTemplate(clusterNodeID, deploymentID).render(),
      false
    ));
  }

  public static void heatSensorUnsubscribe(HttpServerResponse response, String clusterNodeID, String deploymentID) {
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorUpdatesContainer-" + deploymentID,
      MergeTypes.DELETE_ELEMENT.getType(),
      0,
      "<div></div>",
      false
    ));
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorUnsubscribeButton-" + deploymentID,
      DatastarUtils.MergeTypes.MORPH_ELEMENT.getType(),
      0,
      Partials.heatSensorSubscribeButton(clusterNodeID, deploymentID).render(),
      false
    ));

  }

  public static void consumeSensorData(HttpServerResponse response, JsonObject payload) {
    String clusterNodeID = payload.getString("clusterNodeID");
    String deploymentID = payload.getString("deploymentID");
    String temperature = payload.getString("temperature");
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorUpdates-" + deploymentID,
      DatastarUtils.MergeTypes.MORPH_ELEMENT.getType(),
      0,
      Partials.heatSensorDataTemplate(clusterNodeID, deploymentID, temperature).render(),
      false
    ));
  }

  public static void heatSensorsContainer(HttpServerResponse response, String clusterNodeID) {
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorsContainer-" + clusterNodeID,
      MergeTypes.DELETE_ELEMENT.getType(),
      0,
      "<div></div>",
      false
    ));
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#main",
      MergeTypes.APPEND_ELEMENT.getType(),
      0,
      Partials.heatSensorsContainerTemplate(clusterNodeID).render(),
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

}
