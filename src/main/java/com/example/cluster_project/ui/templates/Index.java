package com.example.cluster_project.ui.templates;

import com.example.cluster_project.ui.partials.Partials;
import com.example.cluster_project.ui.partials.SharedPartials;
import io.vertx.core.json.JsonObject;

import static j2html.TagCreator.*;

public class Index {

  public static String getIndex(String deploymentID) {
    var title = "Home";
    var store = new JsonObject().put("test", "yay");
    return document(html(
        SharedPartials.sharedHead(title),
        body(
          h2("Datastar Example: " +deploymentID),
          main().
            withClass("container").
            withId("main").
            withData("store", "{ input: \"\", show: false }").
            with(
              Partials.subscribeSensorUpdates()
            )
        )
      )
    );
  }
}
