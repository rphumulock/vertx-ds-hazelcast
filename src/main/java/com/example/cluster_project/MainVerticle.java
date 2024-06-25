package com.example.cluster_project;

import com.example.cluster_project.verticles.ClusterRegistration;
import com.example.cluster_project.verticles.HTTPServer;
import com.example.cluster_project.verticles.HeatSensor;

import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;

import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class MainVerticle extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

  private MessageConsumer<JsonObject> consumer;

  public static void main(String[] args) {
    HazelcastClusterManager mgr = new HazelcastClusterManager();
    VertxOptions options = new VertxOptions().setClusterManager(mgr);

    Vertx.clusteredVertx(options, res -> {
      if (res.succeeded()) {
        Vertx vertx = res.result();
        vertx.deployVerticle(new MainVerticle(), deployRes -> {
          if (deployRes.succeeded()) {
            logger.info("MainVerticle deployed successfully");
          } else {
            logger.error("Failed to deploy MainVerticle", deployRes.cause());
          }
        });
      } else {
        logger.error("Failed to start clustered Vert.x instance", res.cause());
      }
    });
  }

  @Override
  public void start(Promise<Void> startPromise) {
    String clusterNodeID = deploymentID();
    JsonObject config = new JsonObject()
      .put("clusterNodeID", clusterNodeID);

    DeploymentOptions options = new DeploymentOptions()
      .setConfig(config);

    deployClusterRegistration()
      .compose(deploymentID -> registerVerticle(ClusterRegistration.class.getName(), deploymentID))
      .compose(v -> registerVerticle(MainVerticle.class.getName(), deploymentID()))
      .compose(v -> registerAndDeployVerticle(HTTPServer.class.getName(), options))
      .compose(v -> logRegister())
      .onComplete(ar -> {
        if (ar.succeeded()) {
          logger.info("All verticles deployed successfully.");
          startPromise.complete();
        } else {
          logger.error("Failed to deploy verticles", ar.cause());
          startPromise.fail(ar.cause());
        }
      });
  }

  @Override
  public void stop(Promise<Void> stopPromise) {
    String clusterNodeID = deploymentID();
    if (this.consumer.isRegistered()) {
      this.consumer.unregister()
        .onComplete(res -> {
          if (res.succeeded()) {
            logger.info("verticle.controller unregistered succeeded. clusterNodeID-[{}].", clusterNodeID);
          } else {
            logger.error("verticle.controller unregistered failed. clusterNodeID-[{}].", clusterNodeID);
          }
        });
    }
  }

  private Future<String> deployClusterRegistration() {
    Promise<String> promise = Promise.promise();
    vertx.deployVerticle(ClusterRegistration.class.getName(), res -> {
      if (res.succeeded()) {
        String deploymentID = res.result();
        logger.info("ClusterRegistration verticle deployed successfully with deploymentID: {}", deploymentID);
        promise.complete(deploymentID);
      } else {
        logger.error("Failed to deploy ClusterRegistration verticle", res.cause());
        promise.fail(res.cause());
      }
    });
    return promise.future();
  }

  private Future<Void> registerVerticle(String deploymentName, String deploymentID) {
    Promise<Void> promise = Promise.promise();
    vertx.eventBus().request("cluster.registration", new JsonObject()
      .put("action", "register")
      .put("deploymentName", deploymentName)
      .put("deploymentID", deploymentID), ar -> {
      if (ar.succeeded()) {
        promise.complete();
      } else {
        promise.fail(ar.cause());
      }
    });
    return promise.future();
  }

  private Future<Void> registerAndDeployVerticle(String deploymentName, DeploymentOptions options) {
    Promise<Void> promise = Promise.promise();
    vertx.eventBus().request("cluster.registration", new JsonObject()
      .put("action", "deploy")
      .put("deploymentName", deploymentName)
      .put("options", options.toJson()), ar -> {
      if (ar.succeeded()) {
        promise.complete();
      } else {
        promise.fail(ar.cause());
      }
    });
    return promise.future();
  }

  private Future<Void> logRegister() {
    Promise<Void> promise = Promise.promise();
    vertx.eventBus().request("cluster.registration", new JsonObject()
      .put("action", "log"), ar -> {
      if (ar.succeeded()) {
        promise.complete();
      } else {
        promise.fail(ar.cause());
      }
    });
    return promise.future();
  }


  private void setupVerticleController() {
    String clusterNodeID = deploymentID();
    MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer("verticle.controller." + clusterNodeID);
    verticleControllerHandlers(consumer);
  }

  private void verticleControllerHandlers(MessageConsumer<JsonObject> consumer) {
    String clusterNodeID = deploymentID();

    consumer.handler(message -> {
      JsonObject payload = message.body();
      String eventType = payload.getString("eventType");
      switch (eventType) {
        case "deploy":
          deployVerticle(HeatSensor.class.getName(), message);
          break;
        case "undeploy":
          String deploymentID = payload.getString("deploymentID");
          undeployVerticle(message, deploymentID);
          break;
      }
    });

    consumer.completionHandler(res -> {
      if (res.succeeded()) {
        logger.info("completionHandler verticle.controller succeeded: clusterNodeID-[{}].", clusterNodeID);
      } else {
        logger.info("completionHandler verticle.controller failed: clusterNodeID-[{}].", clusterNodeID);
      }
    });

    consumer.endHandler(unused -> {
      logger.info("endHandler verticle.controller: clusterNodeID-[{}].", clusterNodeID);
    });

    consumer.exceptionHandler(res -> {
      logger.error("exceptionHandler verticle.controller: clusterNodeID-[{}]. Cause: {}.", clusterNodeID, res.getCause());
    });

    this.consumer = consumer;
  }

  private void deployVerticle(String deploymentName, Message<JsonObject> message) {
    String clusterNodeID = message.body().getString("clusterNodeID");
    JsonObject config = new JsonObject()
      .put("clusterNodeID", clusterNodeID);
    DeploymentOptions options = new DeploymentOptions().setConfig(config);

    vertx.deployVerticle(deploymentName, options)
      .onSuccess(deploymentID -> {

        JsonObject registerMessage = new JsonObject()
          .put("action", "register")
          .put("type", ClusterRegistration.class.getSimpleName())
          .put("clusterNodeID", "node1")
          .put("verticleID", "verticle1");

        vertx.eventBus().request("cluster.registration", registerMessage, reply -> {
          if (reply.succeeded()) {
            System.out.println("Verticle registered successfully: " + reply.result().body());
          } else {
            System.err.println("Failed to register verticle: " + reply.cause().getMessage());
          }
        });


//        registrationService.registerAndIncrementCounter(deploymentName, clusterNodeID, deploymentID)
//          .onSuccess(v -> {
//            logger.info("Cluster {} deployed {} {}.", clusterNodeID, deploymentName, deploymentID);
//            JsonObject payload = new JsonObject()
//              .put("status", "success")
//              .put("eventType", "deploy.HeatSensor")
//              .put("deploymentID", deploymentID)
//              .put("clusterNodeID", clusterNodeID);
//            message.reply(payload);
//          })
//          .onFailure(ar -> {
//            JsonObject reply = new JsonObject()
//              .put("status", "failure")
//              .put("message", "Failed to register verticle");
//            message.reply(reply);
//          });
      })
      .onFailure(ar -> {
        JsonObject reply = new JsonObject()
          .put("status", "failure")
          .put("message", "Failed to deploy verticle");
        message.reply(reply);
      });
  }

  private void undeployVerticle(Message<JsonObject> message, String deploymentID) {
    String clusterNodeID = message.body().getString("clusterNodeID");

    vertx.undeploy(deploymentID)
      .onSuccess(v -> {
//        registrationService.unregisterVerticle("HeatSensor", clusterNodeID, deploymentID)
//          .onSuccess(ar -> {
//            logger.info("Cluster {} undeployed Heat Sensor {}.", clusterNodeID, deploymentID);
//            JsonObject payload = new JsonObject()
//              .put("status", "success")
//              .put("eventType", "undeploy.HeatSensor")
//              .put("clusterNodeID", clusterNodeID)
//              .put("deploymentID", deploymentID);
//            message.reply(payload);
//          })
//          .onFailure(ar -> {
//            JsonObject reply = new JsonObject()
//              .put("status", "failure")
//              .put("message", "Failed to unregister heat sensor");
//            message.reply(reply);
//          });
      })
      .onFailure(ar -> {
        JsonObject reply = new JsonObject()
          .put("status", "failure")
          .put("message", "Failed to undeploy verticle");
        message.reply(reply);
      });
  }


//  private void deployVerticles(Promise<Void> startPromise, JsonObject config) {
//    String clusterNodeID = config.getString("clusterNodeID");
//    deployHTTPServerVerticle(config).onComplete(res -> {
//      if (res.succeeded()) {
//        String verticleID = res.result();
////        registrationService.registerAndIncrementCounter("HTTPServer", verticleID, clusterNodeID)
////          .onComplete(regRes -> {
////            if (regRes.succeeded()) {
////              startPromise.complete();
////              logger.info("All verticles deployed successfully");
////            }
////          });
//      } else {
//        startPromise.fail(res.cause());
//        logger.error("Failed to deploy verticles", res.cause());
//      }
//    });
//  }
//
//  private Future<String> deployHTTPServerVerticle(JsonObject config) {
//    Promise<String> promise = Promise.promise();
//    DeploymentOptions options = new DeploymentOptions().setConfig(config);
//    vertx.deployVerticle(new HTTPServer(), options, promise);
//    return promise.future();
//  }
}


