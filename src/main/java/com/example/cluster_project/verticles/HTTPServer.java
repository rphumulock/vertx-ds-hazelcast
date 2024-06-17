//package com.example.cluster_project.verticles;
//
//import com.example.cluster_project.ui.templates.Index;
//import com.example.cluster_project.ui.partials.Partials;
//import com.example.cluster_project.utils.DatastarUtils;
//import com.example.cluster_project.utils.SSEConfig;
//
//import io.vertx.core.AbstractVerticle;
//import io.vertx.core.Promise;
//import io.vertx.core.eventbus.MessageConsumer;
//import io.vertx.core.http.HttpServerResponse;
//import io.vertx.core.json.JsonObject;
//import io.vertx.core.shareddata.AsyncMap;
//import io.vertx.ext.web.Router;
//import io.vertx.ext.web.RoutingContext;
//import io.vertx.ext.web.Session;
//import io.vertx.ext.web.handler.SessionHandler;
//import io.vertx.ext.web.sstore.ClusteredSessionStore;
//import io.vertx.ext.web.sstore.SessionStore;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.util.Date;
//import java.util.Map;
//import java.util.UUID;
//import java.util.concurrent.ConcurrentHashMap;
//
//import static com.example.cluster_project.utils.DatastarUtils.sendSSE;
//import static com.example.cluster_project.utils.DatastarUtils.setHeaders;
//
//public class HTTPServer extends AbstractVerticle {
//
//  public static final String TEMPLATE = "Session [%s] created on %s%n"
//    + "%n"
//    + "Page generated on %s%n";
//
//  private static final Logger logger = LoggerFactory.getLogger(HTTPServer.class);
//
//  private final Map<String, MessageConsumer<JsonObject>> localConsumers = new ConcurrentHashMap<>();
//
//  @Override
//  public void start(Promise<Void> startPromise) throws Exception {
//    Router router = Router.router(vertx);
//    JsonObject config = config();
//    int port = config.getInteger("http.port", 8080);
//
//    // Set up the clustered session store
//    SessionStore store = ClusteredSessionStore.create(vertx);
//    router.route().handler(SessionHandler.create(store));
//
//    setupRoutes(router);
//    vertx.createHttpServer()
//      .requestHandler(router)
//      .listen(port)
//      .onSuccess(v -> {
//        startPromise.complete();
//        logger.info("HTTP server started successfully on: http://localhost:" + port);
//      })
//      .onFailure(startPromise::fail);
//  }
//
//
//  // UI Functions
//  private SSEConfig buildConfig(
//    String withId,
//    String withSelector,
//    String withMergeType,
//    Number withSettle,
//    String withFragment,
//    boolean withEnd
//  ) {
//    return new SSEConfig.Builder()
//      .withId(withId)
//      .withSelector(withSelector)
//      .withMergeType(withMergeType)
//      .withSettle(withSettle)
//      .withFragment(withFragment)
//      .withEnd(withEnd)
//      .build();
//  }
//
//  private void subscribeClickedUI(HttpServerResponse response) {
//    sendSSE(response, buildConfig(
//      UUID.randomUUID().toString(),
//      "#subscribeContainer",
//      DatastarUtils.MergeTypes.MORPH_ELEMENT.getType(),
//      0,
//      Partials.unsubscribeSensorUpdates().render(),
//      false
//    ));
//  }
//
//  private void deleteSensorDataUI(HttpServerResponse response, String id) {
//    sendSSE(response, buildConfig(
//      UUID.randomUUID().toString(),
//      "#" + id,
//      DatastarUtils.MergeTypes.DELETE_ELEMENT.getType(),
//      0,
//      "<div></div>",
//      false
//    ));
//  }
//
//  private void appendSensorDataUI(HttpServerResponse response, String deploymentID, String id, String temp) {
//    sendSSE(response, buildConfig(
//      UUID.randomUUID().toString(),
//      "#sensorUpdatesContainer",
//      DatastarUtils.MergeTypes.APPEND_ELEMENT.getType(),
//      0,
//      Partials.sensorUpdate(deploymentID, id, temp).render(),
//      false
//    ));
//  }
//
//  private void morphSensorDataUI(HttpServerResponse response, String deploymentID, String id, String temp) {
//    // ID exists, perform MORPH_ELEMENT
//    sendSSE(response, buildConfig(
//      UUID.randomUUID().toString(),
//      "#" + id,
//      DatastarUtils.MergeTypes.MORPH_ELEMENT.getType(),
//      0,
//      Partials.sensorUpdate(deploymentID, id, temp).render(),
//      false
//    ));
//  }
//
//  private void morphUnsubscribeUI(HttpServerResponse response) {
//    sendSSE(response, buildConfig(
//      UUID.randomUUID().toString(),
//      "#unsubscribeContainer",
//      DatastarUtils.MergeTypes.MORPH_ELEMENT.getType(),
//      0,
//      Partials.subscribeSensorUpdates().render(),
//      true
//    ));
//  }
//
//  private void setupRoutes(Router router) {
//    router.get("/").handler(this::rootHandler);
//    router.get("/subscribeSensorUpdates").handler(this::subscribeSensorUpdates);
//    router.get("/unsubscribeSensorUpdates").handler(this::unsubscribeSensorUpdates);
//  }
//
//  private void rootHandler(RoutingContext ctx) {
//    Session session = ctx.session();
//    session.computeIfAbsent("createdOn", s -> System.currentTimeMillis());
//    String sessionId = session.id();
//    Date createdOn = new Date(session.<Long>get("createdOn"));
//    Date now = new Date();
//    logger.debug(String.format(TEMPLATE, sessionId, createdOn, now));
//    DatastarUtils.sendHtmlResponse(ctx.response(), Index.getIndex(config()));
//  }
//
//  private void subscribeSensorUpdates(RoutingContext ctx) {
//    HttpServerResponse response = setHeaders(ctx.response());
//    String sessionId = ctx.session().id();
//    subscribeClickedUI(response);
//    setupConsumer(response, sessionId);
//  }
//
//  public void setupConsumer(HttpServerResponse response, String sessionId) {
//    // Register Consumer Stream
//    MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer("sensor.updates", msg -> {
//
//      vertx.sharedData().<String, String>getAsyncMap("sensorDataMap", res -> {
//        if (res.succeeded()) {
//
//          String deploymentID = msg.body().getString("deploymentID");
//          String id = msg.body().getString("id");
//          String temp = msg.body().getString("temp");
//
//          AsyncMap<String, String> sensorDataMap = res.result();
//          sensorDataMap.get(id, asyncResult -> {
//            if (asyncResult.succeeded() && asyncResult.result() == null) {
//              // ID does not exist, perform DELETE and APPEND
//              sensorDataMap.put(sessionId, id, putRes -> {
//                if (putRes.succeeded()) {
//                  deleteSensorDataUI(response, id);
//                  appendSensorDataUI(response, id, id, temp);
//                } else {
//                  logger.error("Failed to put ID into sensorDataMap", putRes.cause());
//                }
//              });
//            } else {
//              morphSensorDataUI(response, deploymentID, id, temp);
//            }
//          });
//        } else {
//          logger.error("Failed to get sensorDataMap", res.cause());
//        }
//      });
//    });
//
//    localConsumers.put(sessionId, consumer);
//
//    consumer.endHandler(unused -> {
//      cleanupConsumer(sessionId);
//    });
//  }
//
//  private void unsubscribeSensorUpdates(RoutingContext routingContext) {
//    HttpServerResponse response = routingContext.response();
//    setHeaders(response);
//    String sessionId = routingContext.session().id();
//    String deploymentID = config().getString("deploymentID");
//
//    // Cleanup consumer
//    cleanupConsumer(sessionId);
//    morphUnsubscribeUI(response);
//  }
//
//  public void cleanupConsumer(String sessionId) {
//    vertx.sharedData().<String, String>getAsyncMap("sensorDataMap", res -> {
//      if (res.succeeded()) {
//        AsyncMap<String, String> sensorDataMap = res.result();
//
//        // Remove sensors tied to the sessionId
//        sensorDataMap.keys(ar -> {
//          if (ar.succeeded()) {
//            ar.result().forEach(key -> {
//              sensorDataMap.get(key, valueResult -> {
//                if (valueResult.succeeded() && valueResult.result() != null && valueResult.result().equals(sessionId)) {
//                  sensorDataMap.remove(key, removeResult -> {
//                    if (removeResult.succeeded()) {
//                      logger.info("Removed sensor data for session: {} and key: {}", sessionId, key);
//                    } else {
//                      logger.error("Failed to remove sensor data for session: {} and key: {}", sessionId, key, removeResult.cause());
//                    }
//                  });
//                }
//              });
//            });
//          } else {
//            logger.error("Failed to retrieve keys from sensorDataMap", ar.cause());
//          }
//        });
//
//        // Unregister consumer
//        MessageConsumer<JsonObject> consumer = localConsumers.remove(sessionId);
//        if (consumer != null && consumer.isRegistered()) {
//          consumer.unregister().onComplete(unregRes -> {
//            if (unregRes.succeeded()) {
//              consumerSessions.remove(sessionId);
//              logger.info("Consumer cleaned up successfully for session: {}", sessionId);
//            } else {
//              logger.error("Failed to unregister consumer for session: {}", sessionId, unregRes.cause());
//            }
//          });
//        } else {
//          logger.error("Consumer not found or already unregistered for session: {}", sessionId);
//        }
//      } else {
//        logger.error("Failed to access sensorDataMap", res.cause());
//      }
//    });
//  }
//
//
//}

