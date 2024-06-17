package com.example.cluster_project.ui.partials;

import j2html.tags.DomContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static j2html.TagCreator.*;

public class Partials {

  private static final Logger logger = LoggerFactory.getLogger(Partials.class);

  public static DomContent subscribeHeatSensorUpdatesTemplate(String heatSensorDeploymentID) {
    return div(
      button()
        .withData(
          "on-click",
          "$$get('/heatSensor/" + heatSensorDeploymentID + "/subscribe')"
        )
        .withId("subscribeHeatSensorUpdatesButton-" + heatSensorDeploymentID)
        .withStyle("height: 100%; border-radius: 50%; margin: auto 5px; background-color: red;")
    )
      .withId("subscribeHeatSensorContainer-" + heatSensorDeploymentID)
      .withStyle("display: flex; align-items: center;");
  }

  public static DomContent unsubscribeHeatSensorTemplate(String heatSensorDeploymentID) {
    return div(
      button()
        .withData(
          "on-click",
          "$$get('/heatSensor/" + heatSensorDeploymentID + "/unsubscribe')"
        )
        .withId("unsubscribeHeatSensorUpdatesButton-" + heatSensorDeploymentID)
        .withStyle("height: 100%; border-radius: 50%; margin: auto 5px; background-color: green;"),
      Partials.sensorUpdatesContainer(heatSensorDeploymentID)
    )
      .withId("unsubscribeHeatSensorContainer-" + heatSensorDeploymentID)
      .withStyle("display: flex; align-items: center;");
  }

  public static DomContent sensorUpdatesContainer(String heatSensorDeploymentID) {
    return div()
      .withId("heatSensorUpdatesContainer-" + heatSensorDeploymentID);
  }

  public static DomContent sensor(String sensorDeploymentID) {
    logger.debug("Sensor {}", sensorDeploymentID);
    return
      div(
        div().withText("Deployment ID: " + sensorDeploymentID),
        subscribeHeatSensorUpdatesTemplate(sensorDeploymentID)
      )
        .withId(sensorDeploymentID)
        .withStyle("border: 1px dotted black; margin: 5px 0px; padding: 5px;");
  }

  public static DomContent sensorUpdate(String nodeDeploymentID, String id, String temp) {
    logger.debug("SensorData from: {} id: {} temp: {}", nodeDeploymentID, id, temp);
    return div(
      div()
        .withText("Temperature: " + temp)
    )
      .withStyle("border: 1px solid black")
      .withId(id);
  }

  public static DomContent heatSensorsContainerTemplate(String nodeDeploymentID) {
    logger.debug("Creating Heat Sensors Container for node: {}", nodeDeploymentID);
    return div()
      .withText("Heat Sensors")
      .withId("heatSensorsContainer-" + nodeDeploymentID)
      .withStyle("border: 3px solid black; margin: 5px 0px; padding: 5px;");
  }

  public static DomContent heatSensorTemplate(String heatSensorDeploymentID) {
    logger.debug("Creating Sensor for: {}", heatSensorDeploymentID);
    return div(
      subscribeHeatSensorUpdatesTemplate(heatSensorDeploymentID),
      div()
        .withText("Heat Sensor-" + heatSensorDeploymentID)
        .withId("heatSensor-" + heatSensorDeploymentID)
    )
      .withId("heatSensorContainer-" + heatSensorDeploymentID)
      .withStyle("border: 3px dotted black; margin: 5px 5px; padding: 5px; font-size: small; display: flex;");
  }

  public static DomContent averageTemp(String message) {
    return div(
      text(message)).withId("averageTemp");
  }

  public static DomContent nodeContainerTemplate(String nodeDeploymentID) {
    logger.debug("Creating Container for cluster node {}", nodeDeploymentID);
    return div()
      .withId("nodeContainer-" + nodeDeploymentID)
      .withText("Cluster Deployment: " + nodeDeploymentID)
      .withStyle("border: 3px solid black; margin: 5px 0px; padding: 5px;");
  }

  public static DomContent deploymentSelectionTemplate() {
    return div()
      .withId("deploymentContainer")
      .with(
        label("Verticle Selection:")
          .attr("for", "mySelectId")
          .withStyle("display: block;"),
        select()
          .withText("Verticle Selection")
          .withData("model", "verticleSelection")
          .with(
            option("Heat Sensor").withValue("1"),
            option("HTTP Server").withValue("2"),
            option("Sensor Data").withValue("3")
          ),
        button().withId("deployVerticleSelection")
          .withText("Deploy Verticle Selection")
          .withData("on-click", "$$post('/deployVerticleSelection')")
      );
  }
}
