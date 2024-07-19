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
          h4("Current Node: " + clusterID).withClass("text-xl font-semibold mb-2 text-center text-teal-700"),
          main(
            div(
              Partials.heatSensors()
            ).withClass("grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4")
          ).withClass("container mx-auto p-4 bg-white rounded-lg shadow-lg")
            .withId("main")
            .withData("store", store.encode())
        ).withClass("bg-gray-100")
      )
    );
  }

  public static DomContent sharedHead(String title) {
    return head(
      title(title),
      link().withRel("icon").withType("image/x-icon").withHref("favicon.ico"),
      link().withHref("/static/css/output.css").withRel("stylesheet"),
      script().withSrc("https://cdn.jsdelivr.net/npm/@sudodevnull/datastar").isDefer().withType("module")
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
          .withClass("text-sm font-medium text-teal-900"),
        heatSensorActions(clusterID, heatSensorID)
      ).withClass("flex flex-col md:flex-row justify-between items-center")
        .withId("heatSensorControlsContainer-" + heatSensorID),
      div().withId("heatSensorDataContainer-" + heatSensorID)
        .withClass("p-2 rounded-lg mt-1")
    ).withClass("border-2 border-teal-500 p-2 my-1 rounded-lg bg-white")
      .withId("heatSensorContainer-" + heatSensorID);
  }

  public static DomContent activeHeatSensorTemplate(String clusterID, String heatSensorID) {
    return div(
      div(
        div("Heat Sensor: " + heatSensorID)
          .withId("heatSensor-" + heatSensorID)
          .withClass("text-sm font-medium text-teal-900"),
        activeHeatSensorActions(clusterID, heatSensorID)
      ).withClass("flex flex-col md:flex-row justify-between items-center")
        .withId("heatSensorControlsContainer-" + heatSensorID),
      div(
        heatSensorUpdatesTemplate(clusterID, heatSensorID)
      ).withId("heatSensorDataContainer-" + heatSensorID)
        .withClass("p-2 rounded-lg mt-1")
    ).withClass("border-2 border-teal-500 p-2 my-1 rounded-lg bg-white")
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
      .withClass("bg-teal-500 hover:bg-teal-700 text-white font-bold py-1 px-2 rounded m-1 text-xs")
      .withData("on-click", "$$post('/heatSensor/" + clusterID + "/deploy')");
  }

  public static DomContent heatSensorUndeployButton(String clusterID, String heatSensorID) {
    return button("Undeploy")
      .withClass("bg-red-500 hover:bg-red-700 text-white font-bold py-1 px-2 rounded m-1 text-xs")
      .withData("on-click", "$$post('/heatSensor/" + clusterID + "/" + heatSensorID + "/undeploy')");
  }

  public static DomContent heatSensorStartUpdates(String clusterID, String heatSensorID) {
    return button("Start Updates")
      .withClass("bg-green-500 hover:bg-green-700 text-white font-bold py-1 px-2 rounded m-1 text-xs")
      .withData("on-click", "$$post('/heatSensor/" + clusterID + "/" + heatSensorID + "/startUpdates')")
      .withId("heatSensorStartUpdatesButton-" + heatSensorID);
  }

  public static DomContent heatSensorStopUpdates(String clusterID, String heatSensorID) {
    return button("Stop Updates")
      .withClass("bg-yellow-500 hover:bg-yellow-700 text-white font-bold py-1 px-2 rounded m-1 text-xs")
      .withData("on-click", "$$post('/heatSensor/" + clusterID + "/" + heatSensorID + "/stopUpdates')")
      .withId("heatSensorStopUpdatesButton-" + heatSensorID);
  }

  public static DomContent heatSensorSubscribeButton(String clusterID, String heatSensorID) {
    return button("Subscribe")
      .withClass("bg-purple-500 hover:bg-purple-700 text-white font-bold py-1 px-2 rounded m-1 text-xs")
      .withData("on-click", "$$get('/heatSensor/" + clusterID + "/" + heatSensorID + "/subscribe')")
      .withId("heatSensorSubscribeButton-" + heatSensorID);
  }

  public static DomContent heatSensorUnsubscribeButton(String clusterID, String heatSensorID) {
    return button("Unsubscribe")
      .withClass("bg-indigo-500 hover:bg-indigo-700 text-white font-bold py-1 px-2 rounded m-1 text-xs")
      .withData("on-click", "$$get('/heatSensor/" + clusterID + "/" + heatSensorID + "/unsubscribe')")
      .withId("heatSensorUnsubscribeButton-" + heatSensorID);
  }

  public static DomContent heatSensorUpdatesTemplate(String clusterID, String heatSensorID) {
    return div(
      text("Temperature:"),
      div().withId("heatSensorUpdates-" + heatSensorID)
        .withClass("ml-2 text-teal-900")
    ).withClass("flex gap-2 border-dotted border-2 border-teal-500 text-xs p-1 rounded")
      .withId("heatSensorUpdatesContainer-" + heatSensorID);
  }

  public static DomContent heatSensorDataTemplate(String clusterNodeID, String heatSensorID, String temperature) {
    return div(temperature)
      .withId("heatSensorUpdates-" + heatSensorID)
      .withClass("text-xs text-teal-900");
  }

  /*****************************************************************************************
   *  HEAT SENSOR CONTAINERS
   *****************************************************************************************/

  public static DomContent heatSensorsContainerTemplate(String clusterID) {
    return div(
      div("Heat Sensors:"),
      heatSensorDeployButton(clusterID)
    ).withClass("border-3 border-teal-500 my-1 p-2 rounded-lg bg-white")
      .withId("heatSensorsContainer-" + clusterID);
  }

  public static DomContent averageTemp(String message) {
    return div(message)
      .withId("averageTemp")
      .withClass("text-sm font-semibold text-teal-700 mt-2");
  }

  public static DomContent heatSensors() {
    return div(
      button("Manage Heat Sensors")
        .withClass("bg-teal-500 hover:bg-teal-700 text-white font-bold py-2 px-4 rounded mt-2")
        .withData("on-click", "$$post('/heatSensors')")
    ).withId("manageHeatSensorsButton");
  }
}
