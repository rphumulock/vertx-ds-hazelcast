package com.example.cluster_project.ui;

import io.vertx.core.json.JsonObject;
import j2html.tags.DomContent;

import static j2html.TagCreator.*;

public class Partials {

  public static String indexTemplate(String clusterID) {
    var title = "Cluster: " + clusterID;
    var store = new JsonObject();
    return document(html(
        sharedHead(title),
        body(
          h4("Current Node: " + clusterID),
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

  public static DomContent heatSensorTemplate(String clusterID, String heatSensorID) {
    return div(
      div(
        div()
          .withText("Heat Sensor: " + heatSensorID)
          .withId("heatSensor-" + heatSensorID),
        heatSensorActions(clusterID, heatSensorID)
      )
        .withStyle("display: flex; justify-content: space-between;")
        .withId("heatSensorControlsContainer-" + heatSensorID),
      div()
        .withId("heatSensorDataContainer-" + heatSensorID)
    )
      .withStyle("border: 2px solid black; margin: 5px 0px;")
      .withId("heatSensorContainer-" + heatSensorID);
  }

  public static DomContent activeHeatSensorTemplate(String clusterID, String heatSensorID) {
    return div(
      div(
        div()
          .withText("Heat Sensor: " + heatSensorID)
          .withId("heatSensor-" + heatSensorID),
        activeHeatSensorActions(clusterID, heatSensorID)
      )
        .withStyle("display: flex; justify-content: space-between;")
        .withId("heatSensorControlsContainer-" + heatSensorID),
      div(
        heatSensorUpdatesTemplate(clusterID, heatSensorID)
      )
        .withId("heatSensorDataContainer-" + heatSensorID)
    )
      .withStyle("border: 2px solid black; margin: 5px 0px;")
      .withId("heatSensorContainer-" + heatSensorID);
  }

  public static DomContent heatSensorActions(String clusterID, String heatSensorID) {
    return div(
      heatSensorStartUpdates(clusterID, heatSensorID),
      heatSensorUndeployButton(clusterID, heatSensorID)
    )
      .withId("heatSensorActionsContainer-" + heatSensorID)
      .withStyle("display: flex; align-items: center;");
  }

  public static DomContent activeHeatSensorActions(String clusterID, String heatSensorID) {
    return div(
      heatSensorUnsubscribeButton(clusterID, heatSensorID),
      heatSensorStopUpdates(clusterID, heatSensorID),
      heatSensorUndeployButton(clusterID, heatSensorID)
    )
      .withId("heatSensorActionsContainer-" + heatSensorID)
      .withStyle("display: flex; align-items: center;");
  }

  public static DomContent heatSensorDeployButton(String clusterID) {
    return button()
      .withText("Deploy")
      .withStyle("margin: 0px 5px;")
      .withData("on-click", "$$post('/heatSensor/" + clusterID + "/deploy')");
  }

  public static DomContent heatSensorUndeployButton(String clusterID, String heatSensorID) {
    return button()
      .withText("Undeploy")
      .withData("on-click", "$$post('/heatSensor/" + clusterID + "/" + heatSensorID + "/undeploy')");
  }

  public static DomContent heatSensorStartUpdates(String clusterID, String heatSensorID) {
    return button()
      .withText("Start Updates")
      .withData(
        "on-click",
        "$$post('/heatSensor/" + clusterID + "/" + heatSensorID + "/startUpdates')"
      )
      .withId("heatSensorStartUpdatesButton-" + heatSensorID);
  }

  public static DomContent heatSensorStopUpdates(String clusterID, String heatSensorID) {
    return button()
      .withText("Stop Updates")
      .withData(
        "on-click",
        "$$post('/heatSensor/" + clusterID + "/" + heatSensorID + "/stopUpdates')"
      )
      .withId("heatSensorStopUpdatesButton-" + heatSensorID);
  }

  public static DomContent heatSensorSubscribeButton(String clusterID, String heatSensorID) {
    return button()
      .withText("Subscribe")
      .withData(
        "on-click",
        "$$get('/heatSensor/" + clusterID + "/" + heatSensorID + "/subscribe')"
      )
      .withId("heatSensorSubscribeButton-" + heatSensorID);

  }

  public static DomContent heatSensorUnsubscribeButton(String clusterID, String heatSensorID) {
    return
      button()
        .withText("Unsubscribe")
        .withData(
          "on-click",
          "$$get('/heatSensor/" + clusterID + "/" + heatSensorID + "/unsubscribe')"
        )
        .withId("heatSensorUnsubscribeButton-" + heatSensorID);
  }

  public static DomContent heatSensorUpdatesTemplate(String clusterID, String heatSensorID) {
    return div(
      text("Temperature:"),
      div()
        .withId("heatSensorUpdates-" + heatSensorID)
    )
      .withStyle("display: flex; gap: 5px; border: 1px dotted black; font-size: small; ")
      .withId("heatSensorUpdatesContainer-" + heatSensorID);
  }

  public static DomContent heatSensorDataTemplate(String clusterNodeID, String heatSensorID, String temperature) {
    return div()
      .withText(temperature)
      .withId("heatSensorUpdates-" + heatSensorID);
  }

  /*****************************************************************************************
   *  HEAT SENSOR CONTAINERS
   *****************************************************************************************/

  public static DomContent heatSensorsContainerTemplate(String clusterID) {
    return div(
      div()
        .withText("Heat Sensors:"),
      heatSensorDeployButton(clusterID)
    )
      .withText(clusterID)
      .withStyle("border: 3px solid black; margin: 5px 0px; padding: 5px;")
      .withId("heatSensorsContainer-" + clusterID);
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
