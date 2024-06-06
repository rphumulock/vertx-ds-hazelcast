package com.example.cluster_project.verticles;

import com.example.cluster_project.services.ConsumerManagerService;
import com.example.cluster_project.ui.templates.Index;
import com.example.cluster_project.ui.partials.Partials;
import com.example.cluster_project.utils.DatastarUtils;
import com.example.cluster_project.utils.SSEConfig;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.ClusteredSessionStore;
import io.vertx.ext.web.sstore.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.UUID;

import static com.example.cluster_project.utils.DatastarUtils.sendSSE;
import static com.example.cluster_project.utils.DatastarUtils.setHeaders;

public class HTTPServer extends AbstractVerticle {

  public static final String TEMPLATE = "Session [%s] created on %s%n"
    + "%n"
    + "Page generated on %s%n";

  private static final Logger logger = LoggerFactory.getLogger(HTTPServer.class);

  private ConsumerManagerService consumerManagerService;

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    consumerManagerService = new ConsumerManagerService(vertx);

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
      .onSuccess(v -> {
        startPromise.complete();
        logger.info("HTTP server started successfully on: http://localhost:" + port);
      })
      .onFailure(startPromise::fail);
  }

  private void setupRoutes(Router router) {
    router.get("/").handler(this::rootHandler);
    router.get("/subscribeSensorUpdates").handler(this::subscribeSensorUpdates);
    router.get("/unsubscribeSensorUpdates").handler(this::unsubscribeSensorUpdates);
  }

  private void rootHandler(RoutingContext ctx) {
    Session session = ctx.session();
    session.computeIfAbsent("createdOn", s -> System.currentTimeMillis());
    String sessionId = session.id();
    Date createdOn = new Date(session.<Long>get("createdOn"));
    Date now = new Date();
    logger.info(String.format(TEMPLATE, sessionId, createdOn, now));
    DatastarUtils.sendHtmlResponse(ctx.response(), Index.getIndex());
  }

  private void subscribeSensorUpdates(RoutingContext ctx) {
    HttpServerResponse response = setHeaders(ctx.response());
    String sessionId = ctx.session().id();
    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#subscribeContainer",
      DatastarUtils.MergeTypes.MORPH_ELEMENT.getType(),
      0,
      Partials.unsubscribeSensorUpdates().render(),
      false
    ));

    // Setup consumer
    consumerManagerService.setupConsumer(sessionId);

    // Listen for responses from the consumer manager service
    vertx.eventBus().consumer("consumer.response." + sessionId, msg -> {
      JsonObject body = (JsonObject) msg.body();
      // Handle the message (e.g., send it via SSE to the client)
      sendSSE(response, buildConfig(
        UUID.randomUUID().toString(),
        "#sensorUpdatesContainer",
        DatastarUtils.MergeTypes.APPEND_ELEMENT.getType(),
        0,
        Partials.sensorUpdate(body.getString("id"), body.getString("temp")).render(),
        false
      ));
    });
  }

  private void unsubscribeSensorUpdates(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    String sessionId = routingContext.session().id();

    // Cleanup consumer
    consumerManagerService.cleanupConsumer(sessionId);

    sendSSE(response, buildConfig(
      UUID.randomUUID().toString(),
      "#unsubscribeContainer",
      DatastarUtils.MergeTypes.MORPH_ELEMENT.getType(),
      0,
      Partials.subscribeSensorUpdates().render(),
      true
    ));
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
}