//package com.example.cluster_project.verticles;
//
//import com.example.cluster_project.services.ConsumerManagerService;
//import com.example.cluster_project.ui.templates.Index;
//import com.example.cluster_project.ui.partials.Partials;
//import com.example.cluster_project.utils.DatastarUtils;
//import com.example.cluster_project.utils.SSEConfig;
//import io.vertx.core.AbstractVerticle;
//import io.vertx.core.Promise;
//import io.vertx.core.http.HttpServerResponse;
//import io.vertx.core.json.JsonObject;
//import io.vertx.core.shareddata.AsyncMap;
//import io.vertx.ext.web.Router;
//import io.vertx.ext.web.RoutingContext;
//import io.vertx.ext.web.Session;
//import io.vertx.ext.web.handler.SessionHandler;
//import io.vertx.ext.web.sstore.ClusteredSessionStore;
//import io.vertx.ext.web.sstore.SessionStore;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.util.Date;
//import java.util.UUID;
//
//import static com.example.cluster_project.utils.DatastarUtils.sendSSE;
//import static com.example.cluster_project.utils.DatastarUtils.setHeaders;
//
//public class HTTPServer extends AbstractVerticle {
//
//  public static final String TEMPLATE = "Session [%s] created on %s%n"
//    + "%n"
//    + "Page generated on %s%n";
//
//  private static final Logger logger = LoggerFactory.getLogger(HTTPServer.class);
//
//  private ConsumerManagerService consumerManagerService;
//
//  @Override
//  public void start(Promise<Void> startPromise) throws Exception {
//    consumerManagerService = new ConsumerManagerService(vertx);
//
//    Router router = Router.router(vertx);
//    JsonObject config = config();
//    int port = config.getInteger("http.port", 8080);
//
//    // Set up the clustered session store
//    SessionStore store = ClusteredSessionStore.create(vertx);
//    router.route().handler(SessionHandler.create(store));
//
//    setupRoutes(router);
//    vertx.createHttpServer()
//      .requestHandler(router)
//      .listen(port)
//      .onSuccess(v -> {
//        startPromise.complete();
//        logger.info("HTTP server started successfully on: http://localhost:" + port);
//      })
//      .onFailure(startPromise::fail);
//  }
//
//  private void setupRoutes(Router router) {
//    router.get("/").handler(this::rootHandler);
//    router.get("/subscribeSensorUpdates").handler(this::subscribeSensorUpdates);
//    router.get("/unsubscribeSensorUpdates").handler(this::unsubscribeSensorUpdates);
//  }
//
//  private void rootHandler(RoutingContext ctx) {
//    Session session = ctx.session();
//    session.computeIfAbsent("createdOn", s -> System.currentTimeMillis());
//    String sessionId = session.id();
//    Date createdOn = new Date(session.<Long>get("createdOn"));
//    Date now = new Date();
//    logger.debug(String.format(TEMPLATE, sessionId, createdOn, now));
//    DatastarUtils.sendHtmlResponse(ctx.response(), Index.getIndex(config()));
//  }
//
//  private void subscribeSensorUpdates(RoutingContext ctx) {
//    HttpServerResponse response = setHeaders(ctx.response());
//    String sessionId = ctx.session().id();
//
//    sendSSE(response, buildConfig(
//      UUID.randomUUID().toString(),
//      "#subscribeContainer",
//      DatastarUtils.MergeTypes.MORPH_ELEMENT.getType(),
//      0,
//      Partials.unsubscribeSensorUpdates().render(),
//      false
//    ));
//
//    // Setup consumer
//    consumerManagerService.setupConsumer(sessionId);
//
//    // Listen for responses from the consumer manager service
//    vertx.eventBus().consumer("consumer.response." + sessionId, msg -> {
//      JsonObject body = (JsonObject) msg.body();
//      String deploymentID = body.getString("deploymentID");
//      String id = body.getString("id");
//      String temp = body.getString("temp");
//
//      vertx.sharedData().<String, String>getAsyncMap("sensorDataMap", res -> {
//        if (res.succeeded()) {
//          AsyncMap<String, String> sensorDataMap = res.result();
//          sensorDataMap.get(id, asyncResult -> {
//            if (asyncResult.succeeded() && asyncResult.result() == null) {
//              // ID does not exist, perform DELETE and APPEND
//              sensorDataMap.put(id, sessionId, putRes -> {
//                if (putRes.succeeded()) {
//                  sendSSE(response, buildConfig(
//                    UUID.randomUUID().toString(),
//                    "#" + id,
//                    DatastarUtils.MergeTypes.DELETE_ELEMENT.getType(),
//                    0,
//                    "<div></div>",
//                    false
//                  ));
//                  sendSSE(response, buildConfig(
//                    UUID.randomUUID().toString(),
//                    "#sensorUpdatesContainer",
//                    DatastarUtils.MergeTypes.APPEND_ELEMENT.getType(),
//                    0,
//                    Partials.sensorUpdate(deploymentID, id, temp).render(),
//                    false
//                  ));
//                } else {
//                  logger.error("Failed to put ID into sensorDataMap", putRes.cause());
//                }
//              });
//            } else {
//              // ID exists, perform MORPH_ELEMENT
//              sendSSE(response, buildConfig(
//                UUID.randomUUID().toString(),
//                "#" + id,
//                DatastarUtils.MergeTypes.MORPH_ELEMENT.getType(),
//                0,
//                Partials.sensorUpdate(deploymentID, id, temp).render(),
//                false
//              ));
//            }
//          });
//        } else {
//          logger.error("Failed to get sensorDataMap", res.cause());
//        }
//      });
//    });
//  }
//
//  private void unsubscribeSensorUpdates(RoutingContext routingContext) {
//    HttpServerResponse response = routingContext.response();
//    setHeaders(response);
//    String sessionId = routingContext.session().id();
//    String deploymentID = config().getString("deploymentID");
//
//    // Cleanup consumer
//    consumerManagerService.cleanupConsumer(sessionId);
//
//    // Clear sensorDataMap entries associated with the session
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
//
//    sendSSE(response, buildConfig(
//      UUID.randomUUID().toString(),
//      "#unsubscribeContainer",
//      DatastarUtils.MergeTypes.MORPH_ELEMENT.getType(),
//      0,
//      Partials.subscribeSensorUpdates().render(),
//      true
//    ));
//  }
//
//  private SSEConfig buildConfig(
//    String withId,
//    String withSelector,
//    String withMergeType,
//    Number withSettle,
//    String withFragment,
//    boolean withEnd
//  ) {
//    return new SSEConfig.Builder()
//      .withId(withId)
//      .withSelector(withSelector)
//      .withMergeType(withMergeType)
//      .withSettle(withSettle)
//      .withFragment(withFragment)
//      .withEnd(withEnd)
//      .build();
//  }
//}
//
// package com.example.cluster_project.verticles;
//
//import com.example.cluster_project.ui.templates.Index;
//import com.example.cluster_project.ui.partials.Partials;
//import com.example.cluster_project.utils.DatastarUtils;
//import com.example.cluster_project.utils.SSEConfig;
//
//import io.vertx.core.AbstractVerticle;
//import io.vertx.core.eventbus.Message;
//import io.vertx.core.eventbus.MessageConsumer;
//import io.vertx.core.http.HttpServerResponse;
//import io.vertx.core.json.JsonObject;
//
//import io.vertx.ext.web.Router;
//import io.vertx.ext.web.RoutingContext;
//
//import java.util.*;
//import java.util.concurrent.ConcurrentHashMap;
//
//import static com.example.cluster_project.utils.DatastarUtils.sendSSE;
//import static com.example.cluster_project.utils.DatastarUtils.setHeaders;
//
//import io.vertx.ext.web.Session;
//import io.vertx.ext.web.handler.SessionHandler;
//import io.vertx.ext.web.sstore.ClusteredSessionStore;
//import io.vertx.ext.web.sstore.SessionStore;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//public class HTTPServer extends AbstractVerticle {
//
//  public static final String TEMPLATE = ""
//    + "Session [%s] created on %s%n"
//    + "%n"
//    + "Page generated on %s%n";
//
//  private static final Logger logger = LoggerFactory.getLogger(HTTPServer.class);
//
//  private final Map<String, MessageConsumer<JsonObject>> consumers = new ConcurrentHashMap<>();
//  private final Map<String, HttpServerResponse> connections = new ConcurrentHashMap<>();
//  Set<String> concurrentSet = Collections.newSetFromMap(new ConcurrentHashMap<>());
//
//  @Override
//  public void start() throws Exception {
//    Router router = Router.router(vertx);
//    JsonObject config = config();
//    int port = config.getInteger("http.port", 8080);
//
//    // Set up the clustered session store
//    SessionStore store = ClusteredSessionStore.create(vertx);
//    router.route().handler(SessionHandler.create(store));
//
//    setupRoutes(router);
//    vertx.createHttpServer()
//      .requestHandler(router)
//      .listen(port)
//      .onSuccess(v -> logger.info("Starter server successfully on: http://localhost:{}", port))
//      .onFailure(t -> logger.error("Failed to unregister consumer.", t));
//  }
//
//  private void setupRoutes(Router router) {
//    router.get("/").handler(this::rootHandler);
//    router.get("/subscribeSensorUpdates").handler(this::subscribeSensorUpdates);
//    router.get("/unsubscribeSensorUpdates").handler(this::unsubscribeSensorUpdates);
//  }
//
//  private void rootHandler(RoutingContext ctx) {
//    Session session = ctx.session();
//    session.computeIfAbsent("createdOn", s -> System.currentTimeMillis()); // (3)
//    String sessionId = session.id();
//    Date createdOn = new Date(session.<Long>get("createdOn"));
//    Date now = new Date();
//    logger.info(String.format(TEMPLATE, sessionId, createdOn, now)); // (4)
//    String deploymentID = config().getString("deploymentID");
//    DatastarUtils.sendHtmlResponse(ctx.response(), Index.getIndex(deploymentID));
//  }
//
//  private void subscribeSensorUpdates(RoutingContext ctx) {
//    HttpServerResponse response = setHeaders(ctx.response());
//    String sessionId = ctx.session().id();
//    sendSSE(response, buildConfig(
//      UUID.randomUUID().toString(),
//      "#subscribeContainer",
//      DatastarUtils.MergeTypes.MORPH_ELEMENT.getType(),
//      0,
//      Partials.unsubscribeSensorUpdates().render(),
//      false
//    ));
//    setupConsumer(sessionId, response);
//  }
//
//  private void unsubscribeSensorUpdates(RoutingContext routingContext) {
//    HttpServerResponse response = routingContext.response();
//    setHeaders(response);
//    String sessionId = routingContext.session().id();
//    this.concurrentSet.clear();
//
//    HttpServerResponse consumerConnection = this.connections.get(sessionId);
//    consumerConnection.end();
//
//    response.endHandler((h) -> {
//      cleanupConsumer(sessionId);
//    });
//
//    sendSSE(response, buildConfig(
//      UUID.randomUUID().toString(),
//      "#unsubscribeContainer",
//      DatastarUtils.MergeTypes.MORPH_ELEMENT.getType(),
//      0,
//      Partials.subscribeSensorUpdates().render(),
//      true
//    ));
//  }
//
//  private void setupConsumer(String sessionId, HttpServerResponse response) {
//    MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer("sensor.updates", msg -> consumeMessage(msg, sessionId, response));
//    consumers.put(sessionId, consumer);
//    connections.put(sessionId, response);
//    consumer.endHandler((Void v) -> onEndConsumer(response));
//    response.endHandler((Void v) -> {
//      cleanupConsumer(sessionId);
//    });
//  }
//
//  private void consumeMessage(Message<JsonObject> msg, String sessionId, HttpServerResponse response) {
//    if (!response.closed()) {
//      String id = msg.body().getString("id");
//      String temp = msg.body().getString("temp");
//      String deploymentID = config().getString("deploymentID");
//
//      if (!this.concurrentSet.contains(id)) {
//        logger.info("SensorID Found: {}", id);
//        this.concurrentSet.add(id);
//        sendSSE(response, buildConfig(
//          UUID.randomUUID().toString(),
//          "#" + id,
//          DatastarUtils.MergeTypes.DELETE_ELEMENT.getType(),
//          0,
//          "<div></div>",
//          false
//        ));
//        sendSSE(response, buildConfig(
//          UUID.randomUUID().toString(),
//          "#sensorUpdatesContainer",
//          DatastarUtils.MergeTypes.APPEND_ELEMENT.getType(),
//          0,
//          Partials.sensorUpdate(deploymentID, id, temp).render(),
//          false
//        ));
//      } else {
//        logger.info("SensorID not Found: {}", id);
//        sendSSE(response, buildConfig(
//          UUID.randomUUID().toString(),
//          "#" + id,
//          DatastarUtils.MergeTypes.MORPH_ELEMENT.getType(),
//          0,
//          Partials.sensorUpdate(deploymentID, id, temp).render(),
//          false
//        ));
//      }
//    }
//  }
//
//  private void onEndConsumer(HttpServerResponse response) {
//    logger.info("Closing connection....");
//    this.concurrentSet.clear();
//  }
//
//  private SSEConfig buildConfig(
//    String withId,
//    String withSelector,
//    String withMergeType,
//    Number withSettle,
//    String withFragment,
//    boolean withEnd
//  ) {
//    return new SSEConfig.Builder()
//      .withId(withId)
//      .withSelector(withSelector)
//      .withMergeType(withMergeType)
//      .withSettle(withSettle)
//      .withFragment(withFragment)
//      .withEnd(withEnd)
//      .build();
//  }
//
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
//
//}


