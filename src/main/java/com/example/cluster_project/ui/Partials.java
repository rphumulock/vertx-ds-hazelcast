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
          div(
            h4("Current Node: " + clusterID),
            manageHeatSensorsButton()
          ).withClass("text-xl font-semibold mb-2 text-center text-base-content"),
          main(
            div().withId("sensorsContainer")
              .withClass("grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4")
          ).withId("main")
            .withData("store", store.encode())
        ).withClass("bg-base-300 h-full flex flex-col")
      )
        .withStyle("height: -webkit-fill-available;")
        .withData("theme", "dim")
    );
  }

  public static DomContent sharedHead(String title) {
    return head(
      title(title),
      link().withRel("icon").withType("image/x-icon").withHref("favicon.ico"),
      link().withHref("/static/css/output.css").withRel("stylesheet"),
      script().withSrc("https://cdn.jsdelivr.net/npm/@sudodevnull/datastar").isDefer().withType("module"),
      style("html, body { height: 100%; margin: 0; display: flex; flex-direction: column; }")
    );
  }

  /*****************************************************************************************
   *  HEAT SENSOR CONTAINERS
   *****************************************************************************************/

  public static DomContent heatSensorsContainerTemplate(String clusterID) {
    return
      div(
        div(
          text("Cluster-" + clusterID),
          heatSensorDeployButton(clusterID)
        ).withClass("my-1 p-2 flex flex-col justify-between items-center"),
        div()
          .withClass("my-1 p-2 flex flex-col")
      )
        .withId("heatSensorsContainer-" + clusterID)
        .withClass("border-3 border-primary rounded-lg bg-base-200 mx-5 p-5 justify-between items-center");
  }

  public static DomContent averageTemp(String message) {
    return div(message).withId("averageTemp")
      .withClass("text-sm font-semibold text-base-content mt-2");
  }

  public static DomContent manageHeatSensorsButton() {
    return div(
      button("Manage Heat Sensors")
        .withClass("btn btn-primary font-bold py-2 px-4 rounded mt-2")
        .withData("on-click", "$$post('/heatSensors')")
    ).withId("manageHeatSensorsButton");
  }

  /*****************************************************************************************
   *  HEAT SENSOR TEMPLATES
   *****************************************************************************************/

  public static DomContent heatSensorTemplate(String clusterID, String heatSensorID, boolean isActive) {
    return div(
      div("Heat Sensor: " + heatSensorID).withId("heatSensor-" + heatSensorID)
        .withClass("text-sm font-medium text-base-content"),
      div(
        isActive ? activeHeatSensorActions(clusterID, heatSensorID) : heatSensorActions(clusterID, heatSensorID)
      ).withId("heatSensorControlsContainer-" + heatSensorID)
        .withClass("p-2 rounded-lg mt-1"),
      isActive ? heatSensorUpdatesTemplate(heatSensorID) : null
    ).withId("heatSensorContainer-" + heatSensorID)
      .withClass("border-2 border-primary p-2 my-1 rounded-lg bg-base-200 flex flex-col justify-between items-center");
  }


  public static DomContent heatSensorActions(String clusterID, String heatSensorID) {
    return div(
      heatSensorStartUpdates(clusterID, heatSensorID),
      heatSensorUndeployButton(clusterID, heatSensorID)
    ).withId("heatSensorActionsContainer-" + heatSensorID)
      .withClass("flex flex-col md:flex-row items-center space-x-2");
  }

  public static DomContent activeHeatSensorActions(String clusterID, String heatSensorID) {
    return div(
      heatSensorUnsubscribeButton(clusterID, heatSensorID),
      heatSensorStopUpdates(clusterID, heatSensorID),
      heatSensorUndeployButton(clusterID, heatSensorID)
    ).withId("heatSensorActionsContainer-" + heatSensorID)
      .withClass("flex flex-col md:flex-row items-center space-x-2");
  }

  /*****************************************************************************************
   *  HEAT SENSOR BUTTONS
   *****************************************************************************************/

  public static DomContent heatSensorDeployButton(String clusterID) {
    return button("Deploy")
      .withClass("btn btn-success font-bold py-1 px-2 rounded m-1 text-xs")
      .withData("on-click", "$$post('/heatSensor/" + clusterID + "/deploy')");
  }

  public static DomContent heatSensorUndeployButton(String clusterID, String heatSensorID) {
    return button("Undeploy")
      .withClass("btn btn-error font-bold py-1 px-2 rounded m-1 text-xs")
      .withData("on-click", "$$post('/heatSensor/" + clusterID + "/" + heatSensorID + "/undeploy')");
  }

  public static DomContent heatSensorStartUpdates(String clusterID, String heatSensorID) {
    return button("Start Updates").withId("heatSensorStartUpdatesButton-" + heatSensorID)
      .withClass("btn btn-info font-bold py-1 px-2 rounded m-1 text-xs")
      .withData("on-click", "$$post('/heatSensor/" + clusterID + "/" + heatSensorID + "/startUpdates')");
  }

  public static DomContent heatSensorStopUpdates(String clusterID, String heatSensorID) {
    return button("Stop Updates").withId("heatSensorStopUpdatesButton-" + heatSensorID)
      .withClass("btn btn-warning font-bold py-1 px-2 rounded m-1 text-xs")
      .withData("on-click", "$$post('/heatSensor/" + clusterID + "/" + heatSensorID + "/stopUpdates')");
  }

  public static DomContent heatSensorSubscribeButton(String clusterID, String heatSensorID) {
    return button("Subscribe").withId("heatSensorSubscribeButton-" + heatSensorID)
      .withClass("btn btn-primary font-bold py-1 px-2 rounded m-1 text-xs")
      .withData("on-click", "$$get('/heatSensor/" + clusterID + "/" + heatSensorID + "/subscribe')");
  }

  public static DomContent heatSensorUnsubscribeButton(String clusterID, String heatSensorID) {
    return button("Unsubscribe").withId("heatSensorUnsubscribeButton-" + heatSensorID)
      .withClass("btn btn-secondary font-bold py-1 px-2 rounded m-1 text-xs")
      .withData("on-click", "$$get('/heatSensor/" + clusterID + "/" + heatSensorID + "/unsubscribe')");
  }

  public static DomContent heatSensorUpdatesTemplate(String heatSensorID) {
    return div(
      text("Temperature:"),
      div().withId("heatSensorUpdates-" + heatSensorID)
        .withClass("ml-2 text-base-content")
    ).withId("heatSensorUpdatesContainer-" + heatSensorID)
      .withClass("flex gap-2 border-dotted border-2 border-primary text-xs p-1 rounded");

  }

  public static DomContent heatSensorDataTemplate(String heatSensorID, String temperature) {
    return div(temperature)
      .withId("heatSensorUpdates-" + heatSensorID)
      .withClass("text-xs text-base-content");
  }

}
