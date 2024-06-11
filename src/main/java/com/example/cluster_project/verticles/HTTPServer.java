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


package com.example.cluster_project.verticles;

import com.example.cluster_project.ui.templates.Index;
import com.example.cluster_project.ui.partials.Partials;
import com.example.cluster_project.utils.DatastarUtils;
import com.example.cluster_project.utils.SSEConfig;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;

import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.SharedData;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.example.cluster_project.utils.DatastarUtils.sendSSE;
import static com.example.cluster_project.utils.DatastarUtils.setHeaders;

import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.ClusteredSessionStore;
import io.vertx.ext.web.sstore.SessionStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HTTPServer extends AbstractVerticle {

  public static final String TEMPLATE = ""
    + "Session [%s] created on %s%n"
    + "%n"
    + "Page generated on %s%n";

  private static final Logger logger = LoggerFactory.getLogger(HTTPServer.class);

  private final Map<String, MessageConsumer<JsonObject>> consumers = new ConcurrentHashMap<>();
  private final Map<String, HttpServerResponse> connections = new ConcurrentHashMap<>();
  Set<String> concurrentSet = Collections.newSetFromMap(new ConcurrentHashMap<>());

  @Override
  public void start() throws Exception {
    Router router = Router.router(vertx);
    JsonObject config = config();
    int port = config.getInteger("http.port", 8080);

    // Set up the clustered session store
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
    router.route("/css/*").handler(this::stylesHandler);

    router.get("/subscribeSensorUpdates").handler(this::subscribeSensorUpdates);
    router.get("/unsubscribeSensorUpdates").handler(this::unsubscribeSensorUpdates);

    router.post("/deployVerticleSelection").handler(this::deployVerticleSelection);

  }

  private void stylesHandler(RoutingContext routingContext) {
    String path = routingContext.request().path();
    routingContext.response().sendFile("public" + path);
  }

  private void rootHandler(RoutingContext routingContext) {
    Session session = routingContext.session();
    session.computeIfAbsent("createdOn", s -> System.currentTimeMillis()); // (3)
    String sessionId = session.id();
    Date createdOn = new Date(session.<Long>get("createdOn"));
    Date now = new Date();
    logger.info(String.format(TEMPLATE, sessionId, createdOn, now)); // (4)
    String deploymentID = config().getString("deploymentID");
    DatastarUtils.sendHtmlResponse(routingContext.response(), Index.getIndex(deploymentID));
  }


  private void subscribeSensorUpdates(RoutingContext routingContext) {
    HttpServerResponse response = setHeaders(routingContext.response());
    String sessionId = routingContext.session().id();
    subscribeButtonUI(response);
    setupConsumer(sessionId, response);
  }

  private void unsubscribeSensorUpdates(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    String sessionId = routingContext.session().id();
    HttpServerResponse consumerConnection = this.connections.get(sessionId);
    consumerConnection.end();
    response.endHandler(unused -> cleanupConsumer(sessionId));
    unsubscribeButtonUI(response);
    this.concurrentSet.clear();
  }

  private void subscribeButtonUI(HttpServerResponse response) {
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#subscribeContainer",
      DatastarUtils.MergeTypes.MORPH_ELEMENT.getType(),
      0,
      Partials.unsubscribeSensorUpdates().render(),
      false
    ));
  }

  private void unsubscribeButtonUI(HttpServerResponse response) {
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#unsubscribeContainer",
      DatastarUtils.MergeTypes.MORPH_ELEMENT.getType(),
      0,
      Partials.subscribeSensorUpdates().render(),
      true
    ));
  }

  private void setupConsumer(String sessionId, HttpServerResponse response) {
    MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer("sensor.updates", msg -> consumeMessage(msg, response));
    consumers.put(sessionId, consumer);
    connections.put(sessionId, response);
    consumer.endHandler(unused -> onEndConsumer(response));
    response.endHandler(unused -> {
      cleanupConsumer(sessionId);
    });
  }

  private void consumeMessage(Message<JsonObject> msg, HttpServerResponse response) {
    if (!response.closed()) {
      String id = msg.body().getString("id");
      String temp = msg.body().getString("temp");
      String deploymentID = msg.body().getString("deploymentID");

      if (!this.concurrentSet.contains(id)) {
        logger.info("SensorID Found: {}", id);
        this.concurrentSet.add(id);
        sendSSE(response, buildConfig(
          UUID.randomUUID().toString(),
          "#" + id,
          DatastarUtils.MergeTypes.DELETE_ELEMENT.getType(),
          0,
          "<div></div>",
          false
        ));
        sendSSE(response, buildConfig(
          UUID.randomUUID().toString(),
          "#sensorUpdatesContainer",
          DatastarUtils.MergeTypes.APPEND_ELEMENT.getType(),
          0,
          Partials.sensorUpdate(deploymentID, id, temp).render(),
          false
        ));
      } else {
        logger.info("SensorID not Found: {}", id);
        sendSSE(response, buildConfig(
          UUID.randomUUID().toString(),
          "#" + id,
          DatastarUtils.MergeTypes.MORPH_ELEMENT.getType(),
          0,
          Partials.sensorUpdate(deploymentID, id, temp).render(),
          false
        ));
      }
    }
  }

  private void onEndConsumer(HttpServerResponse response) {
    logger.info("Closing connection....");
    this.concurrentSet.clear();
  }

  private SSEConfig buildConfig(
    String withId,
    String withSelector,
    String withMergeType,
    Number withSettle,
    String withFragment,
    boolean withEnd
  ) {
    return new SSEConfig.Builder()
      .withId(withId)
      .withSelector(withSelector)
      .withMergeType(withMergeType)
      .withSettle(withSettle)
      .withFragment(withFragment)
      .withEnd(withEnd)
      .build();
  }

  private void cleanupConsumer(String sessionId) {
    MessageConsumer<JsonObject> consumer = consumers.remove(sessionId);
    if (consumer != null && consumer.isRegistered()) {
      consumer.unregister().onComplete(res -> {
        if (res.succeeded()) {
          logger.info("Consumer unregistered successfully for session: {}", sessionId);
        } else {
          logger.error("Failed to unregister consumer for session: {}", sessionId, res.cause());
        }
      });
    }
  }


  private void deployVerticleSelection(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);

    // Read the body asynchronously
    routingContext.request().bodyHandler(body -> {
      // Convert the body to a string (or any other format you need)
      JsonObject jsonBody = body.toJsonObject();
      String selection = jsonBody.getString("verticleSelection");

      String verticleName = HeatSensor.class.getName();
      if (selection.equals("2")) {
        verticleName = HTTPServer.class.getName();
      } else if (selection.equals("3")) {
        verticleName = SensorData.class.getName();
      }

      addVerticleHandler(response, verticleName);
    });
  }

  private void addVerticleHandler(HttpServerResponse response, String verticleName) {
    vertx.deployVerticle(verticleName, res -> {
      if (res.succeeded()) {
        String deploymentID = res.result();
        SharedData sharedData = vertx.sharedData();
        sharedData.<String, String>getAsyncMap("verticleRegistry", mapRes -> {
          if (mapRes.succeeded()) {
            AsyncMap<String, String> verticleRegistry = mapRes.result();
            verticleRegistry.put(deploymentID, verticleName, putRes -> {
              if (putRes.succeeded()) {
                response.setStatusCode(200).end("Verticle added: " + verticleName + " with ID: " + deploymentID);
              } else {
                response.setStatusCode(500).end("Failed to register verticle: " + verticleName);
              }
            });
          } else {
            response.setStatusCode(500).end("Failed to access verticle registry");
          }
        });
      } else {
        response.setStatusCode(500).end("Failed to add verticle: " + verticleName);
      }
    });
  }

}