//  private void deployVerticleSelection(RoutingContext routingContext) {
//    HttpServerResponse response = routingContext.response();
//    setHeaders(response);
//
//    String nodeDeploymentID = config().getString("nodeDeploymentID");
//
//    // Read the body asynchronously
//    routingContext.request().bodyHandler(body -> {
//      // Convert the body to a string (or any other format you need)
//      JsonObject jsonBody = body.toJsonObject();
//      String selection = jsonBody.getString("verticleSelection");
//
//      String verticleName;
//      if (selection.equals("2")) {
//        verticleName = HTTPServer.class.getName();
//      } else if (selection.equals("3")) {
//        verticleName = SensorData.class.getName();
//      } else {
//        verticleName = HeatSensor.class.getName();
//      }
//
//      JsonObject config = new JsonObject();
//      config.put("nodeDeploymentID", nodeDeploymentID);
//      DeploymentOptions options = new DeploymentOptions().setConfig(config);
//
//      vertx.deployVerticle(verticleName, options, res -> {
//        if (res.succeeded()) {
//          String heatSensorDeploymentID = res.result();
//          registerVerticle(response, nodeDeploymentID, heatSensorDeploymentID);
//          incrementVerticleCount(response, nodeDeploymentID, heatSensorDeploymentID);
//          response.end();
//        }
//      });
//    });
//  }
//
//  private void registerVerticle(HttpServerResponse response, String nodeDeploymentID, String heatSensorDeploymentID) {
//    SharedData sharedData = vertx.sharedData();
//
//    // Add deployment ID and verticle name to shared map
//    sharedData.<String, String>getAsyncMap("verticleRegistry", mapRes -> {
//      if (mapRes.succeeded()) {
//        AsyncMap<String, String> map = mapRes.result();
//        map.put(nodeDeploymentID, heatSensorDeploymentID, putRes -> {
//          if (putRes.failed()) {
//            response.setStatusCode(500).end("Failed to update verticle registry");
//          }
//        });
//      } else {
//        response.setStatusCode(500).end("Failed to access verticle registry");
//      }
//    });
//  }
//
//  private void incrementVerticleCount(HttpServerResponse response, String nodeDeploymentID, String heatSensorDeploymentID) {
//    SharedData sharedData = vertx.sharedData();
//
//    // Increment the shared counter for verticle count
//    sharedData.getCounter("verticleCount", counterRes -> {
//      if (counterRes.succeeded()) {
//        Counter counter = counterRes.result();
//        counter.incrementAndGet(incrementRes -> {
//          if (incrementRes.succeeded()) {
//            if (!this.heatSensors.contains(heatSensorDeploymentID)) {
//              // If Sensor isn't added add it.
//              logger.info("Heat Sensor ID not found: {}", heatSensorDeploymentID);
//              this.heatSensors.add(heatSensorDeploymentID);
//              addHeatSensorsContainer(response, nodeDeploymentID);
//              addHeatSensor(response, heatSensorDeploymentID);
//              response.end();
//            }
//          }
//          if (incrementRes.failed()) {
//            response.setStatusCode(500).end("Failed to update verticle count");
//          }
//        });
//      } else {
//        response.setStatusCode(500).end("Failed to access verticle count");
//      }
//    });
//  }


