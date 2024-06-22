package com.example.cluster_project.verticles;

import com.example.cluster_project.services.ClusterRegistrationServiceImpl;
import com.example.cluster_project.ui.partials.Partials;
import com.example.cluster_project.ui.templates.Index;
import com.example.cluster_project.utils.ClusterUtils;

import io.vertx.core.*;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;

import io.vertx.core.shareddata.SharedData;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.ClusteredSessionStore;
import io.vertx.ext.web.sstore.SessionStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.example.cluster_project.utils.DatastarUtils.*;

import java.text.SimpleDateFormat;
import java.util.Date;

public class HTTPServer extends AbstractVerticle {

  public static final String TEMPLATE = ""
    + "Session [%s] created on %s%n"
    + "%n"
    + "Page generated on %s%n";

  SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a");

  private static final Logger logger = LoggerFactory.getLogger(HTTPServer.class);

  private ClusterRegistrationServiceImpl registrationService;

  Map<String, JsonObject> openConnections = new ConcurrentHashMap<>();

  ArrayList<MessageConsumer<JsonObject>> openConsumers = new ArrayList<>();

  @Override
  public void start() throws Exception {

    Router router = Router.router(vertx);
    JsonObject config = config();
    int port = config.getInteger("http.port", 8080);

    SessionStore store = ClusteredSessionStore.create(vertx);
    router.route().handler(SessionHandler.create(store));
    setupRoutes(router);

    registrationService = new ClusterRegistrationServiceImpl(vertx);

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(port)
      .onSuccess(v -> logger.info("Starter server successfully on: http://localhost:{}", port))
      .onFailure(t -> logger.error("Failed to unregister consumer.", t));
  }

  @Override
  public void stop() throws Exception {
    this.openConsumers.forEach(consumer -> {
      if (consumer != null) {
        consumer.unregister(res -> {
          if (res.succeeded()) {
            logger.info("Deployment consumer unregistered successfully.");
          } else {
            logger.error("Failed to unregister deployment consumer.", res.cause());
          }
        });
      }
    });
  }

  private void setupRoutes(Router router) {
    router.get("/").handler(this::rootHandler);
    router.post("/heatSensors").handler(this::heatSensorsHandler);
    router.get("/heatSensor/:clusterNodeID/deploy").handler(this::heatSensorDeployHandler);
    router.get("/heatSensor/:clusterNodeID/:heatSensorID/undeploy").handler(this::heatSensorUnDeployHandler);
//    router.get("/heatSensor/:clusterNodeID/:heatSensorID/startUpdates").handler(this::heatSensorStartUpdatesHandler);
//    router.get("/heatSensor/:clusterNodeID/:heatSensorID/stopUpdates").handler(this::heatSensorStopUpdatesHandler);
//    router.get("/heatSensor/:clusterNodeID/:heatSensorID/subscribe").handler(this::heatSensorSubscribeHandler);
//    router.get("/heatSensor/:clusterNodeID/:heatSensorID/unsubscribe").handler(this::heatSensorUnsubscribeHandler);
  }

  /*****************************************************************************************
   *  HANDLERS
   *****************************************************************************************/

  private void rootHandler(RoutingContext routingContext) {
    Session session = routingContext.session();
    session.computeIfAbsent("createdOn", s -> System.currentTimeMillis()); // (3)
    logger.debug(String.format(TEMPLATE, session.id(), new Date(session.<Long>get("createdOn")), new Date()));

    HttpServerResponse response = routingContext.response();
    String clusterNodeID = config().getString("clusterNodeID");

    sendHtmlResponse(response, Index.getIndex(clusterNodeID));
  }

