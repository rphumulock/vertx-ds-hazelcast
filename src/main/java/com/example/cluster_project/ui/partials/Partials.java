package com.example.cluster_project.ui.partials;

import com.example.cluster_project.ui.templates.Index;
import io.vertx.core.json.JsonObject;
import j2html.tags.DomContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static j2html.TagCreator.*;

public class Partials {

  private static final Logger logger = LoggerFactory.getLogger(Partials.class);


  /*****************************************************************************************
   *  CLUSTER NODE
   *****************************************************************************************/

  public static DomContent nodeContainerTemplate(String nodeDeploymentID) {
    logger.debug("nodeContainerTemplate - Node {}.", nodeDeploymentID);
    return div()
      .withId("nodeContainer-" + nodeDeploymentID)
      .withText("Cluster Deployment: " + nodeDeploymentID)
      .withStyle("border: 3px solid black; margin: 5px 0px; padding: 5px;");
  }


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
        .withStyle("border: 3px dotted black; margin: 5px 5px; padding: 5px; font-size: small; display: flex; justify-content: space-between;")
        .withId("heatSensorControlsContainer-" + heatSensorID),
      div()
        .withId("heatSensorDataContainer-" + heatSensorID)
    )
      .withId("heatSensorContainer-" + heatSensorID);
  }

  public static DomContent heatSensorActions(String clusterNodeID, String heatSensorID) {
    return div(
      heatSensorStartUpdates(heatSensorID),
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
      .withData(
        "on-click",
        "$post('/heatSensor/" + clusterNodeID + "/" + heatSensorID + "/undeploy')"
      );
  }

  public static DomContent heatSensorStartUpdates(String heatSensorID) {
    return button()
      .withText("Start Updates")
      .withData(
        "on-click",
        "$$get('/heatSensor/" + heatSensorID + "/startUpdates')"
      )
      .withId("heatSensorStartUpdatesButton-" + heatSensorID);
  }

  public static DomContent heatSensorStopUpdates(String heatSensorID) {
    return button()
      .withText("Stop Updates")
      .withData(
        "on-click",
        "$$get('/heatSensor/" + heatSensorID + "/stopUpdates')"
      )
      .withId("heatSensorStopUpdatesButton-" + heatSensorID);
  }

  public static DomContent heatSensorSubscribeButton(String heatSensorID) {
    return button()
      .withText("Subscribe")
      .withData(
        "on-click",
        "$$get('/heatSensor/" + heatSensorID + "/subscribe')"
      )
      .withId("heatSensorSubscribeButton-" + heatSensorID);

  }

  public static DomContent heatSensorUnsubscribeButton(String heatSensorID) {
    return
      button()
        .withText("Unsubscribe")
        .withData(
          "on-click",
          "$$get('/heatSensor/" + heatSensorID + "/unsubscribe')"
        )
        .withId("heatSensorUnsubscribeButton-" + heatSensorID);
  }


  public static DomContent heatSensorUpdatesTemplate(String heatSensorID) {
    return div(
      text("Temperature:"),
      div()
        .withId("heatSensorUpdates-" + heatSensorID)
    )
      .withStyle("display: flex; gap: 5px; margin: 0px 5px;")
      .withId("heatSensorUpdatesContainer-" + heatSensorID);
  }

  public static DomContent subscribeHeatSensorTemplate(String heatSensorID) {
    return div(
      heatSensorUnsubscribeButton(heatSensorID)
    )
      .withId("unsubscribeHeatSensorContainer-" + heatSensorID)
      .withStyle("display: flex; justify-content: space-between;");
  }


  public static DomContent heatSensorUpdatesTemplate(String heatSensorID, String temp) {
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


//  public static DomContent sensor(String sensorDeploymentID) {
//    logger.debug("Sensor {}", sensorDeploymentID);
//    return
//      div(
//        div().withText("Deployment ID: " + sensorDeploymentID),
//        subscribeHeatSensorUpdatesTemplate(sensorDeploymentID)
//      )
//        .withId(sensorDeploymentID)
//        .withStyle("border: 1px dotted black; margin: 5px 0px; padding: 5px;");
//  }


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


  public static DomContent deploymentSelectionTemplate() {
    return div(
      button()
        .withId("deployVerticleSelection")
        .withText("Deploy Verticle Selection")
        .withData("on-click", "$$post('/heatSensor/deploy')")
    )
      .withId("deploymentContainer");

//      .with(
//        label("Verticle Selection:")
//          .attr("for", "mySelectId")
//          .withStyle("display: block;"),
//        select()
//          .withText("Verticle Selection")
//          .withData("model", "verticleSelection")
//          .with(
//            option("Heat Sensor").withValue("1"),
//            option("HTTP Server").withValue("2"),
//            option("Sensor Data").withValue("3")
//          ),

  }
}


//  public static DomContent unsubscribeHeatSensorTemplate(String heatSensorID) {
//    return div(
//
//      button()
//        .withData(
//          "on-click",
//          "$$get('/heatSensor/" + heatSensorID + "/unsubscribe')"
//        )
//        .withId("unsubscribeHeatSensorUpdatesButton-" + heatSensorID)
//        .withStyle("height: 100%; border-radius: 50%; margin: auto 5px; background-color: green;")
//    )
//
//      .withId("unsubscribeHeatSensorContainer-" + heatSensorID)
//      .withStyle("display: flex; align-items: center;");
//  }