package com.example.cluster_project.verticles;

import com.example.cluster_project.ui.templates.Index;
import com.example.cluster_project.utils.DatastarUtils;

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

public class HTTPServer extends AbstractVerticle {

  public static final String TEMPLATE = ""
    + "Session [%s] created on %s%n"
    + "%n"
    + "Page generated on %s%n";

  private static final Logger logger = LoggerFactory.getLogger(HTTPServer.class);

//  private final Map<String, MessageConsumer<JsonObject>> consumers = new ConcurrentHashMap<>();
//  private final Map<String, HttpServerResponse> connections = new ConcurrentHashMap<>();

  Set<String> externalNodes = Collections.newSetFromMap(new ConcurrentHashMap<>());
  Set<String> heatSensorData = Collections.newSetFromMap(new ConcurrentHashMap<>());

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

  private void setupRoutes(Router router) {
    router.get("/").handler(this::rootHandler);
    router.get("/heatSensor/:heatSensorDeploymentID/subscribe").handler(this::heatSensorSubscribeHandler);
//    router.get("/heatSensor/:heatSensorDeploymentID/unsubscribe").handler(this::heatSensorUnsubscribeHandler);
    router.post("/deployVerticleSelection").handler(this::deployVerticleSelection);
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

    DatastarUtils.sendHtmlResponse(response, Index.getIndex(nodeDeploymentID));
  }

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

