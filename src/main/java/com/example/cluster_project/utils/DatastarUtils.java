package com.example.cluster_project.utils;

import com.example.cluster_project.ui.partials.Partials;
import io.vertx.core.http.HttpServerResponse;
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

  public static void addNodeContainer(HttpServerResponse response, String nodeDeploymentID) {
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#main",
      MergeTypes.APPEND_ELEMENT.getType(),
      0,
      Partials.nodeContainerTemplate(nodeDeploymentID).render()
    ));
  }

  /*****************************************************************************************
   * HEAT SENSOR SUBSCRIBE
   *****************************************************************************************/

  public static void addHeatSensor(HttpServerResponse response, String nodeDeploymentID, String heatSensorDeploymentID, Long count) {
    if (count == 1) {
      sendSSE(response, DatastarUtils.buildConfig(
        UUID.randomUUID().toString(),
        "#nodeContainer-" + nodeDeploymentID,
        DatastarUtils.MergeTypes.APPEND_ELEMENT.getType(),
        0,
        Partials.heatSensorsContainerTemplate(nodeDeploymentID, heatSensorDeploymentID).render()
      ));
    } else {
      sendSSE(response, DatastarUtils.buildConfig(
        UUID.randomUUID().toString(),
        "#heatSensorsContainer-" + nodeDeploymentID,
        DatastarUtils.MergeTypes.APPEND_ELEMENT.getType(),
        0,
        Partials.heatSensorTemplate(heatSensorDeploymentID).render()
      ));
    }
  }

  /*****************************************************************************************
   *  HEAT SENSOR DATA
   *****************************************************************************************/

  public static void addSensorData(HttpServerResponse response, String heatSensorDeploymentID, String temp) {
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorUpdatesContainer-" + heatSensorDeploymentID,
      DatastarUtils.MergeTypes.DELETE_ELEMENT.getType(),
      0,
      "<div></div>"
    ));
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#unsubscribeHeatSensorContainer-" + heatSensorDeploymentID,
      DatastarUtils.MergeTypes.APPEND_ELEMENT.getType(),
      0,
      Partials.heatSensorUpdateTemplate(heatSensorDeploymentID, temp).render()
    ));
  }

  public static void editSensorData(HttpServerResponse response, String heatSensorDeploymentID, String temp) {
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#heatSensorUpdates-" + heatSensorDeploymentID,
      DatastarUtils.MergeTypes.MORPH_ELEMENT.getType(),
      0,
      Partials.heatSensorUpdateTemplate(heatSensorDeploymentID, temp).render()
    ));
  }

  /*****************************************************************************************
   * HEAT SENSOR SUBSCRIBE / UNSUBSCRIBE
   *****************************************************************************************/

  public static void heatSensorSubscribe(HttpServerResponse response, String heatSensorDeploymentID) {
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#subscribeHeatSensorContainer-" + heatSensorDeploymentID,
      DatastarUtils.MergeTypes.MORPH_ELEMENT.getType(),
      0,
      Partials.onSubscribeHeatSensorTemplate(heatSensorDeploymentID).render()
    ));
  }

  public static void heatSensorUnsubscribe(HttpServerResponse response, String heatSensorDeploymentID) {
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#unsubscribeHeatSensorContainer-" + heatSensorDeploymentID,
      DatastarUtils.MergeTypes.MORPH_ELEMENT.getType(),
      0,
      Partials.subscribeHeatSensorUpdatesTemplate(heatSensorDeploymentID).render()
    ));
  }

}
