package com.example.cluster_project.utils;

import com.example.cluster_project.ui.partials.Partials;
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
  }

//  public static void appendHeatSensorContainer(HttpServerResponse response, String nodeDeploymentID) {
//    sendSSE(response, DatastarUtils.buildConfig(
//      UUID.randomUUID().toString(),
//      "#main",
//      DatastarUtils.MergeTypes.APPEND_ELEMENT.getType(),
//      0,
//      Partials.sensorContainer(nodeDeploymentID).render(),
//      false
//    ));
//  }

  public static void appendNewHeatSensor(HttpServerResponse response, String sensorDeploymentID) {
    sendSSE(response, DatastarUtils.buildConfig(
      UUID.randomUUID().toString(),
      "#sensorContainer",
      DatastarUtils.MergeTypes.APPEND_ELEMENT.getType(),
      0,
      Partials.sensor(sensorDeploymentID).render()
    ));
  }

  // Sensor Data
  public static void addSensorData(HttpServerResponse response, String deploymentID, String id, String temp) {
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#" + id,
      DatastarUtils.MergeTypes.DELETE_ELEMENT.getType(),
      0,
      "<div></div>"
    ));
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#sensorUpdatesContainer",
      DatastarUtils.MergeTypes.APPEND_ELEMENT.getType(),
      0,
      Partials.sensorUpdate(deploymentID, id, temp).render()
    ));
  }

  public static void editSensorData(HttpServerResponse response, String deploymentID, String id, String temp) {
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#" + id,
      DatastarUtils.MergeTypes.MORPH_ELEMENT.getType(),
      0,
      Partials.sensorUpdate(deploymentID, id, temp).render()
    ));
  }

  // Sensor Data
  public static void addNodeContainer(HttpServerResponse response, String nodeDeploymentID) {
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#main",
      MergeTypes.APPEND_ELEMENT.getType(),
      0,
      Partials.nodeContainerTemplate(nodeDeploymentID).render()
    ));
  }


  public static void addHeatSensorsContainer(HttpServerResponse response, String nodeDeploymentID) {
    sendSSE(response, DatastarUtils.buildConfig(
      UUID.randomUUID().toString(),
      "#nodeContainer-" + nodeDeploymentID,
      DatastarUtils.MergeTypes.APPEND_ELEMENT.getType(),
      0,
      Partials.heatSensorsContainerTemplate(nodeDeploymentID).render()
    ));
  }

  public static void addHeatSensor(HttpServerResponse response, String nodeDeploymentID, String heatSensorDeploymentID) {
    sendSSE(response, DatastarUtils.buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorsContainer-" + nodeDeploymentID,
      DatastarUtils.MergeTypes.APPEND_ELEMENT.getType(),
      0,
      Partials.heatSensorTemplate(heatSensorDeploymentID).render()
    ));
  }

  public static void subscribeHeatSensorTemplate(HttpServerResponse response, String heatSensorDeploymentID) {
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorContainer-" + heatSensorDeploymentID,
      DatastarUtils.MergeTypes.MORPH_ELEMENT.getType(),
      0,
      Partials.unsubscribeSensorUpdates(heatSensorDeploymentID).render()
    ));
  }

  public static void unsubscribeHeatSensorTemplate(HttpServerResponse response, String sensorID) {
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#unsubscribeContainer",
      DatastarUtils.MergeTypes.MORPH_ELEMENT.getType(),
      0,
      Partials.subscribeHeatSensorUpdatesTemplate(sensorID).render()
    ));
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

}