  private void heatSensorSubscribeHandler(RoutingContext routingContext) {
    Session session = routingContext.session();
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    routingContext.request().bodyHandler(body -> {
      Map<String, String> pathParams = routingContext.pathParams();
      String heatSensorDeploymentID = pathParams.get("heatSensorDeploymentID");
      heatSensorSubscribe(response, heatSensorDeploymentID);
      heatSensorConsumer(response, session);
    });
  }

//  private void heatSensorUnsubscribeHandler(RoutingContext routingContext) {
//    HttpServerResponse response = routingContext.response();
//    setHeaders(response);
//    routingContext.request().bodyHandler(body -> {
//      Map<String, String> pathParams = routingContext.pathParams();
//      String heatSensorDeploymentID = pathParams.get("heatSensorDeploymentID");
//      String sessionId = routingContext.session().id();
//
//      // Close out everything
//      HttpServerResponse consumerConnection = this.connections.get(sessionId);
//      consumerConnection.end();
//      response.endHandler(unused -> cleanupConsumer(sessionId));
//      this.heatSensorData.clear();
//      heatSensorUnsubscribe(response, heatSensorDeploymentID);
//      response.end();
//    });
//  }

  private void deployVerticleSelection(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);

    String nodeDeploymentID = config().getString("nodeDeploymentID");

