package com.example.cluster_project.verticles;

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

  Set<String> externalNodes = Collections.newSetFromMap(new ConcurrentHashMap<>());
  Set<String> displayedHeatSensors = Collections.newSetFromMap(new ConcurrentHashMap<>());

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
    router.get("/heatSensor/:nodeDeploymentID/deploy").handler(this::heatSensorDeployHandler);
    router.post("/heatSensor/:nodeDeploymentID/:heatSensorDeploymentID/undeploy").handler(this::heatSensorUnDeployHandler);
    router.get("/heatSensor/:nodeDeploymentID/:heatSensorDeploymentID/startUpdates").handler(this::heatSensorStartUpdatesHandler);
    router.get("/heatSensor/:nodeDeploymentID/:heatSensorDeploymentID/stopUpdates").handler(this::heatSensorStopUpdatesHandler);
    router.get("/heatSensor/:nodeDeploymentID/:heatSensorDeploymentID/subscribe").handler(this::heatSensorSubscribeHandler);
    router.get("/heatSensor/:nodeDeploymentID/:heatSensorDeploymentID/unsubscribe").handler(this::heatSensorUnsubscribeHandler);
  }

  /*****************************************************************************************
   *  HANDLERS
   *****************************************************************************************/

  private void rootHandler(RoutingContext routingContext) {
    Session session = routingContext.session();
    session.computeIfAbsent("createdOn", s -> System.currentTimeMillis()); // (3)
    logger.debug(String.format(TEMPLATE, session.id(), new Date(session.<Long>get("createdOn")), new Date()));

    HttpServerResponse response = routingContext.response();
    String nodeDeploymentID = config().getString("nodeDeploymentID");

    sendHtmlResponse(response, Index.getIndex(nodeDeploymentID));
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
    String nodeDeploymentID = config().getString("nodeDeploymentID");
    MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer("heatSensors", msg -> {
      JsonObject payload = msg.body();
      String eventType = payload.getString("eventType");
      String sensorNodeDeploymentID = payload.getString("nodeDeploymentID");
      String heatSensorDeploymentID = payload.getString("heatSensorDeploymentID");


      if (eventType.equals("sensorDeployment") && nodeDeploymentID.equals(sensorNodeDeploymentID)) {
        deployHeatSensor(response, nodeDeploymentID);
      } else if (eventType.equals("sensorUndeployment") && nodeDeploymentID.equals(sensorNodeDeploymentID)) {
        undeployHeatSensor(response, nodeDeploymentID, heatSensorDeploymentID);
      } else if (eventType.equals("sensorUpdate")) {
        consumeSensorData(response, payload);
      }
    });
    onStartConsumer(response, consumer, nodeDeploymentID, "heatSensors");
  }

  private void deployHeatSensor(HttpServerResponse response, String nodeDeploymentID) {
    JsonObject config = new JsonObject();
    config.put("nodeDeploymentID", nodeDeploymentID);
    DeploymentOptions options = new DeploymentOptions().setConfig(config);

    vertx.deployVerticle(HeatSensor.class.getName(), options, res -> {
      if (res.succeeded()) {
        String heatSensorDeploymentID = res.result();
        registerVerticle(nodeDeploymentID, heatSensorDeploymentID).onComplete(ar -> {
          if (ar.succeeded()) {
            addHeatSensor(response, nodeDeploymentID, heatSensorDeploymentID);
          } else {
            response.setStatusCode(500).end("Failed to deploy verticle");
          }
        });
      } else {
        response.setStatusCode(500).end("Failed to deploy verticle");
      }
    });
  }

  private void undeployHeatSensor(HttpServerResponse response, String nodeDeploymentID, String heatSensorDeploymentID) {
    vertx.undeploy(heatSensorDeploymentID, res -> {
      if (res.succeeded()) {
        unregisterVerticle(nodeDeploymentID, heatSensorDeploymentID).onComplete(ar -> {
          if (ar.succeeded()) {
            addHeatSensor(response, nodeDeploymentID, heatSensorDeploymentID);
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
      String heatSensorDeploymentID = pathParams.get("heatSensorDeploymentID");
      heatSensorSubscribe(response, heatSensorDeploymentID);
    });
  }

  private void heatSensorStartUpdatesHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    String nodeDeploymentID = config().getString("nodeDeploymentID");
    routingContext.request().bodyHandler(body -> {
      Map<String, String> pathParams = routingContext.pathParams();
      String heatSensorDeploymentID = pathParams.get("heatSensorDeploymentID");
      JsonObject payload = new JsonObject()
        .put("nodeDeploymentID", nodeDeploymentID)
        .put("heatSensorDeploymentID", heatSensorDeploymentID)
        .put("eventType", "startUpdates");
      vertx.eventBus().publish("heatSensors", payload);
      heatSensorStartUpdates(response, heatSensorDeploymentID);
      response.end();
    });
  }

  private void heatSensorStopUpdatesHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    String nodeDeploymentID = config().getString("nodeDeploymentID");
    routingContext.request().bodyHandler(body -> {
      Map<String, String> pathParams = routingContext.pathParams();
      String heatSensorDeploymentID = pathParams.get("heatSensorDeploymentID");
      JsonObject payload = new JsonObject()
        .put("nodeDeploymentID", nodeDeploymentID)
        .put("heatSensorDeploymentID", heatSensorDeploymentID)
        .put("eventType", "stopUpdates");
      vertx.eventBus().publish("heatSensors", payload);
      heatSensorStopUpdates(response, heatSensorDeploymentID);
      response.end();
    });
  }

  private void heatSensorUnsubscribeHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    routingContext.request().bodyHandler(body -> {
      Map<String, String> pathParams = routingContext.pathParams();
      String heatSensorDeploymentID = pathParams.get("heatSensorDeploymentID");
      heatSensorUnsubscribe(response, heatSensorDeploymentID);
      response.end();
    });
  }


  private void heatSensorDeployHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    routingContext.request().bodyHandler(body -> {
      Map<String, String> pathParams = routingContext.pathParams();
      String nodeDeploymentID = pathParams.get("nodeDeploymentID");
      JsonObject payload = new JsonObject()
        .put("eventType", "sensorDeployment")
        .put("nodeDeploymentID", nodeDeploymentID)
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
      String nodeDeploymentID = pathParams.get("nodeDeploymentID");
      JsonObject payload = new JsonObject()
        .put("eventType", "sensorUndeployment")
        .put("sensorNodeDeploymentID", nodeDeploymentID)
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

  private void onStartConsumer(HttpServerResponse response, MessageConsumer<JsonObject> consumer, String nodeDeploymentID, String type) {
    logger.info("sensor.updates consumer started for: {}", nodeDeploymentID);
    JsonObject openConsumer = new JsonObject().put("type", type).put("response", response).put("consumer", consumer);
    openConnections.put(nodeDeploymentID, openConsumer);
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


  private Future<Long> registerVerticle(String nodeDeploymentID, String heatSensorDeploymentID) {
    SharedData sharedData = vertx.sharedData();

    Future<Long> incrementFuture = sharedData.getCounter("heatSensorVerticleCount").compose(counter ->
      counter.incrementAndGet()
    );

    Future<Void> mapUpdateFuture = sharedData.<String, String>getAsyncMap("verticleRegistry").compose(map ->
      map.put(nodeDeploymentID, heatSensorDeploymentID).mapEmpty()
    );

    return CompositeFuture.all(incrementFuture, mapUpdateFuture).compose(result -> {
      Long incrementedCount = result.resultAt(0);
      return Future.succeededFuture(incrementedCount);
    });
  }


  private Future<Void> unregisterVerticle(String nodeDeploymentID, String heatSensorDeploymentID) {
    SharedData sharedData = vertx.sharedData();

    // Decrement the counter
    Future<Void> decrementFuture = sharedData.getCounter("heatSensorVerticleCount").compose(counter ->
      counter.decrementAndGet().mapEmpty()
    );

    // Remove the entry from the verticleRegistry map
    Future<Void> mapUpdateFuture = sharedData.<String, String>getAsyncMap("verticleRegistry").compose(map ->
      map.remove(nodeDeploymentID).mapEmpty()
    );

    // Combine both futures
    return CompositeFuture.all(decrementFuture, mapUpdateFuture).mapEmpty();
  }


}

//  private void heatSensorDeployHandler(RoutingContext routingContext) {
//    HttpServerResponse response = routingContext.response();
//    setHeaders(response);
//    String nodeDeploymentID = config().getString("nodeDeploymentID");
//    routingContext.request().bodyHandler(body -> {
//      JsonObject config = new JsonObject();
//      config.put("nodeDeploymentID", nodeDeploymentID);
//      DeploymentOptions options = new DeploymentOptions().setConfig(config);
//
//      vertx.deployVerticle(HeatSensor.class.getName(), options, res -> {
//        if (res.succeeded()) {
//          String heatSensorDeploymentID = res.result();
//          registerVerticle(nodeDeploymentID, heatSensorDeploymentID).onComplete(ar -> {
//            if (ar.succeeded()) {
//              addHeatSensor(response, nodeDeploymentID, heatSensorDeploymentID);
//              JsonObject payload = new JsonObject();
//              payload.put("nodeDeploymentID", nodeDeploymentID);
//              payload.put("heatSensorDeploymentID", heatSensorDeploymentID);
//              vertx.eventBus().publish("cluster.heatsensors", payload);
//
//              response.end();
//            } else {
//              response.setStatusCode(500).end("Failed to deploy verticle");
//            }
//          });
//        } else {
//          response.setStatusCode(500).end("Failed to deploy verticle");
//        }
//      });
//    });
//  }


//  private void checkDeployedVerticles() {
//    vertx.sharedData().<String, String>getAsyncMap("sensorDataMap", res -> {
//      if (res.succeeded()) {
//        AsyncMap<String, String> sensorDataMap = res.result();
//        // Iterate through the map to find and remove entries associated with the session
//        sensorDataMap.entries(mapRes -> {
//          if (mapRes.succeeded()) {
//            mapRes.result().forEach((id, storedSessionId) -> {
//              if (storedSessionId.equals(sessionId)) {
//                sensorDataMap.remove(id, removeRes -> {
//                  if (removeRes.succeeded()) {
//                    logger.info("Removed sensor data for ID: {}", id);
//                  } else {
//                    logger.error("Failed to remove sensor data for ID: {}", id, removeRes.cause());
//                  }
//                });
//              }
//            });
//          } else {
//            logger.error("Failed to retrieve entries from sensorDataMap", mapRes.cause());
//          }
//        });
//      } else {
//        logger.error("Failed to get sensorDataMap", res.cause());
//      }
//    });
//  }


//  private void cleanupConsumer(String sessionId) {
//    MessageConsumer<JsonObject> consumer = consumers.remove(sessionId);
//    if (consumer != null && consumer.isRegistered()) {
//      consumer.unregister().onComplete(res -> {
//        if (res.succeeded()) {
//          logger.info("Consumer unregistered successfully for session: {}", sessionId);
//        } else {
//          logger.error("Failed to unregister consumer for session: {}", sessionId, res.cause());
//        }
//      });
//    }
//  }

//  private void rootConsumer(HttpServerResponse response, Session session) {
//    String thisNodeDeploymentID = config().getString("nodeDeploymentID");
//    MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer("cluster.heatsensors", msg -> {
//      String nodeDeploymentID = msg.body().getString("nodeDeploymentID");
//      String heatSensorDeploymentID = msg.body().getString("heatSensorDeploymentID");
//      if (!response.closed() && !nodeDeploymentID.equals(thisNodeDeploymentID)) {
//        if (!externalNodes.contains(nodeDeploymentID)) {
//          // If external node isn't discovered add it with a container for it
//          externalNodes.add(nodeDeploymentID);
//          addNodeContainer(response, nodeDeploymentID);
//        }
////        if (!this.heatSensors.contains(heatSensorDeploymentID)) {
////          // If Sensor isn't added add it.
////          logger.info("Heat Sensor ID not found: {}", heatSensorDeploymentID);
////          this.heatSensors.add(heatSensorDeploymentID);
////          addHeatSensorsContainer(response, nodeDeploymentID);
////          addHeatSensor(response, nodeDeploymentID, heatSensorDeploymentID);
////          response.end();
////        }
//      }
//    });
//    onStartConsumer(response, consumer, session);
//  }
//  private void heatSensorConsumer(HttpServerResponse response, String heatSensorDeploymentID) {
//    MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer("sensor.updates." + heatSensorDeploymentID, msg -> {
//      if (!response.closed()) {
////        String heatSensorDeploymentID = msg.body().getString("heatSensorDeploymentID");
//        String temp = msg.body().getString("temp");
//        if (!this.locallyDisplayedHeatSensors.contains(heatSensorDeploymentID)) {
//          logger.info("Heat Sensor ID not found: {}", heatSensorDeploymentID);
//          this.locallyDisplayedHeatSensors.add(heatSensorDeploymentID);
//          addSensorData(response, heatSensorDeploymentID, temp);
//        } else {
//          logger.info("Heat Sensor ID found: {}", heatSensorDeploymentID);
//          editSensorData(response, heatSensorDeploymentID, temp);
//        }
//      }
//    });
//    onStartConsumer(response, consumer, id);
//  }
//    private void heatSensorConsumer (HttpServerResponse response, String heatSensorDeploymentID){
//      MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer("sensor.updates." + heatSensorDeploymentID, msg -> {
////      if (!response.closed()) {
//        String temp = msg.body().getString("temp");
//        consumeSensorData(response, heatSensorDeploymentID, temp);
////      }
//      });
//      onStartConsumer(response, consumer, heatSensorDeploymentID);
//    }


//  private void heatSensorUnsubscribeHandler(RoutingContext routingContext) {
//    HttpServerResponse response = routingContext.response();
//    setHeaders(response);
//    routingContext.request().bodyHandler(body -> {
//      Map<String, String> pathParams = routingContext.pathParams();
//      String heatSensorDeploymentID = pathParams.get("heatSensorDeploymentID");
//
//      HttpServerResponse openConnection = openConnections.get(heatSensorDeploymentID);
//      openConnection.end();
//
//      heatSensorUnsubscribe(response, heatSensorDeploymentID);
//      response.end();
//    });
//  }