//package com.example.cluster_project.verticles;
//
//import com.example.cluster_project.services.ConsumerService;
//import com.example.cluster_project.ui.templates.Index;
//import com.example.cluster_project.ui.partials.Partials;
//import com.example.cluster_project.utils.DatastarUtils;
//import com.example.cluster_project.utils.SSEConfig;
//import io.vertx.core.AbstractVerticle;
//import io.vertx.core.http.HttpServerResponse;
//import io.vertx.core.json.JsonObject;
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
//  public static final String TEMPLATE = ""
//    + "Session [%s] created on %s%n"
//    + "%n"
//    + "Page generated on %s%n";
//
//  private static final Logger logger = LoggerFactory.getLogger(HTTPServer.class);
//
//  private ConsumerService consumerService;
//
//  @Override
//  public void start(Promise<Void> startPromise) throws Exception {
//    consumerService = new ConsumerService(vertx);
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
//    logger.info(String.format(TEMPLATE, sessionId, createdOn, now));
//    DatastarUtils.sendHtmlResponse(ctx.response(), Index.getIndex());
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
//    consumerService.setupConsumer(sessionId, response);
//  }
//
//  private void unsubscribeSensorUpdates(RoutingContext routingContext) {
//    HttpServerResponse response = routingContext.response();
//    setHeaders(response);
//    String sessionId = routingContext.session().id();
//    consumerService.cleanupConsumer(sessionId);
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
//
////package com.example.cluster_project.verticles;
////
////import com.example.cluster_project.ui.templates.Index;
////import com.example.cluster_project.ui.partials.Partials;
////import com.example.cluster_project.utils.DatastarUtils;
////import com.example.cluster_project.utils.SSEConfig;
////
////import io.vertx.core.AbstractVerticle;
////import io.vertx.core.eventbus.Message;
////import io.vertx.core.eventbus.MessageConsumer;
////import io.vertx.core.http.HttpServerResponse;
////import io.vertx.core.json.JsonObject;
////
////import io.vertx.ext.web.Router;
////import io.vertx.ext.web.RoutingContext;
////
////import java.util.*;
////import java.util.concurrent.ConcurrentHashMap;
////
////import static com.example.cluster_project.utils.DatastarUtils.sendSSE;
////import static com.example.cluster_project.utils.DatastarUtils.setHeaders;
////
////import io.vertx.ext.web.Session;
////import io.vertx.ext.web.handler.SessionHandler;
////import io.vertx.ext.web.sstore.ClusteredSessionStore;
////import io.vertx.ext.web.sstore.SessionStore;
////
////import org.slf4j.Logger;
////import org.slf4j.LoggerFactory;
////
////public class HTTPServer extends AbstractVerticle {
////
////  public static final String TEMPLATE = ""
////    + "Session [%s] created on %s%n"
////    + "%n"
////    + "Page generated on %s%n";
////
////  private static final Logger logger = LoggerFactory.getLogger(HTTPServer.class);
////
////  private final Map<String, MessageConsumer<JsonObject>> consumers = new ConcurrentHashMap<>();
////  private final Map<String, HttpServerResponse> connections = new ConcurrentHashMap<>();
////  Set<String> concurrentSet = Collections.newSetFromMap(new ConcurrentHashMap<>());
////
////  @Override
////  public void start() throws Exception {
////    Router router = Router.router(vertx);
////    JsonObject config = config();
////    int port = config.getInteger("http.port", 8080);
////
////    // Set up the clustered session store
////    SessionStore store = ClusteredSessionStore.create(vertx);
////    router.route().handler(SessionHandler.create(store));
////
////    setupRoutes(router);
////    vertx.createHttpServer()
////      .requestHandler(router)
////      .listen(port)
////      .onSuccess(v -> logger.info("Starter server successfully on: http://localhost:{}", port))
////      .onFailure(t -> logger.error("Failed to unregister consumer.", t));
////  }
////
////  private void setupRoutes(Router router) {
////    router.get("/").handler(this::rootHandler);
////    router.get("/subscribeSensorUpdates").handler(this::subscribeSensorUpdates);
////    router.get("/unsubscribeSensorUpdates").handler(this::unsubscribeSensorUpdates);
////  }
////
////  private void rootHandler(RoutingContext ctx) {
////    Session session = ctx.session();
////    session.computeIfAbsent("createdOn", s -> System.currentTimeMillis()); // (3)
////    String sessionId = session.id();
////    Date createdOn = new Date(session.<Long>get("createdOn"));
////    Date now = new Date();
////    logger.info(String.format(TEMPLATE, sessionId, createdOn, now)); // (4)
////    DatastarUtils.sendHtmlResponse(ctx.response(), Index.getIndex());
////  }
////
////  private void subscribeSensorUpdates(RoutingContext ctx) {
////    HttpServerResponse response = setHeaders(ctx.response());
////    String sessionId = ctx.session().id();
////    sendSSE(response, buildConfig(
////      UUID.randomUUID().toString(),
////      "#subscribeContainer",
////      DatastarUtils.MergeTypes.MORPH_ELEMENT.getType(),
////      0,
////      Partials.unsubscribeSensorUpdates().render(),
////      false
////    ));
////    setupConsumer(sessionId, response);
////  }
////
////  private void unsubscribeSensorUpdates(RoutingContext routingContext) {
////    HttpServerResponse response = routingContext.response();
////    setHeaders(response);
////    String sessionId = routingContext.session().id();
////    this.concurrentSet.clear();
////
////    HttpServerResponse consumerConnection = this.connections.get(sessionId);
////    consumerConnection.end();
////
////    response.endHandler((h) -> {
////      cleanupConsumer(sessionId);
////    });
////
////    sendSSE(response, buildConfig(
////      UUID.randomUUID().toString(),
////      "#unsubscribeContainer",
////      DatastarUtils.MergeTypes.MORPH_ELEMENT.getType(),
////      0,
////      Partials.subscribeSensorUpdates().render(),
////      true
////    ));
////  }
////
////  private void setupConsumer(String sessionId, HttpServerResponse response) {
////    MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer("sensor.updates", msg -> consumeMessage(msg, sessionId, response));
////    consumers.put(sessionId, consumer);
////    connections.put(sessionId, response);
////    consumer.endHandler((Void v) -> onEndConsumer(response));
////    response.endHandler((Void v) -> {
////      cleanupConsumer(sessionId);
////    });
////  }
////
////  private void consumeMessage(Message<JsonObject> msg, String sessionId, HttpServerResponse response) {
////    if (!response.closed()) {
////      String id = msg.body().getString("id");
////      String temp = msg.body().getString("temp");
////
////      if (!this.concurrentSet.contains(id)) {
////        this.concurrentSet.add(id);
////        sendSSE(response, buildConfig(
////          UUID.randomUUID().toString(),
////          "#" + id,
////          DatastarUtils.MergeTypes.DELETE_ELEMENT.getType(),
////          0,
////          "<div></div>",
////          false
////        ));
////        sendSSE(response, buildConfig(
////          UUID.randomUUID().toString(),
////          "#sensorUpdatesContainer",
////          DatastarUtils.MergeTypes.APPEND_ELEMENT.getType(),
////          0,
////          Partials.sensorUpdate(id, temp).render(),
////          false
////        ));
////      } else {
////        sendSSE(response, buildConfig(
////          UUID.randomUUID().toString(),
////          "#" + id,
////          DatastarUtils.MergeTypes.MORPH_ELEMENT.getType(),
////          0,
////          Partials.sensorUpdate(id, temp).render(),
////          false
////        ));
////      }
////    }
////  }
////
////  private void onEndConsumer(HttpServerResponse response) {
////    logger.info("Closing connection....");
////    this.concurrentSet.clear();
////  }
////
////  private SSEConfig buildConfig(
////    String withId,
////    String withSelector,
////    String withMergeType,
////    Number withSettle,
////    String withFragment,
////    boolean withEnd
////  ) {
////    return new SSEConfig.Builder()
////      .withId(withId)
////      .withSelector(withSelector)
////      .withMergeType(withMergeType)
////      .withSettle(withSettle)
////      .withFragment(withFragment)
////      .withEnd(withEnd)
////      .build();
////  }
////
////  private void cleanupConsumer(String sessionId) {
////    MessageConsumer<JsonObject> consumer = consumers.remove(sessionId);
////    if (consumer != null && consumer.isRegistered()) {
////      consumer.unregister().onComplete(res -> {
////        if (res.succeeded()) {
////          logger.info("Consumer unregistered successfully for session: {}", sessionId);
////        } else {
////          logger.error("Failed to unregister consumer for session: {}", sessionId, res.cause());
////        }
////      });
////    }
////  }
////
////}
