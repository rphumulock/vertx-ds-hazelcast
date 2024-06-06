package com.example.cluster_project.ui.partials;

import j2html.tags.DomContent;

import static j2html.TagCreator.*;

public class Partials {

  public static DomContent subscribeSensorUpdates() {
    return div(
      button("Subscribe to Sensor Updates").withData("on-click", "$$get('/subscribeSensorUpdates')")
        .withId("subscribeSensorUpdates"))
      .withId("subscribeContainer");
  }

  public static DomContent unsubscribeSensorUpdates() {
    return div(
      button().withText("Unsubscribe to Sensor Updates").withData("on-click", "$$get('/unsubscribeSensorUpdates')"),
      Partials.sensorUpdatesContainer()
    ).withId("unsubscribeContainer");
  }

  public static DomContent sensorUpdatesContainer() {
    return div().withText("Sensor Updates:").withId("sensorUpdatesContainer");
  }


  public static DomContent sensorUpdate(String id, String temp) {
    return div().withText(id + " ---- ").withText(temp).withId(id);
  }

  public static DomContent averageTemp(String message) {
    return div(
      text(message)).withId("averageTemp");
  }
}
