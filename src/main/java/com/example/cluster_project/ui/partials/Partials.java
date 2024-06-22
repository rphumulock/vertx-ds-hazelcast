package com.example.cluster_project.ui.partials;

import j2html.tags.DomContent;

import static j2html.TagCreator.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Partials {

  private static final Logger logger = LoggerFactory.getLogger(Partials.class);

  /*****************************************************************************************
   *  SUBSCRIBE / UNSUBSCRIBE
   *****************************************************************************************/

  public static DomContent heatSensorTemplate(String clusterNodeID, String heatSensorID) {
    logger.debug("heatSensorTemplate - Sensor  {}.", heatSensorID);
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

  public static DomContent heatSensorDataTemplate(String cluserNodeID, String heatSensorID, String temp) {
    logger.debug("SensorData from: heatSensorID: {} temp: {}", heatSensorID, temp);
    return div()
      .withText(temp)
      .withId("heatSensorUpdates-" + heatSensorID);
  }

  /*****************************************************************************************
   *  HEAT SENSOR CONTAINERS
   *****************************************************************************************/

  public static DomContent heatSensorsContainerTemplate(String clusterNodeID) {
    logger.debug("heatSensorsContainerTemplate - Node {}.", clusterNodeID);
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
