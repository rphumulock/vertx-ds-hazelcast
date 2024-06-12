package com.example.cluster_project.ui.partials;

import j2html.tags.DomContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static j2html.TagCreator.*;

public class Partials {

  private static final Logger logger = LoggerFactory.getLogger(Partials.class);

  public static DomContent subscribeHeatSensorUpdatesTemplate(String heatSensorDeploymentID) {
    return div(
      button("Subscribe to Sensor Updates")
        .withData("on-click", "$$post('/subscribeSensorUpdates/" + heatSensorDeploymentID + "')")
        .withId("subscribeSensorUpdates"))
      .withId("subscribeContainer");
  }

  public static DomContent unsubscribeSensorUpdates(String sensorID) {
    return div(
      button()
        .withText("Unsubscribe to Sensor Updates")
        .withData("on-click", "$$post('/unsubscribeSensorUpdates/" + sensorID + "')"),
      Partials.sensorUpdatesContainer()
    ).withId("unsubscribeContainer");
  }

  public static DomContent sensorUpdatesContainer() {
    return div()
      .withText("Sensor Updates:")
      .withId("sensorUpdatesContainer");
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

  public static DomContent sensorUpdate(String deploymentID, String id, String temp) {
    logger.debug("SensorData from: {} id: {} temp: {}", deploymentID, id, temp);
    return div(
      div().withText("Sensor ID: " + id),
      div().withText("Sensor Temp: " + temp)
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
      div()
        .withText("Heat Sensor: " + heatSensorDeploymentID),
      subscribeHeatSensorUpdatesTemplate(heatSensorDeploymentID)
    )
      .withId("heatSensorContainer-" + heatSensorDeploymentID)
      .withStyle("border: 3px dotted black; margin: 5px 0px; padding: 5px; font-size: small");
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
