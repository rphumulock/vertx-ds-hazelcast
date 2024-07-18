package com.example.cluster_project.verticles;

import com.example.cluster_project.MainVerticle;
import com.example.cluster_project.services.ClusterRegistrationService;
import com.example.cluster_project.ui.Partials;
import io.vertx.core.*;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.ClusteredSessionStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.example.cluster_project.utils.DatastarUtils.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

public class HTTPServer extends AbstractVerticle {

  public static final String TEMPLATE = ""
    + "Session [%s] created on %s%n"
    + "%n"
    + "Page generated on %s%n";

  SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a");

  private static final Logger logger = LoggerFactory.getLogger(HTTPServer.class);
  private ClusterRegistrationService proxy;

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    JsonObject config = config();
    int port = config.getInteger("http.port", 8080);

    proxy = ClusterRegistrationService.createProxy(vertx, "cluster.registration");

    Router router = Router.router(vertx);
    ClusteredSessionStore store = ClusteredSessionStore.create(vertx);
    router.route().handler(SessionHandler.create(store));
    setupRoutes(router);

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(port)
      .onSuccess(v -> {
        logger.info("HTTP verticle deployed successfully");
        logger.info("Started server successfully on: http://localhost:{}", port);
        startPromise.complete();
      })
      .onFailure(t -> {
        logger.error("Failed to start server.", t);
        startPromise.fail(t);
      });
  }

  @Override
  public void stop(Promise<Void> stopPromise) {
    JsonObject config = config();
    String clusterID = config.getString("clusterID");
  }

  private void setupRoutes(Router router) {
    router.get("/").handler(this::rootHandler);
    router.post("/heatSensors").handler(this::heatSensorsHandler);
    router.post("/heatSensor/:clusterID/deploy").handler(this::heatSensorDeployHandler);
    router.post("/heatSensor/:clusterID/:deploymentID/undeploy").handler(this::heatSensorUndeployHandler);
    router.get("/heatSensor/:clusterID/:deploymentID/startUpdates").handler(this::heatSensorStartUpdatesHandler);
//    router.get("/heatSensor/:clusterID/:deploymentID/stopUpdates").handler(this::heatSensorStopUpdatesHandler);
    router.get("/heatSensor/:clusterID/:deploymentID/subscribe").handler(this::heatSensorSubscribeHandler);
//    router.get("/heatSensor/:clusterID/:deploymentID/unsubscribe").handler(this::heatSensorUnsubscribeHandler);
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
            if (activated.contains(deploymentID)) {
              addActiveHeatSensor(response, sensorClusterID, deploymentID);
            } else {
              addHeatSensor(response, sensorClusterID, deploymentID);
            }
          });
        }

        manageHeatSensors(response);
        heatSensorConsumer(response, clusterID);
        heatSensorResponseHandlers(response, clusterID);

        logger.info("MainVerticle: {}, HeatSensor: {}", mainVerticles, heatSensors);
      })
      .onFailure(cause -> {
        logger.error("Failed to retrieve active nodes from cluster.", cause);
        response.setStatusCode(500).end(new JsonObject().put("error", "Failed to retrieve active nodes from cluster.").encode());
      });
  }

  private void heatSensorConsumer(HttpServerResponse response, String clusterID) {
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
        case "stop.updates":
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

    consumer.exceptionHandler(res -> {
      logger.info("cluster.HeatSensors consumer exceptionHandler [{}].", clusterID);
    });

    consumer.endHandler(res -> {
      logger.info("cluster.HeatSensors consumer endHandler [{}].", clusterID);
    });
  }

  private void heatSensorResponseHandlers(HttpServerResponse response, String clusterID) {
    response.closeHandler(v -> {
      logger.info("heatSensorsHandler response closeHandler [{}].", clusterID);
    });

    response.endHandler(unused -> {
      logger.info("heatSensorsHandler response endHandler [{}].", clusterID);
    });
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
    routingContext.request().bodyHandler(body -> {
      setupHeatSensorDashboard(response);
    });
  }

//  private void unregisterConsumers(String clusterID) {
//    if (consumer != null && consumer.isRegistered()) {
//      consumer.unregister().onComplete(res -> {
//        if (res.succeeded()) {
//          logger.info("cluster.HeatSensors consumer unregister [{}] succeeded.", clusterID);
//        } else {
//          logger.info("cluster.HeatSensors consumer unregister [{}] failed.", clusterID);
//        }
//      });
//    }
//  }

  private void heatSensorDeployHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    routingContext.request().bodyHandler(body -> {
      Map<String, String> pathParams = routingContext.pathParams();
      String clusterID = pathParams.get("clusterID");

      JsonObject message = new JsonObject()
        .put("deploymentName", HeatSensor.class.getName())
        .put("options", new DeploymentOptions().setConfig(config()).toJson());

      DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("action", "deploy");

      vertx.eventBus().request("deploy-heat-sensor." + clusterID, message, deliveryOptions)
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
          String startedID = replyBody.getString("deploymentID");




              vertx.eventBus().publish("cluster.HeatSensors", message, deliveryOptions);
              return Future.succeededFuture(replyBody);

        })
        .onSuccess(replyBody -> {
          response.setStatusCode(200)
            .end(new JsonObject()
              .put("status", "success")
              .put("deploymentID", replyBody.getString("deploymentID"))
              .encode());
        })
        .onFailure(err -> {
          response.setStatusCode(500)
            .end(new JsonObject()
              .put("status", "failure")
              .put("message", err.getMessage())
              .encode());
        });
    });
  }


  private void heatSensorSubscribeHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    routingContext.request().bodyHandler(body -> {
      Map<String, String> pathParams = routingContext.pathParams();
      String clusterID = pathParams.get("clusterID");
      String deploymentID = pathParams.get("deploymentID");
      heatSensorSubscribe(response, clusterID, deploymentID);
      response.end();
    });
  }

  private void heatSensorUnsubscribeHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    String clusterID = config().getString("clusterID");
    routingContext.request().bodyHandler(body -> {
      Map<String, String> pathParams = routingContext.pathParams();
      String deploymentID = pathParams.get("deploymentID");
      heatSensorUnsubscribe(response, clusterID, deploymentID);
      response.end();
    });
  }

  private void heatSensorStopUpdatesHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    routingContext.request().bodyHandler(body -> {
      Map<String, String> pathParams = routingContext.pathParams();
      String clusterID = pathParams.get("clusterID");
      String deploymentID = pathParams.get("deploymentID");

      stopUpdates(clusterID, deploymentID)
        .onSuccess(result -> {
          response
            .setStatusCode(200)
            .end(
              new JsonObject()
                .put("status", "success")
                .put("deploymentID", result.getString("deploymentID"))
                .encode()
            );
        })
        .onFailure(cause -> {
          response
            .setStatusCode(500)
            .end(
              new JsonObject()
                .put("status", "failure")
                .put("message", cause.getMessage())
                .encode()
            );
        });
    });
  }

  private Future<JsonObject> stopUpdates(String clusterID, String deploymentID) {
    Promise<JsonObject> promise = Promise.promise();

    JsonObject payload = new JsonObject()
      .put("action", "stop.updates")
      .put("clusterID", clusterID)
      .put("deploymentID", deploymentID);

    vertx.eventBus().request("HeatSensor." + clusterID + "." + deploymentID, payload)
      .onSuccess(ar -> {
        JsonObject result = (JsonObject) ar.body();
        vertx.eventBus().publish("cluster.HeatSensors", payload);
        promise.complete(result);
      })
      .onFailure(promise::fail);

    return promise.future();
  }

}
