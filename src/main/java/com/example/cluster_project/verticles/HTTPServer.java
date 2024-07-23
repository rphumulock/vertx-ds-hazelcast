package com.example.cluster_project.verticles;

import com.example.cluster_project.MainVerticle;
import com.example.cluster_project.services.ClusterRegistrationService;
import com.example.cluster_project.utils.MessageWrapper;
import com.example.cluster_project.ui.Partials;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.sstore.ClusteredSessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static com.example.cluster_project.utils.DatastarUtils.*;
import static com.example.cluster_project.utils.DatastarUtils.addHeatSensor;

public class HTTPServer extends AbstractVerticle {

  public static final String TEMPLATE = "Session [%s] created on %s%n"
    + "%n"
    + "Page generated on %s%n";

  private static final Logger logger = LoggerFactory.getLogger(HTTPServer.class);
  private ClusterRegistrationService proxy;
  private final List<MessageConsumer<JsonObject>> consumers = new ArrayList<>();
  private HttpServer httpServer;

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    proxy = ClusterRegistrationService.createProxy(vertx, "cluster.registration");

    Router router = Router.router(vertx);
    ClusteredSessionStore store = ClusteredSessionStore.create(vertx);
    router.route().handler(SessionHandler.create(store));
    router.route("/static/*").handler(StaticHandler.create("webroot"));
    setupRoutes(router);

    httpServer = vertx.createHttpServer()
      .requestHandler(router);

    Integer port = config().getInteger("http.port", 8080);

