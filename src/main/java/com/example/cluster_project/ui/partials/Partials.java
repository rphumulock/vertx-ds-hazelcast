package com.example.cluster_project.ui.partials;

import io.vertx.core.json.JsonObject;
import j2html.tags.DomContent;

import static j2html.TagCreator.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Partials {

  private static final Logger logger = LoggerFactory.getLogger(Partials.class);

  public static String indexTemplate(String clusterNodeID) {
    var title = "Cluster: " + clusterNodeID;
    var store = new JsonObject();
    return document(html(
        sharedHead(title),
        body(
          h4("Current Node: " + clusterNodeID),
          main(
            Partials.heatSensors()
          ).
            withClass("container").
            withId("main").
            withData("store", store.encode())
        )
      )
    );
  }

  public static DomContent sharedHead(String title) {
    return head(
      title(title),
      script().withSrc("https://cdn.jsdelivr.net/npm/@sudodevnull/datastar").isDefer().withType("module"),
      link().withRel("icon").withType("image/x-icon").withHref("favicon.ico")
    );
  }

  /*****************************************************************************************
   *  SUBSCRIBE / UNSUBSCRIBE
   *****************************************************************************************/

  public static DomContent heatSensorTemplate(String clusterNodeID, String heatSensorID) {
    return div(
      div(
        div()
          .withText("Heat Sensor: " + heatSensorID)
          .withId("heatSensor-" + heatSensorID),
        heatSensorActions(clusterNodeID, heatSensorID)
      )
        .withStyle("display: flex; justify-content: space-between;")
        .withId("heatSensorControlsContainer-" + heatSensorID),
      div()
        .withId("heatSensorDataContainer-" + heatSensorID)
    )
      .withStyle("border: 2px solid black; margin: 5px 0px;")
      .withId("heatSensorContainer-" + heatSensorID);
  }

  public static DomContent heatSensorActions(String clusterNodeID, String heatSensorID) {
    return div(
      heatSensorStartUpdates(clusterNodeID, heatSensorID),
      heatSensorUndeployButton(clusterNodeID, heatSensorID)
    )
      .withId("heatSensorActionsContainer-" + heatSensorID)
      .withStyle("display: flex; align-items: center;");
  }

  public static DomContent heatSensorDeployButton(String clusterNodeID) {
    return button()
      .withText("Deploy")
      .withStyle("margin: 0px 5px;")
      .withData("on-click", "$$get('/heatSensor/" + clusterNodeID + "/deploy')");
  }

  public static DomContent heatSensorUndeployButton(String clusterNodeID, String heatSensorID) {
    return button()
      .withText("Undeploy")
      .withData("on-click", "$$get('/heatSensor/" + clusterNodeID + "/" + heatSensorID + "/undeploy')");
  }

  public static DomContent heatSensorStartUpdates(String clusterNodeID, String heatSensorID) {
    return button()
      .withText("Start Updates")
      .withData(
        "on-click",
        "$$get('/heatSensor/" + clusterNodeID + "/" + heatSensorID + "/startUpdates')"
      )
      .withId("heatSensorStartUpdatesButton-" + heatSensorID);
  }

  public static DomContent heatSensorStopUpdates(String clusterNodeID, String heatSensorID) {
    return button()
      .withText("Stop Updates")
      .withData(
        "on-click",
        "$$get('/heatSensor/" + clusterNodeID + "/" + heatSensorID + "/stopUpdates')"
      )
      .withId("heatSensorStopUpdatesButton-" + heatSensorID);
  }

  public static DomContent heatSensorSubscribeButton(String clusterNodeID, String heatSensorID) {
    return button()
      .withText("Subscribe")
      .withData(
        "on-click",
        "$$get('/heatSensor/" + clusterNodeID + "/" + heatSensorID + "/subscribe')"
      )
      .withId("heatSensorSubscribeButton-" + heatSensorID);

  }

  public static DomContent heatSensorUnsubscribeButton(String clusterNodeID, String heatSensorID) {
    return
      button()
        .withText("Unsubscribe")
        .withData(
          "on-click",
          "$$get('/heatSensor/" + clusterNodeID + "/" + heatSensorID + "/unsubscribe')"
        )
        .withId("heatSensorUnsubscribeButton-" + heatSensorID);
  }

  public static DomContent heatSensorUpdatesTemplate(String clusterNodeID, String heatSensorID) {
    return div(
      text("Temperature:"),
      div()
        .withId("heatSensorUpdates-" + heatSensorID)
    )
      .withStyle("display: flex; gap: 5px; border: 1px dotted black; font-size: small; ")
      .withId("heatSensorUpdatesContainer-" + heatSensorID);
  }

  public static DomContent heatSensorDataTemplate(String cluserNodeID, String heatSensorID, String temperature) {
    logger.debug("SensorData from: heatSensorID: {} temp: {}", heatSensorID, temperature);
    return div()
      .withText(temperature)
      .withId("heatSensorUpdates-" + heatSensorID);
  }

  /*****************************************************************************************
   *  HEAT SENSOR CONTAINERS
   *****************************************************************************************/

  public static DomContent heatSensorsContainerTemplate(String clusterNodeID) {
    return div(
      div()
        .withText("Heat Sensors:"),
      heatSensorDeployButton(clusterNodeID)
    )
      .withText(clusterNodeID)
      .withStyle("border: 3px solid black; margin: 5px 0px; padding: 5px;")
      .withId("heatSensorsContainer-" + clusterNodeID);
  }

  public static DomContent averageTemp(String message) {
    return div(
      text(message)).withId("averageTemp");
  }

  public static DomContent heatSensors() {
    return div(
      button()
        .withText("Manage Heat Sensors")
        .withData("on-click", "$$post('/heatSensors')")
    )
      .withId("manageHeatSensorsButton");
  }
}
