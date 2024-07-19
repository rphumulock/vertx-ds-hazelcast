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
          h4("Current Node: " + clusterID).withClass("text-xl font-semibold mb-2 text-center text-base-content"),
          main(
            div(
              Partials.heatSensors()
            ).withClass("grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4")
          ).withClass("container mx-auto p-4 bg-base-200 rounded-lg shadow-lg flex-1")
            .withId("main")
            .withData("store", store.encode())
        ).withClass("bg-base-300 h-full flex flex-col")
      ).attr("style", "height: 100%;")
        .withClass("h-full")
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
   *  SUBSCRIBE / UNSUBSCRIBE
   *****************************************************************************************/

  public static DomContent heatSensorTemplate(String clusterID, String heatSensorID) {
    return div(
      div(
        div("Heat Sensor: " + heatSensorID)
          .withId("heatSensor-" + heatSensorID)
          .withClass("text-sm font-medium text-base-content"),
        heatSensorActions(clusterID, heatSensorID)
      ).withClass("flex flex-col md:flex-row justify-between items-center")
        .withId("heatSensorControlsContainer-" + heatSensorID),
      div().withId("heatSensorDataContainer-" + heatSensorID)
        .withClass("p-2 rounded-lg mt-1")
    ).withClass("border-2 border-primary p-2 my-1 rounded-lg bg-base-200")
      .withId("heatSensorContainer-" + heatSensorID);
  }

  public static DomContent activeHeatSensorTemplate(String clusterID, String heatSensorID) {
    return div(
      div(
        div("Heat Sensor: " + heatSensorID)
          .withId("heatSensor-" + heatSensorID)
          .withClass("text-sm font-medium text-base-content"),
        activeHeatSensorActions(clusterID, heatSensorID)
      ).withClass("flex flex-col md:flex-row justify-between items-center")
        .withId("heatSensorControlsContainer-" + heatSensorID),
      div(
        heatSensorUpdatesTemplate(clusterID, heatSensorID)
      ).withId("heatSensorDataContainer-" + heatSensorID)
        .withClass("p-2 rounded-lg mt-1")
    ).withClass("border-2 border-primary p-2 my-1 rounded-lg bg-base-200")
      .withId("heatSensorContainer-" + heatSensorID);
  }

  public static DomContent heatSensorActions(String clusterID, String heatSensorID) {
    return div(
      heatSensorStartUpdates(clusterID, heatSensorID),
      heatSensorUndeployButton(clusterID, heatSensorID)
    ).withId("heatSensorActionsContainer-" + heatSensorID)
      .withClass("flex flex-col md:flex-row items-center");
  }

  public static DomContent activeHeatSensorActions(String clusterID, String heatSensorID) {
    return div(
      heatSensorUnsubscribeButton(clusterID, heatSensorID),
      heatSensorStopUpdates(clusterID, heatSensorID),
      heatSensorUndeployButton(clusterID, heatSensorID)
    ).withId("heatSensorActionsContainer-" + heatSensorID)
      .withClass("flex flex-col md:flex-row items-center");
  }

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
    return button("Start Updates")
      .withClass("btn btn-info font-bold py-1 px-2 rounded m-1 text-xs")
      .withData("on-click", "$$post('/heatSensor/" + clusterID + "/" + heatSensorID + "/startUpdates')")
      .withId("heatSensorStartUpdatesButton-" + heatSensorID);
  }

  public static DomContent heatSensorStopUpdates(String clusterID, String heatSensorID) {
    return button("Stop Updates")
      .withClass("btn btn-warning font-bold py-1 px-2 rounded m-1 text-xs")
      .withData("on-click", "$$post('/heatSensor/" + clusterID + "/" + heatSensorID + "/stopUpdates')")
      .withId("heatSensorStopUpdatesButton-" + heatSensorID);
  }

  public static DomContent heatSensorSubscribeButton(String clusterID, String heatSensorID) {
    return button("Subscribe")
      .withClass("btn btn-primary font-bold py-1 px-2 rounded m-1 text-xs")
      .withData("on-click", "$$get('/heatSensor/" + clusterID + "/" + heatSensorID + "/subscribe')")
      .withId("heatSensorSubscribeButton-" + heatSensorID);
  }

  public static DomContent heatSensorUnsubscribeButton(String clusterID, String heatSensorID) {
    return button("Unsubscribe")
      .withClass("btn btn-secondary font-bold py-1 px-2 rounded m-1 text-xs")
      .withData("on-click", "$$get('/heatSensor/" + clusterID + "/" + heatSensorID + "/unsubscribe')")
      .withId("heatSensorUnsubscribeButton-" + heatSensorID);
  }

  public static DomContent heatSensorUpdatesTemplate(String clusterID, String heatSensorID) {
    return div(
      text("Temperature:"),
      div().withId("heatSensorUpdates-" + heatSensorID)
        .withClass("ml-2 text-base-content")
    ).withClass("flex gap-2 border-dotted border-2 border-primary text-xs p-1 rounded")
      .withId("heatSensorUpdatesContainer-" + heatSensorID);
  }

  public static DomContent heatSensorDataTemplate(String clusterNodeID, String heatSensorID, String temperature) {
    return div(temperature)
      .withId("heatSensorUpdates-" + heatSensorID)
      .withClass("text-xs text-base-content");
  }

  /*****************************************************************************************
   *  HEAT SENSOR CONTAINERS
   *****************************************************************************************/

  public static DomContent heatSensorsContainerTemplate(String clusterID) {
    return div(
      div("Heat Sensors:"),
      heatSensorDeployButton(clusterID)
    ).withClass("border-3 border-primary my-1 p-2 rounded-lg bg-base-200")
      .withId("heatSensorsContainer-" + clusterID);
  }

  public static DomContent averageTemp(String message) {
    return div(message)
      .withId("averageTemp")
      .withClass("text-sm font-semibold text-base-content mt-2");
  }

  public static DomContent heatSensors() {
    return div(
      button("Manage Heat Sensors")
        .withClass("btn btn-primary font-bold py-2 px-4 rounded mt-2")
        .withData("on-click", "$$post('/heatSensors')")
    ).withId("manageHeatSensorsButton");
  }
}