    httpServer.listen(port)
      .onSuccess(server -> {
        logger.info("HTTP verticle deployed successfully");
        logger.info("Started server successfully on: http://localhost:{}", server.actualPort());
        startPromise.complete();
      })
      .onFailure(t -> {
        logger.error("Failed to start server.", t);
        startPromise.fail(t);
      });
  }

  @Override
  public void stop(Promise<Void> stopPromise) {
    logger.info("Stopped {} {}", config().getString("clusterID"), HttpServer.class.getName());

    // Unregister all consumers
    Future.all(consumers.stream()
        .map(MessageConsumer::unregister)
        .collect(Collectors.toList()))
      .compose(v -> {
        // Close the HTTP server
        if (httpServer != null) {
          return httpServer.close();
        } else {
          return Future.succeededFuture();
        }
      })
      .onComplete(ar -> {
        if (ar.succeeded()) {
          logger.info("All consumers unregistered and HTTP server closed successfully");
          stopPromise.complete();
        } else {
          logger.error("Failed to stop verticle cleanly", ar.cause());
          stopPromise.fail(ar.cause());
        }
      });
  }

  private void setupRoutes(Router router) {
    router.get("/").handler(this::rootHandler);
    router.post("/heatSensors").handler(this::heatSensorsHandler);
    router.post("/heatSensor/:clusterID/deploy").handler(this::heatSensorDeployHandler);
    router.post("/heatSensor/:clusterID/:deploymentID/undeploy").handler(this::heatSensorUndeployHandler);
    router.post("/heatSensor/:clusterID/:deploymentID/startUpdates").handler(this::heatSensorStartUpdatesHandler);
    router.post("/heatSensor/:clusterID/:deploymentID/stopUpdates").handler(this::heatSensorStopUpdatesHandler);
  }

  private void rootHandler(RoutingContext routingContext) {
    Session session = routingContext.session();
    session.computeIfAbsent("createdOn", s -> System.currentTimeMillis());
    logger.debug(String.format(TEMPLATE, session.id(), new Date(session.<Long>get("createdOn")), new Date()));

    HttpServerResponse response = routingContext.response();
    String clusterID = config().getString("clusterID");

    sendHtmlResponse(response, Partials.indexTemplate(clusterID));
  }

  private void heatSensorsHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    routingContext.request().bodyHandler(body -> setupHeatSensorDashboard(response));
  }


  private void setupHeatSensorDashboard(HttpServerResponse response) {
    String clusterID = config().getString("clusterID");

    Future<JsonArray> mainVerticleFuture = proxy.getRegistry(MainVerticle.class.getName());
    Future<JsonArray> activeFuture = proxy.getRegistry("active." + HeatSensor.class.getName());
    Future<JsonArray> heatSensorFuture = proxy.getRegistry(HeatSensor.class.getName());

    Future.all(mainVerticleFuture, activeFuture, heatSensorFuture)
      .onSuccess(composite -> {
        JsonArray mainVerticles = composite.resultAt(0);
        if (mainVerticles != null) {
          mainVerticles.forEach(item -> {
            JsonObject mainVerticleObject = (JsonObject) item;
            String deploymentID = mainVerticleObject.getString("deploymentID");
            heatSensorsContainer(response, deploymentID);
          });
        }

        JsonArray activated = composite.resultAt(1);
        JsonArray heatSensors = composite.resultAt(2);
        if (heatSensors != null) {
          heatSensors.forEach(item -> {
            JsonObject heatSensorsObject = (JsonObject) item;
            String sensorClusterID = heatSensorsObject.getString("clusterID");
            String deploymentID = heatSensorsObject.getString("deploymentID");
            addHeatSensor(response, sensorClusterID, deploymentID, activated.contains(deploymentID));
          });
        }

        manageHeatSensors(response);
        MessageConsumer<JsonObject> consumer = heatSensorConsumer(response, clusterID);
        heatSensorResponseHandlers(response, clusterID, consumer);

        logger.info("MainVerticle: {}, HeatSensor: {}", mainVerticles, heatSensors);
      })
      .onFailure(cause -> {
        logger.error("Failed to retrieve active nodes from cluster.", cause);
        response.setStatusCode(500).end(new JsonObject().put("error", "Failed to retrieve active nodes from cluster.").encode());
      });
  }

  private MessageConsumer<JsonObject> heatSensorConsumer(HttpServerResponse response, String clusterID) {
    MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer("cluster.HeatSensors");

    consumer.handler(msg -> {
      String action = msg.headers().get("action");
      JsonObject message = msg.body();
      switch (action) {
        case "add":
          addHeatSensorPayload(response, message);
          break;
        case "remove":
          removeHeatSensor(response, message);
          break;
        case "startUpdates":
          heatSensorStartUpdates(response, message);
          break;
        case "stopUpdates":
          heatSensorStopUpdates(response, message);
          break;
        case "consumeUpdates":
          consumeSensorData(response, message);
          break;
      }
    });

    consumer.completionHandler(res -> {
      if (res.succeeded()) {
        logger.info("cluster.HeatSensors consumer completionHandler [{}] succeeded.", clusterID);
      } else {
        logger.info("cluster.HeatSensors consumer completionHandler [{}] failed.", clusterID);
      }
    });

    consumer.exceptionHandler(res -> logger.info("cluster.HeatSensors consumer exceptionHandler [{}].", clusterID));

    consumer.endHandler(res -> logger.info("cluster.HeatSensors consumer endHandler [{}].", clusterID));

    consumers.add(consumer);

    return consumer;
  }

  private void heatSensorResponseHandlers(HttpServerResponse response, String clusterID, MessageConsumer<JsonObject> consumer) {
    response.closeHandler(v -> logger.info("heatSensorsHandler response closeHandler [{}].", clusterID));

    response.endHandler(unused -> {
      logger.info("heatSensorsHandler response endHandler [{}].", clusterID);
      if (consumer != null && consumer.isRegistered()) {
        consumer.unregister().onComplete(res -> {
          if (res.succeeded()) {
            logger.info("cluster.HeatSensors consumer unregister [{}] succeeded.", clusterID);
          } else {
            logger.info("cluster.HeatSensors consumer unregister [{}] failed.", clusterID);
          }
        });
      }
    });
  }

  private void heatSensorDeployHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    routingContext.request().bodyHandler(body -> {
      Map<String, String> pathParams = routingContext.pathParams();
      String clusterID = pathParams.get("clusterID");

      JsonObject config = new JsonObject().put("clusterID", clusterID);
      JsonObject message = new JsonObject()
        .put("clusterID", clusterID)
        .put("deploymentName", HeatSensor.class.getName());

      MessageWrapper wrapper = new MessageWrapper(message, new DeploymentOptions().setConfig(config));
      Buffer buffer = Buffer.buffer();
      wrapper.writeToBuffer(buffer);

      DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("action", "deploy");

      vertx.eventBus().request("deploy-heat-sensor." + clusterID, buffer, deliveryOptions)
        .onSuccess(reply -> {
          JsonObject replyBody = (JsonObject) reply.body();

          String deploymentID = replyBody.getString("deploymentID");
          JsonObject updateMessage = new JsonObject()
            .put("clusterID", clusterID)
            .put("deploymentID", deploymentID);
          DeliveryOptions updateOptions = new DeliveryOptions().addHeader("action", "add");

          vertx.eventBus().publish("cluster.HeatSensors", updateMessage, updateOptions);

          response.setStatusCode(200).end(replyBody.encode());
        })
        .onFailure(err -> {
          logger.info("{}", err.getMessage());
          response.setStatusCode(500).end(new JsonObject()
            .put("status", "failure")
            .put("message", err.getMessage()).encode());
        });
    });
  }

  private void heatSensorUndeployHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    routingContext.request().bodyHandler(body -> {
      Map<String, String> pathParams = routingContext.pathParams();
      String clusterID = pathParams.get("clusterID");
      String deploymentID = pathParams.get("deploymentID");

      JsonObject message = new JsonObject()
        .put("deploymentName", HeatSensor.class.getName())
        .put("deploymentID", deploymentID);

      DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("action", "undeploy");

      vertx.eventBus().request("undeploy-heat-sensor." + clusterID, message, deliveryOptions)
        .onSuccess(reply -> {

          JsonObject replyBody = (JsonObject) reply.body();

          String undeploymentID = replyBody.getString("undeploymentID");
          JsonObject updateMessage = new JsonObject()
            .put("clusterID", clusterID)
            .put("deploymentID", undeploymentID);

          DeliveryOptions updateOptions = new DeliveryOptions().addHeader("action", "remove");

          vertx.eventBus().publish("cluster.HeatSensors", updateMessage, updateOptions);

          response.setStatusCode(200).end(replyBody.encode());
        })
        .onFailure(err -> {
          logger.info("{}", err.getMessage());
          response.setStatusCode(500).end(new JsonObject()
            .put("status", "failure")
            .put("message", err.getMessage()).encode());
        });
    });
  }

  private void heatSensorStartUpdatesHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    routingContext.request().bodyHandler(body -> {
      Map<String, String> pathParams = routingContext.pathParams();
      String clusterID = pathParams.get("clusterID");
      String deploymentID = pathParams.get("deploymentID");

      JsonObject message = new JsonObject()
        .put("clusterID", clusterID)
        .put("deploymentID", deploymentID);

      DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("action", "startUpdates");

      vertx.eventBus().request("HeatSensor." + clusterID + "." + deploymentID, message, deliveryOptions)
        .compose(reply -> {
          JsonObject replyBody = (JsonObject) reply.body();
          vertx.eventBus().publish("cluster.HeatSensors", message, deliveryOptions);
          return Future.succeededFuture(replyBody);
        })
        .onSuccess(replyBody -> response.setStatusCode(200)
          .end(new JsonObject()
            .put("status", "success")
            .put("deploymentID", replyBody.getString("deploymentID"))
            .encode())
        )
        .onFailure(err -> response.setStatusCode(500)
          .end(new JsonObject()
            .put("status", "failure")
            .put("message", err.getMessage())
            .encode())
        );
    });
  }

  private void heatSensorStopUpdatesHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    routingContext.request().bodyHandler(body -> {
      Map<String, String> pathParams = routingContext.pathParams();
      String clusterID = pathParams.get("clusterID");
      String deploymentID = pathParams.get("deploymentID");

      JsonObject message = new JsonObject()
        .put("clusterID", clusterID)
        .put("deploymentID", deploymentID);

      DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("action", "stopUpdates");

      vertx.eventBus().request("HeatSensor." + clusterID + "." + deploymentID, message, deliveryOptions)
        .compose(reply -> {
          JsonObject replyBody = (JsonObject) reply.body();
          vertx.eventBus().publish("cluster.HeatSensors", message, deliveryOptions);
          return Future.succeededFuture(replyBody);
        })
        .onSuccess(replyBody -> response.setStatusCode(200)
          .end(new JsonObject()
            .put("status", "success")
            .put("deploymentID", replyBody.getString("deploymentID"))
            .encode())
        )
        .onFailure(err ->
          response.setStatusCode(500)
            .end(new JsonObject()
              .put("status", "failure")
              .put("message", err.getMessage())
              .encode())
        );
    });
  }

}
