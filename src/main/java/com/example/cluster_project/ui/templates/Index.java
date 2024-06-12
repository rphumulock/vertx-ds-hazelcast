package com.example.cluster_project.ui.templates;

import com.example.cluster_project.ui.partials.Partials;
import com.example.cluster_project.ui.partials.SharedPartials;
import io.vertx.core.json.JsonObject;

import static j2html.TagCreator.*;

public class Index {

  public static String getIndex(String nodeDeploymentID) {
    var title = "Cluster: " + nodeDeploymentID;
    var store = new JsonObject().put("verticleSelection", "1");
    return document(html(
        SharedPartials.sharedHead(title),
        body(
          h4("Current Node: " + nodeDeploymentID),
          main(
            Partials.deploymentSelectionTemplate(),
            Partials.nodeContainerTemplate(nodeDeploymentID)
          ).
            withClass("container").
            withId("main").
            withData("store", store.encode())
        )
      )
    );
  }
}