    // Read the body asynchronously
    routingContext.request().bodyHandler(body -> {
      // Convert the body to a string (or any other format you need)
      JsonObject jsonBody = body.toJsonObject();
      String selection = jsonBody.getString("verticleSelection");

      String verticleName;
      if (selection.equals("2")) {
        verticleName = HTTPServer.class.getName();
      } else if (selection.equals("3")) {
        verticleName = SensorData.class.getName();
      } else {
        verticleName = HeatSensor.class.getName();
      }

      JsonObject config = new JsonObject();
      config.put("nodeDeploymentID", nodeDeploymentID);
      DeploymentOptions options = new DeploymentOptions().setConfig(config);

      vertx.deployVerticle(verticleName, options, res -> {
        if (res.succeeded()) {
          String heatSensorDeploymentID = res.result();
          registerVerticle(nodeDeploymentID, heatSensorDeploymentID).onComplete(ar -> {
            if (ar.succeeded()) {
              long count = ar.result(); // Get the count from the result

              addHeatSensor(response, nodeDeploymentID, heatSensorDeploymentID, count);

              JsonObject payload = new JsonObject();
              payload.put("nodeDeploymentID", nodeDeploymentID);
              payload.put("heatSensorDeploymentID", heatSensorDeploymentID);
              payload.put("count", count); // Include the count in the payload
              vertx.eventBus().publish("cluster.heatsensors", payload);

              response.end();
            } else {
              response.setStatusCode(500).end("Failed to deploy verticle");
            }
          });
        } else {
          response.setStatusCode(500).end("Failed to deploy verticle: " + verticleName);
        }
      });
    });
  }

  /*****************************************************************************************
   *  CONSUMERS
   *****************************************************************************************/

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
  private void heatSensorConsumer(HttpServerResponse response, Session session) {
    MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer("sensor.updates", msg -> {
      if (!response.closed()) {
        String heatSensorDeploymentID = msg.body().getString("heatSensorDeploymentID");
        String temp = msg.body().getString("temp");
        if (!this.heatSensorData.contains(heatSensorDeploymentID)) {
          logger.info("Heat Sensor ID not found: {}", heatSensorDeploymentID);
          this.heatSensorData.add(heatSensorDeploymentID);
          addSensorData(response, heatSensorDeploymentID, temp);
        } else {
          logger.info("Heat Sensor ID found: {}", heatSensorDeploymentID);
          editSensorData(response, heatSensorDeploymentID, temp);
        }
      }
    });
    onStartConsumer(response, consumer, session);
  }

  /*****************************************************************************************
   *  CONSUMER UTILITIES
   *****************************************************************************************/

  private void onStartConsumer(HttpServerResponse response, MessageConsumer<JsonObject> consumer, Session session) {
//    consumers.put(sessionId, consumer);
//    connections.put(sessionId, response);

    logger.info("sensor.updates consumer started for: {}", session.id());
    response.endHandler(unused -> cleanupConsumer(consumer, session));
    consumer.endHandler(this::onEndConsumer);
  }

  private void cleanupConsumer(MessageConsumer<JsonObject> consumer, Session session) {
    if (consumer.isRegistered()) {
      consumer.unregister().onComplete(res -> {
        if (res.succeeded()) {
          logger.debug("Consumer unregistered successfully for session: {} - created on: {}.",
            session.id(), new Date(session.<Long>get("createdOn")));
        } else {
          logger.error("Failed to unregister consumer for session: {} - created on: {}.",
            session.id(), session.<Long>get("createdOn"), res.cause());
        }
      });
    }
  }

  private void onEndConsumer(Void unused) {
    logger.info("Closing consumer connection....");
  }

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

  private Future<Void> unregisterVerticle(String nodeDeploymentID) {
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
