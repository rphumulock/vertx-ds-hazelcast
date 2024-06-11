package com.example.cluster_project.ui.partials;

import j2html.tags.DomContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static j2html.TagCreator.*;

public class Partials {

  private static final Logger logger = LoggerFactory.getLogger(Partials.class);

  public static DomContent subscribeSensorUpdates() {
    return div(
      button("Subscribe to Sensor Updates").withData("on-click", "$$get('/subscribeSensorUpdates')")
        .withId("subscribeSensorUpdates"))
      .withId("subscribeContainer");
  }

  public static DomContent unsubscribeSensorUpdates() {
    return div(
      button()
        .withText("Unsubscribe to Sensor Updates")
        .withData("on-click", "$$get('/unsubscribeSensorUpdates')"),
      Partials.sensorUpdatesContainer()
    ).withId("unsubscribeContainer");
  }

  public static DomContent sensorUpdatesContainer() {
    return div()
      .withText("Sensor Updates:")
      .withId("sensorUpdatesContainer");
  }

  public static DomContent sensorUpdate(String deploymentID, String id, String temp) {
    logger.debug("SensorData from: {} id: {} temp: {}", deploymentID, id, temp);
    return div(
      div()
        .withStyle("border: 1px solid black")
        .withText("Deployment ID: " + deploymentID),
      div(
        div().withText("Sensor ID: " + id),
        div().withText("Sensor Temp: " + temp)
      ).withStyle("border: 1px solid black")
    )
      .withId(id)
      .withStyle("border: 3px solid black; margin: 5px 0px;");
  }

  public static DomContent averageTemp(String message) {
    return div(
      text(message)).withId("averageTemp");
  }

  public static DomContent deploymentSelection() {
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
