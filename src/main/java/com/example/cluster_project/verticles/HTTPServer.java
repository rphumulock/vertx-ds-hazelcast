package com.example.cluster_project.verticles;

import com.example.cluster_project.services.ClusterRegistrationService;
import com.example.cluster_project.ui.partials.Partials;
import io.vertx.core.*;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.ClusteredSessionStore;
import io.vertx.ext.web.sstore.SessionStore;
import io.vertx.serviceproxy.ServiceProxyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.example.cluster_project.ui.partials.Partials.activeHeatSensorTemplate;
import static com.example.cluster_project.utils.DatastarUtils.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class HTTPServer extends AbstractVerticle {

  public static final String TEMPLATE = ""
    + "Session [%s] created on %s%n"
    + "%n"
    + "Page generated on %s%n";

  SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a");

  private static final Logger logger = LoggerFactory.getLogger(HTTPServer.class);

  private ClusterRegistrationService clusterRegistrationService;
  private MessageConsumer<JsonObject> consumer;

  @Override
  public void start(Promise<Void> startPromise) throws Exception {



    ServiceProxyBuilder builder = new ServiceProxyBuilder(vertx).setAddress("cluster.registration.service");
    clusterRegistrationService = builder.setOptions(options).build(ClusterRegistrationService.class);

    JsonObject config = config();
    int port = config.getInteger("http.port", 8080);

    Router router = Router.router(vertx);
    SessionStore store = ClusteredSessionStore.create(vertx);
    router.route().handler(SessionHandler.create(store));
    setupRoutes(router);

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(port)
      .onSuccess(v -> {
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
    if (consumer != null && consumer.isRegistered()) {
      consumer.unregister()
        .onComplete(res -> {
          if (res.succeeded()) {
            logger.info("cluster.HeatSensors unregistered succeeded. CL-[ {} ].", clusterID);
          } else {
            logger.error("cluster.HeatSensors unregistered failed. CL-[{}].", clusterID);
          }
          stopPromise.complete();
        });
    } else {
      stopPromise.complete();
    }
  }

  private void setupRoutes(Router router) {
    router.get("/").handler(this::rootHandler);
    router.post("/heatSensors").handler(this::heatSensorsHandler);
    router.get("/heatSensor/:clusterID/deploy").handler(this::heatSensorDeployHandler);
    router.get("/heatSensor/:clusterID/:deploymentID/undeploy").handler(this::heatSensorUndeployHandler);
    router.get("/heatSensor/:clusterID/:deploymentID/startUpdates").handler(this::heatSensorStartUpdatesHandler);
    router.get("/heatSensor/:clusterID/:deploymentID/stopUpdates").handler(this::heatSensorStopUpdatesHandler);
    router.get("/heatSensor/:clusterID/:deploymentID/subscribe").handler(this::heatSensorSubscribeHandler);
    router.get("/heatSensor/:clusterID/:deploymentID/unsubscribe").handler(this::heatSensorUnsubscribeHandler);
  }

  private void setupHeatSensorDashboard(HttpServerResponse response) {
    clusterRegistrationService.getRegistry()
      .compose(registry -> getActiveSensors().map(activeSensors -> new JsonObject()
        .put("verticles", registry)
        .put("activeSensors", activeSensors))
      )
      .onSuccess(map -> {

        JsonObject verticles = map.getJsonObject("verticles");
        JsonArray activeSensors = map.getJsonArray("activeSensors", new JsonArray());
//        JsonObject mainVerticles = verticles.getJsonObject("registry.com.example.cluster_project.MainVerticle");
//        JsonObject heatSensorVerticles = verticles.getJsonObject("registry.com.example.cluster_project.verticles.HeatSensor");
//
//        if (mainVerticles != null) {
//          for (String clusterId : mainVerticles.fieldNames()) {
//            JsonArray deploymentIds = mainVerticles.getJsonArray(clusterId);
//            for (Object id : deploymentIds) {
//              heatSensorsContainer(response, id.toString());
//            }
//          }
//        }
//
//        if (heatSensorVerticles != null) {
//          for (String clusterId : heatSensorVerticles.fieldNames()) {
//            JsonArray deploymentIds = heatSensorVerticles.getJsonArray(clusterId);
//            for (Object id : deploymentIds) {
//              if (activeSensors.contains(id.toString())) {
//                addActiveHeatSensor(response, clusterId, (String) id);
//              } else {
//                addHeatSensor(response, clusterId, (String) id);
//              }
//            }
//          }
//        }

//        manageHeatSensors(response);
//        heatSensorConsumer(response);
      })
      .onFailure(cause -> {
        logger.error("Failed to retrieve active nodes from cluster.", cause);
      });
  }

  private Future<JsonArray> getActiveSensors() {
    Promise<JsonArray> promise = Promise.promise();
    vertx.sharedData().<String, JsonArray>getAsyncMap("activeSensors")
      .onSuccess(map -> {
        map.get("activeSensors")
          .onSuccess(activeSensors -> {
            if (activeSensors == null) {
              promise.complete(new JsonArray());
            } else {
              promise.complete(activeSensors);
            }
          })
          .onFailure(promise::fail);
      })
      .onFailure(promise::fail);
    return promise.future();
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

  private void heatSensorConsumer(HttpServerResponse response) {
    String clusterID = config().getString("clusterID");
    if (consumer == null) {
      consumer = vertx.eventBus().consumer("cluster.HeatSensors");
      heatSensorConsumerHandlers(response, consumer, clusterID);
      heatSensorResponseHandlers(response, clusterID);
    }
  }

  private void heatSensorConsumerHandlers(HttpServerResponse response, MessageConsumer<JsonObject> consumer, String clusterID) {
    consumer.handler(msg -> {
      JsonObject payload = msg.body();
      String action = payload.getString("action");
      switch (action) {
        case "deploy":
          addHeatSensorPayload(response, payload);
          break;
        case "undeploy":
          removeHeatSensor(response, payload);
          break;
        case "start.updates":
          heatSensorStartUpdates(response, payload);
          break;
        case "stop.updates":
          heatSensorStopUpdates(response, payload);
          break;
        case "consume.updates":
          consumeSensorData(response, payload);
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

//  private void stopVerticles(String clusterID) {
//    clusterRegistrationService.getVerticles(HeatSensor.class.getName(), clusterID)
//      .onSuccess(deploymentIDs -> {
//        for (String id : deploymentIDs) {
//          stopUpdates(clusterID, id);
//        }
//      })
//      .onFailure(cause -> {
//        logger.error("Failed to retrieve heat sensor verticles from cluster.", cause);
//      });
//  }

  private void unregisterConsumers(String clusterID) {
    if (consumer != null && consumer.isRegistered()) {
      consumer.unregister().onComplete(res -> {
        if (res.succeeded()) {
          logger.info("cluster.HeatSensors consumer unregister [{}] succeeded.", clusterID);
        } else {
          logger.info("cluster.HeatSensors consumer unregister [{}] failed.", clusterID);
        }
      });
    }
  }

  private void heatSensorDeployHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    routingContext.request().bodyHandler(body -> {
      Map<String, String> pathParams = routingContext.pathParams();
      String clusterID = pathParams.get("clusterID");

      DeploymentOptions options = new DeploymentOptions().setConfig(config());

      JsonObject payload = new JsonObject()
        .put("action", "deploy")
        .put("clusterID", clusterID)
        .put("deploymentName", HeatSensor.class.getName())
        .put("options", options.toJson());

      clusterRegistrationService.deployVerticle(HeatSensor.class.getName(), options)
        .onSuccess(v -> response.setStatusCode(200)
          .end(
            new JsonObject()
              .put("status", "success")
              .put("heatSensorID", "deploymentID").encode()
          ))
        .onFailure(ar -> response.setStatusCode(500)
          .end(
            new JsonObject()
              .put("status", "failure")
              .put("message", ar.getMessage()).encode()
          ));
    });
  }

  private void heatSensorUndeployHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    routingContext.request().bodyHandler(body -> {
      Map<String, String> pathParams = routingContext.pathParams();
      String clusterID = pathParams.get("clusterID");
      String deploymentID = pathParams.get("deploymentID");

      JsonObject payload = new JsonObject()
        .put("action", "undeploy")
        .put("clusterID", clusterID)
        .put("deploymentID", deploymentID)
        .put("deploymentName", HeatSensor.class.getName());

      clusterRegistrationService.undeployVerticle(deploymentID)
        .onSuccess(v -> response.setStatusCode(200)
          .end(
            new JsonObject()
              .put("status", "success")
              .put("heatSensorID", deploymentID).encode()
          ))
        .onFailure(ar -> response.setStatusCode(500)
          .end(
            new JsonObject()
              .put("status", "failure")
              .put("message", ar.getMessage()).encode()
          ));
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

  private void heatSensorStartUpdatesHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    routingContext.request().bodyHandler(body -> {
      Map<String, String> pathParams = routingContext.pathParams();
      String clusterID = pathParams.get("clusterID");
      String deploymentID = pathParams.get("deploymentID");

      JsonObject payload = new JsonObject()
        .put("action", "start.updates")
        .put("clusterID", clusterID)
        .put("deploymentID", deploymentID);

      vertx.eventBus().request("HeatSensor." + clusterID + "." + deploymentID, payload)
        .onSuccess(ar -> {
          JsonObject result = (JsonObject) ar.body();
          vertx.eventBus().publish("cluster.HeatSensors", payload);
          response
            .setStatusCode(200)
            .end(
              new JsonObject()
                .put("status", "success")
                .put("deploymentID", result.getString("deploymentID"))
                .encode()
            );
        })
        .onFailure(ar -> {
          response
            .setStatusCode(500)
            .end(
              new JsonObject()
                .put("status", "failure")
                .put("message", ar.getMessage())
                .encode()
            );
        });
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
