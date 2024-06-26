package com.example.cluster_project.verticles;

import com.example.cluster_project.MainVerticle;
import com.example.cluster_project.ui.partials.Partials;

import io.vertx.core.*;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.*;

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

  private MessageConsumer<JsonObject> consumer;

  @Override
  public void start() throws Exception {
    JsonObject config = config();
    int port = config.getInteger("http.port", 8080);

    Router router = Router.router(vertx);
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
  public void stop(Promise<Void> stopPromise) {
    JsonObject config = config();
    String clusterNodeID = config.getString("clusterNodeID");
    if (this.consumer.isRegistered()) {
      this.consumer.unregister()
        .onComplete(res -> {
          if (res.succeeded()) {
            logger.info("cluster.HeatSensors unregistered succeeded. CL-[ {} ].", clusterNodeID);
          } else {
            logger.error("cluster.HeatSensors unregistered failed. CL-[{}].", clusterNodeID);
          }
        });
    }
  }

  /*****************************************************************************************
   *  SETUP
   *****************************************************************************************/

  private void setupRoutes(Router router) {
    router.get("/").handler(this::rootHandler);
    router.post("/heatSensors").handler(this::heatSensorsHandler);
    router.get("/heatSensor/:clusterNodeID/deploy").handler(this::heatSensorDeployHandler);
    router.get("/heatSensor/:clusterNodeID/:deploymentID/undeploy").handler(this::heatSensorUndeployHandler);
    router.get("/heatSensor/:clusterNodeID/:deploymentID/startUpdates").handler(this::heatSensorStartUpdatesHandler);
    router.get("/heatSensor/:clusterNodeID/:deploymentID/stopUpdates").handler(this::heatSensorStopUpdatesHandler);
    router.get("/heatSensor/:clusterNodeID/:deploymentID/subscribe").handler(this::heatSensorSubscribeHandler);
    router.get("/heatSensor/:clusterNodeID/:deploymentID/unsubscribe").handler(this::heatSensorUnsubscribeHandler);
  }

  private void setupHeatSensorDashboard(HttpServerResponse response) {
    getRegisteredVerticles(MainVerticle.class.getName())
      .onSuccess(clusterNodes -> {
        clusterNodes.forEach(clusterNodeID -> {
          heatSensorsContainer(response, clusterNodeID);
        });
        manageHeatSensors(response);
        heatSensorConsumer(response);
      })
      .onFailure(cause -> {
        logger.error("Failed to retrieve active nodes from cluster.", cause);
      });
  }

  private Future<List<String>> getRegisteredVerticles(String verticleName) {
    Promise<List<String>> promise = Promise.promise();
    vertx.eventBus().request("cluster.registration", new JsonObject()
      .put("action", "getRegisteredVerticles")
      .put("deploymentName", verticleName), ar -> {
      if (ar.succeeded()) {
        JsonObject result = (JsonObject) ar.result().body();
        List<String> deploymentIDs = result.getJsonArray("deploymentIDs").getList();
        promise.complete(deploymentIDs);
      } else {
        promise.fail(ar.cause());
      }
    });
    return promise.future();
  }

  /*****************************************************************************************
   *  ROOT
   *****************************************************************************************/

  private void rootHandler(RoutingContext routingContext) {
    Session session = routingContext.session();
    session.computeIfAbsent("createdOn", s -> System.currentTimeMillis()); // (3)
    logger.debug(String.format(TEMPLATE, session.id(), new Date(session.<Long>get("createdOn")), new Date()));

    HttpServerResponse response = routingContext.response();
    String clusterNodeID = config().getString("clusterNodeID");

    sendHtmlResponse(response, Partials.indexTemplate(clusterNodeID));
  }

  private void heatSensorsHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    routingContext.request().bodyHandler(body -> {
      setupHeatSensorDashboard(response);
    });
  }

  private void heatSensorConsumer(HttpServerResponse response) {
    String clusterNodeID = config().getString("clusterNodeID");
    MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer("cluster.HeatSensors");
    heatSensorConsumerHandlers(response, consumer, clusterNodeID);
    heatSensorResponseHandlers(response, clusterNodeID);
    this.consumer = consumer;
  }

  private void heatSensorConsumerHandlers(HttpServerResponse response, MessageConsumer<JsonObject> consumer, String clusterNodeID) {
    consumer.handler(msg -> {
      JsonObject payload = msg.body();
      String action = payload.getString("action");
      switch (action) {
        case "deploy":
          addHeatSensor(response, payload);
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
        logger.info("cluster.HeatSensors completionHandler [{}] succeeded.", clusterNodeID);
      } else {
        logger.info("cluster.HeatSensors completionHandler [{}] failed.", clusterNodeID);
      }
    });

    consumer.exceptionHandler(res -> {
      logger.info("cluster.HeatSensors exceptionHandler [{}].", clusterNodeID);
    });

    consumer.endHandler(res -> {
      logger.info("cluster.HeatSensors endHandler [{}].", clusterNodeID);
    });
  }

  private void heatSensorResponseHandlers(HttpServerResponse response, String clusterNodeID) {
    response.closeHandler(v -> {
      logger.info("heatSensorsHandler closeHandler [{}].", clusterNodeID);
    });

    response.endHandler(unused -> {
      logger.info("heatSensorsHandler endHandler [{}].", clusterNodeID);
      if (consumer.isRegistered()) {
        consumer.unregister().onComplete(res -> {
          if (res.succeeded()) {
            logger.info("cluster.HeatSensors unregister [{}] succeeded.", clusterNodeID);
          } else {
            logger.info("cluster.HeatSensors unregister [{}] failed.", clusterNodeID);
          }
        });
      }
    });
  }

  /*****************************************************************************************
   *  DEPLOY
   *****************************************************************************************/

  private void heatSensorDeployHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    routingContext.request().bodyHandler(body -> {
      Map<String, String> pathParams = routingContext.pathParams();
      String clusterNodeID = pathParams.get("clusterNodeID");

      DeploymentOptions options = new DeploymentOptions().setConfig(config());

      JsonObject payload = new JsonObject()
        .put("action", "deploy")
        .put("clusterNodeID", clusterNodeID)
        .put("deploymentName", HeatSensor.class.getName())
        .put("options", options.toJson());

      vertx.eventBus().request("cluster.registration", payload)
        .onSuccess(ar -> {
          JsonObject result = (JsonObject) ar.body();
          String deploymentID = result.getString("deploymentID");
          payload.put("deploymentID", deploymentID);
          vertx.eventBus().publish("cluster.HeatSensors", payload);
          response.setStatusCode(200)
            .end(
              new JsonObject()
                .put("status", "success")
                .put("heatSensorID", deploymentID).encode()
            );
        })
        .onFailure(ar -> {
          response.setStatusCode(500)
            .end(
              new JsonObject()
                .put("status", "failure")
                .put("message", ar.getMessage()).encode()
            );
        });
    });
  }

  private void heatSensorUndeployHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    routingContext.request().bodyHandler(body -> {
      Map<String, String> pathParams = routingContext.pathParams();
      String clusterNodeID = pathParams.get("clusterNodeID");
      String deploymentID = pathParams.get("deploymentID");

      DeploymentOptions options = new DeploymentOptions().setConfig(config());

      JsonObject payload = new JsonObject()
        .put("action", "undeploy")
        .put("clusterNodeID", clusterNodeID)
        .put("deploymentID", deploymentID)
        .put("deploymentName", HeatSensor.class.getName());

      vertx.eventBus().request("cluster.registration", payload)
        .onSuccess(ar -> {
          JsonObject result = (JsonObject) ar.body();
//          String deploymentID = result.getString("deploymentID");
          payload.put("deploymentID", deploymentID);
          vertx.eventBus().publish("cluster.HeatSensors", payload);
          response.setStatusCode(200)
            .end(
              new JsonObject()
                .put("status", "success")
                .put("heatSensorID", deploymentID).encode()
            );
        })
        .onFailure(ar -> {
          response.setStatusCode(500)
            .end(
              new JsonObject()
                .put("status", "failure")
                .put("message", ar.getMessage()).encode()
            );
        });
    });
  }


  /*****************************************************************************************
   *  SUBSCRIBE
   *****************************************************************************************/

  private void heatSensorSubscribeHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    routingContext.request().bodyHandler(body -> {
      Map<String, String> pathParams = routingContext.pathParams();
      String clusterNodeID = pathParams.get("clusterNodeID");
      String deploymentID = pathParams.get("deploymentID");
      heatSensorSubscribe(response, clusterNodeID, deploymentID);
      response.end();
    });
  }

  private void heatSensorUnsubscribeHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    String clusterNodeID = config().getString("clusterNodeID");
    routingContext.request().bodyHandler(body -> {
      Map<String, String> pathParams = routingContext.pathParams();
      String deploymentID = pathParams.get("deploymentID");
      heatSensorUnsubscribe(response, clusterNodeID, deploymentID);
      response.end();
    });
  }

  /*****************************************************************************************
   *  UPDATES
   *****************************************************************************************/

  private void heatSensorStartUpdatesHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    setHeaders(response);
    routingContext.request().bodyHandler(body -> {
      Map<String, String> pathParams = routingContext.pathParams();
      String clusterNodeID = pathParams.get("clusterNodeID");
      String deploymentID = pathParams.get("deploymentID");

      JsonObject payload = new JsonObject()
        .put("action", "start.updates")
        .put("clusterNodeID", clusterNodeID)
        .put("deploymentID", deploymentID);

      vertx.eventBus().request("HeatSensor." + clusterNodeID + "." + deploymentID, payload)
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
      String clusterNodeID = pathParams.get("clusterNodeID");
      String deploymentID = pathParams.get("deploymentID");

      JsonObject payload = new JsonObject()
        .put("action", "stop.updates")
        .put("clusterNodeID", clusterNodeID)
        .put("deploymentID", deploymentID);

      vertx.eventBus().request("HeatSensor." + clusterNodeID + "." + deploymentID, payload)
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

//  private void heatSensorUndeployHandler(RoutingContext routingContext) {
//    HttpServerResponse response = routingContext.response();
//    setHeaders(response);
//    routingContext.request().bodyHandler(body -> {
//      Map<String, String> pathParams = routingContext.pathParams();
//      String clusterNodeID = pathParams.get("clusterNodeID");
//      String deploymentID = pathParams.get("deploymentID");
//
//      JsonObject payload = new JsonObject()
//        .put("eventType", "undeploy")
//        .put("clusterNodeID", clusterNodeID)
//        .put("deploymentID", deploymentID);
//
//      vertx.eventBus().request("verticle.controller." + clusterNodeID, payload)
//        .onSuccess(ar -> {
//          JsonObject result = (JsonObject) ar.body();
//          vertx.eventBus().publish("cluster.HeatSensors", result);
//          response.end(
//            new JsonObject()
//              .put("status", "success")
//              .put("deploymentID", result.getString("deploymentID"))
//              .encode()
//          );
//        })
//        .onFailure(ar -> {
//          response.setStatusCode(500).end(new JsonObject().put("status", "failure").put("message", ar.getMessage()).encode());
//        });
//    });
//  }


}
