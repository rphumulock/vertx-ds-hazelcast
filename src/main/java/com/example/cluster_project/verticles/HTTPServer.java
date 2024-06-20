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
    router.post("/heatSensor/:clusterNodeID/:heatSensorID/undeploy").handler(this::heatSensorUnDeployHandler);
    router.get("/heatSensor/:clusterNodeID/:heatSensorID/startUpdates").handler(this::heatSensorStartUpdatesHandler);
    router.get("/heatSensor/:clusterNodeID/:heatSensorID/stopUpdates").handler(this::heatSensorStopUpdatesHandler);
    router.get("/heatSensor/:clusterNodeID/:heatSensorID/subscribe").handler(this::heatSensorSubscribeHandler);
    router.get("/heatSensor/:clusterNodeID/:heatSensorID/unsubscribe").handler(this::heatSensorUnsubscribeHandler);
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
    String clusterNodeID = config().getString("clusterNodeID");
    MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer("heatSensors", msg -> {
      JsonObject payload = msg.body();
      String eventType = payload.getString("eventType");
      String sensorClusterNodeID = payload.getString("clusterNodeID");
      String heatSensorID = payload.getString("heatSensorID");

      if (eventType.equals("sensorDeployment") && clusterNodeID.equals(sensorClusterNodeID)) {
        deployHeatSensor(response, clusterNodeID);
      } else if (eventType.equals("sensorUndeployment") && clusterNodeID.equals(sensorClusterNodeID)) {
        undeployHeatSensor(response, clusterNodeID, heatSensorID);
      } else if (eventType.equals("sensorUpdate")) {
        consumeSensorData(response, payload);
      }
    });
    onStartConsumer(response, consumer, clusterNodeID, "heatSensors");
  }

  private void deployHeatSensor(HttpServerResponse response, String clusterNodeID) {
    JsonObject config = new JsonObject()
      .put("clusterNodeID", clusterNodeID);
    DeploymentOptions options = new DeploymentOptions().setConfig(config);

    vertx.deployVerticle(HeatSensor.class.getName(), options, res -> {
      if (res.succeeded()) {
        String heatSensorID = res.result();
        registrationService.registerAndIncrementCounter("MainVerticle", clusterNodeID, heatSensorID)
          .onComplete(ar -> {
            if (ar.succeeded()) {
              addHeatSensor(response, clusterNodeID, heatSensorID);
            } else {
              response.setStatusCode(500).end("Failed to deploy verticle");
            }
          });
      } else {
        response.setStatusCode(500).end("Failed to deploy verticle");
      }
    });
  }

  private void undeployHeatSensor(HttpServerResponse response, String clusterNodeID, String heatSensorID) {
    vertx.undeploy(heatSensorID, res -> {
      if (res.succeeded()) {
        unregisterVerticle(clusterNodeID, heatSensorID).onComplete(ar -> {
          if (ar.succeeded()) {
            addHeatSensor(response, clusterNodeID, heatSensorID);
          } else {
            response.setStatusCode(500).end("Failed to deploy verticle");
          }
        });
      } else {
        response.setStatusCode(500).end("Failed to deploy verticle");
      }
    });
  }

  private void heatSensorSubscribeHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    routingContext.request().bodyHandler(body -> {
      Map<String, String> pathParams = routingContext.pathParams();
      String heatSensorID = pathParams.get("heatSensorID");
      heatSensorSubscribe(response, heatSensorID);
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
      vertx.eventBus().publish("heatSensors", payload);
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
      vertx.eventBus().publish("heatSensors", payload);
      heatSensorStopUpdates(response, heatSensorID);
      response.end();
    });
  }

  private void heatSensorUnsubscribeHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    routingContext.request().bodyHandler(body -> {
      Map<String, String> pathParams = routingContext.pathParams();
      String heatSensorID = pathParams.get("heatSensorID");
      heatSensorUnsubscribe(response, heatSensorID);
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
        .put("eventType", "sensorDeployment")
        .put("clusterNodeID", clusterNodeID)
        .put("verticleName", HeatSensor.class.getName());
      vertx.eventBus().publish("heatSensors", payload);
      response.end();
    });
  }


  private void heatSensorUnDeployHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    routingContext.request().bodyHandler(body -> {
      Map<String, String> pathParams = routingContext.pathParams();
      String clusterNodeID = pathParams.get("clusterNodeID");
      JsonObject payload = new JsonObject()
        .put("eventType", "sensorUndeployment")
        .put("sensorclusterNodeID", clusterNodeID)
        .put("verticleName", HeatSensor.class.getName());
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

  private void onStartConsumer(HttpServerResponse response, MessageConsumer<JsonObject> consumer, String clusterNodeID, String type) {
    logger.info("sensor.updates consumer started for: {}", clusterNodeID);
    JsonObject openConsumer = new JsonObject().put("type", type).put("response", response).put("consumer", consumer);
    openConnections.put(clusterNodeID, openConsumer);
    response.endHandler(unused -> onEndConnection(consumer));
    consumer.endHandler(unused -> onEndConsumer());
  }

  private void onEndConnection(MessageConsumer<JsonObject> consumer) {
//    logger.debug("Connection ended for session: {} - created on: {}.",
//      id, dateFormat.format(new Date(session.<Long>get("createdOn"))));

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
  }

  private void onEndConsumer() {
    logger.debug("Unregistering consumer for session: {} - created on: {}.");
//      session.id(), new Date(session.<Long>get("createdOn")));
  }


  private Future<Long> registerVerticle(String clusterNodeID, String heatSensorID) {
    SharedData sharedData = vertx.sharedData();

    Future<Long> incrementFuture = sharedData.getCounter("heatSensorVerticleCount").compose(counter ->
      counter.incrementAndGet()
    );

    Future<Void> mapUpdateFuture = sharedData.<String, String>getAsyncMap("verticleRegistry").compose(map ->
      map.put(clusterNodeID, heatSensorID).mapEmpty()
    );

    return CompositeFuture.all(incrementFuture, mapUpdateFuture).compose(result -> {
      Long incrementedCount = result.resultAt(0);
      return Future.succeededFuture(incrementedCount);
    });
  }


  private Future<Void> unregisterVerticle(String clusterNodeID, String heatSensorID) {
    SharedData sharedData = vertx.sharedData();

    // Decrement the counter
    Future<Void> decrementFuture = sharedData.getCounter("heatSensorVerticleCount").compose(counter ->
      counter.decrementAndGet().mapEmpty()
    );

    // Remove the entry from the verticleRegistry map
    Future<Void> mapUpdateFuture = sharedData.<String, String>getAsyncMap("verticleRegistry").compose(map ->
      map.remove(clusterNodeID).mapEmpty()
    );

    // Combine both futures
    return CompositeFuture.all(decrementFuture, mapUpdateFuture).mapEmpty();
  }

}