  private void heatSensorsHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    routingContext.request().bodyHandler(body -> {

      ClusterUtils.getClusterNodes(vertx, res -> {
        if (res.succeeded()) {
          List<String> nodes = res.result();
          logger.info("Active nodes in the cluster: {}", nodes);

          nodes.forEach(node -> {
            sendSSE(response, buildConfig(
              UUID.randomUUID().toString(),
              "#main",
              MergeTypes.APPEND_ELEMENT.getType(),
              0,
              Partials.heatSensorsContainerTemplate(node).render()
            ));
          });

          sendSSE(response, buildConfig(
            UUID.randomUUID().toString(),
            "#manageHeatSensorsButton",
            MergeTypes.DELETE_ELEMENT.getType(),
            0,
            "<div></div>"
          ));

          heatSensorConsumer(response);

        } else {
          logger.error("Failed to retrieve active nodes from cluster.", res.cause());
        }
      });
    });
  }

  private void heatSensorConsumer(HttpServerResponse response) {
    String thisClusterNodeID = config().getString("clusterNodeID");
    MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer("cluster.HeatSensors");

    consumer.handler(msg -> {
      JsonObject payload = msg.body();
      String eventType = payload.getString("eventType");
      String clusterNodeID = payload.getString("clusterNodeID");
      String deploymentID = payload.getString("deploymentID");

      if (eventType.equals("deploy.HeatSensor")) {
        addHeatSensor(response, clusterNodeID, deploymentID);
      } else if (eventType.equals("update.HeatSensor")) {
        consumeSensorData(response, payload);
      }
    });

    consumer.completionHandler(res -> {
      if (res.succeeded()) {
        logger.info("The HeatSensors handler registration for {} has reached all nodes.", thisClusterNodeID);
      } else {
        logger.info("The HeatSensors handler registration has failed.");
      }
    });

    consumer.exceptionHandler(res -> {
      logger.debug("Consumer exception for ");
    });

    consumer.endHandler(res -> {
      logger.debug("Unregistering consumer for session: {} - created on: {}.");
    });

    response.endHandler(unused -> {
      if (consumer.isRegistered()) {
        consumer.unregister().onComplete(res -> {
          if (res.succeeded()) {
            logger.debug("Consumer unregistered successfully for session: {} - created on: {}.");
//            session.id(), new Date(session.<Long>get("createdOn")));
          } else {
            logger.error("Failed to unregister consumer for session: {} - created on: {}.");
//            session.id(), session.<Long>get("createdOn"), res.cause());
          }
        });
      }
    });

  }


  private void heatSensorSubscribeHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    routingContext.request().bodyHandler(body -> {
      Map<String, String> pathParams = routingContext.pathParams();
      String clusterNodeID = pathParams.get("clusterNodeID");
      String heatSensorID = pathParams.get("heatSensorID");
      heatSensorSubscribe(response, clusterNodeID, heatSensorID);
    });
  }

  private void heatSensorStartUpdatesHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    String clusterNodeID = config().getString("clusterNodeID");
    routingContext.request().bodyHandler(body -> {
      Map<String, String> pathParams = routingContext.pathParams();
      String heatSensorID = pathParams.get("heatSensorID");
      JsonObject payload = new JsonObject()
        .put("clusterNodeID", clusterNodeID)
        .put("heatSensorID", heatSensorID)
        .put("eventType", "startUpdates");
      vertx.eventBus().publish("heatSensor." + clusterNodeID + "." + heatSensorID, payload);
      heatSensorStartUpdates(response, clusterNodeID, heatSensorID);
      response.end();
    });
  }

  private void heatSensorStopUpdatesHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    String clusterNodeID = config().getString("clusterNodeID");
    routingContext.request().bodyHandler(body -> {
      Map<String, String> pathParams = routingContext.pathParams();
      String heatSensorID = pathParams.get("heatSensorID");
      JsonObject payload = new JsonObject()
        .put("clusterNodeID", clusterNodeID)
        .put("heatSensorID", heatSensorID)
        .put("eventType", "stopUpdates");
      vertx.eventBus().publish("heatSensor." + clusterNodeID + "." + heatSensorID, payload);
      heatSensorStopUpdates(response, clusterNodeID, heatSensorID);
      response.end();
    });
  }

  private void heatSensorUnsubscribeHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    String clusterNodeID = config().getString("clusterNodeID");
    routingContext.request().bodyHandler(body -> {
      Map<String, String> pathParams = routingContext.pathParams();
      String heatSensorID = pathParams.get("heatSensorID");
      heatSensorUnsubscribe(response, clusterNodeID, heatSensorID);
      response.end();
    });
  }

  private void heatSensorDeployHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    routingContext.request().bodyHandler(body -> {
      Map<String, String> pathParams = routingContext.pathParams();
      String clusterNodeID = pathParams.get("clusterNodeID");
      JsonObject payload = new JsonObject()
        .put("eventType", "deploy.HeatSensor")
        .put("clusterNodeID", clusterNodeID)
        .put("verticleName", HeatSensor.class.getName());

      vertx.eventBus().request("verticle.controller." + clusterNodeID, payload)
        .onSuccess(ar -> {
          JsonObject result = (JsonObject) ar.body();
          String deploymentID = result.getString("deploymentID");
          vertx.eventBus().publish("cluster.HeatSensors", result);
          response.end(new JsonObject().put("status", "success").put("heatSensorID", deploymentID).encode());
        })
        .onFailure(ar -> {
          response.setStatusCode(500).end(new JsonObject().put("status", "failure").put("message", ar.getMessage()).encode());
        });
    });
  }

  private void heatSensorUnDeployHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    routingContext.request().bodyHandler(body -> {
      Map<String, String> pathParams = routingContext.pathParams();
      String clusterNodeID = pathParams.get("clusterNodeID");
      String heatSensorID = pathParams.get("heatSensorID");
      JsonObject payload = new JsonObject()
        .put("eventType", "sensorUndeployment")
        .put("sensorClusterNodeID", clusterNodeID)
        .put("heatSensorID", heatSensorID);
      vertx.eventBus().publish("heatSensors", payload);
      response.end();
    });
  }

/*****************************************************************************************
 *  CONSUMERS
 *****************************************************************************************/


/*****************************************************************************************
 *  CONSUMER UTILITIES
 *****************************************************************************************/


}
