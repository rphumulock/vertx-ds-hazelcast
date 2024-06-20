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

  public static DomContent heatSensorTemplate(String nodeDeploymentID, String heatSensorDeploymentID) {
    logger.debug("heatSensorTemplate - Sensor  {}.", heatSensorDeploymentID);
    return div(
      div(
        div()
          .withText("Heat Sensor: " + heatSensorDeploymentID)
          .withId("heatSensor-" + heatSensorDeploymentID),
        heatSensorActions(nodeDeploymentID, heatSensorDeploymentID)
      )
        .withStyle("border: 3px dotted black; margin: 5px 5px; padding: 5px; font-size: small; display: flex; justify-content: space-between;")
        .withId("heatSensorControlsContainer-" + heatSensorDeploymentID),
      div()
        .withId("heatSensorDataContainer-" + heatSensorDeploymentID)
    )
      .withId("heatSensorContainer-" + heatSensorDeploymentID);
  }

  public static DomContent heatSensorActions(String nodeDeploymentID, String heatSensorDeploymentID) {
    return div(
      heatSensorStartUpdates(heatSensorDeploymentID),
      heatSensorUndeployButton(nodeDeploymentID, heatSensorDeploymentID)
    )
      .withId("heatSensorActionsContainer-" + heatSensorDeploymentID)
      .withStyle("display: flex; align-items: center;");
  }


  public static DomContent heatSensorDeployButton(String nodeDeploymentID) {
    return button()
      .withText("Deploy")
      .withData("on-click", "$$get('/heatSensor/" + nodeDeploymentID + "/deploy')");
  }


  public static DomContent heatSensorUndeployButton(String nodeDeploymentID, String heatSensorDeploymentID) {
    return button()
      .withText("Undeploy")
      .withData(
        "on-click",
        "$post('/heatSensor/" + nodeDeploymentID + "/" + heatSensorDeploymentID + "/undeploy')"
      );
  }

  public static DomContent heatSensorStartUpdates(String heatSensorDeploymentID) {
    return button()
      .withText("Start Updates")
      .withData(
        "on-click",
        "$$get('/heatSensor/" + heatSensorDeploymentID + "/startUpdates')"
      )
      .withId("heatSensorStartUpdatesButton-" + heatSensorDeploymentID);
  }

  public static DomContent heatSensorStopUpdates(String heatSensorDeploymentID) {
    return button()
      .withText("Stop Updates")
      .withData(
        "on-click",
        "$$get('/heatSensor/" + heatSensorDeploymentID + "/stopUpdates')"
      )
      .withId("heatSensorStopUpdatesButton-" + heatSensorDeploymentID);
  }

  public static DomContent heatSensorSubscribeButton(String heatSensorDeploymentID) {
    return button()
      .withText("Subscribe")
      .withData(
        "on-click",
        "$$get('/heatSensor/" + heatSensorDeploymentID + "/subscribe')"
      )
      .withId("heatSensorSubscribeButton-" + heatSensorDeploymentID);

  }

  public static DomContent heatSensorUnsubscribeButton(String heatSensorDeploymentID) {
    return
      button()
        .withText("Unsubscribe")
        .withData(
          "on-click",
          "$$get('/heatSensor/" + heatSensorDeploymentID + "/unsubscribe')"
        )
        .withId("heatSensorUnsubscribeButton-" + heatSensorDeploymentID);
  }


  public static DomContent heatSensorUpdatesTemplate(String heatSensorDeploymentID) {
    return div(
      text("Temperature:"),
      div()
        .withId("heatSensorUpdates-" + heatSensorDeploymentID)
    )
      .withStyle("display: flex; gap: 5px; margin: 0px 5px;")
      .withId("heatSensorUpdatesContainer-" + heatSensorDeploymentID);
  }

  public static DomContent subscribeHeatSensorTemplate(String heatSensorDeploymentID) {
    return div(
      heatSensorUnsubscribeButton(heatSensorDeploymentID)
    )
      .withId("unsubscribeHeatSensorContainer-" + heatSensorDeploymentID)
      .withStyle("display: flex; justify-content: space-between;");
  }


  public static DomContent heatSensorUpdatesTemplate(String heatSensorDeploymentID, String temp) {
    logger.debug("SensorData from: heatSensorDeploymentID: {} temp: {}", heatSensorDeploymentID, temp);
    return div()
      .withText(temp)
      .withId("heatSensorUpdates-" + heatSensorDeploymentID);
  }

  /*****************************************************************************************
   *  HEAT SENSOR CONTAINERS
   *****************************************************************************************/

  public static DomContent heatSensorsContainerTemplate(String nodeDeploymentID) {
    logger.debug("heatSensorsContainerTemplate - Node {}.", nodeDeploymentID);
    return div(
      div()
        .withText("Heat Sensors:"),
      heatSensorDeployButton(nodeDeploymentID)
    )
      .withText(nodeDeploymentID)
      .withStyle("border: 3px solid black; margin: 5px 0px; padding: 5px;")
      .withId("heatSensorsContainer-" + nodeDeploymentID);
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


//  public static DomContent unsubscribeHeatSensorTemplate(String heatSensorDeploymentID) {
//    return div(
//
//      button()
//        .withData(
//          "on-click",
//          "$$get('/heatSensor/" + heatSensorDeploymentID + "/unsubscribe')"
//        )
//        .withId("unsubscribeHeatSensorUpdatesButton-" + heatSensorDeploymentID)
//        .withStyle("height: 100%; border-radius: 50%; margin: auto 5px; background-color: green;")
//    )
//
//      .withId("unsubscribeHeatSensorContainer-" + heatSensorDeploymentID)
//      .withStyle("display: flex; align-items: center;");
//  }
