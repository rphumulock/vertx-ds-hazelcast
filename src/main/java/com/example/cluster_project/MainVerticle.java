package com.example.cluster_project;

import com.example.cluster_project.services.ClusterRegistrationService;
import com.example.cluster_project.services.ClusterRegistrationServiceImpl;

import com.example.cluster_project.verticles.HTTPServer;
import com.example.cluster_project.verticles.HeatSensor;
import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;

import io.vertx.serviceproxy.ServiceBinder;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.example.cluster_project.utils.DatastarUtils.*;

public class MainVerticle extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);


  private ClusterRegistrationService proxy;

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
    JsonObject config = new JsonObject().put("clusterID", deploymentID());
    DeploymentOptions options = new DeploymentOptions().setConfig(config);

    ClusterRegistrationService service = new ClusterRegistrationServiceImpl(vertx);
    new ServiceBinder(vertx)
      .setAddress("cluster.registration")
      .register(ClusterRegistrationService.class, service);

    proxy = ClusterRegistrationService.createProxy(vertx, "cluster.registration");
    proxy.registerVerticle(deploymentID(), MainVerticle.class.getName(), deploymentID())
      .compose(v -> proxy.deployVerticle(deploymentID(), HTTPServer.class.getName(), options))
      .compose(v -> setupConsumers())
      .onSuccess(v -> {
        logger.info("All verticles deployed successfully.");
        startPromise.complete();
      })
      .onFailure(cause -> {
        logger.error("Failed to deploy verticles", cause);
        startPromise.fail(cause);
      });
  }

  public Future<Void> setupConsumers() {
    Promise<Void> promise = Promise.promise();

    MessageConsumer<JsonObject> deployConsumer = vertx.eventBus().consumer("deploy-heat-sensor." + deploymentID());
    handleDeployConsumer(deployConsumer);
    MessageConsumer<JsonObject> undeployConsumer = vertx.eventBus().consumer("undeploy-heat-sensor." + deploymentID());
    handleDeployConsumer(undeployConsumer);

    promise.complete();
    return promise.future();
  }

  public void handleDeployConsumer(MessageConsumer<JsonObject> consumer) {
    consumer.handler(message -> {
      String action = message.headers().get("action");
      switch (action) {
        case "deploy":
          deployVerticle(message);
          break;
        case "undeploy":
          undeployVerticle(message);
          break;
      }
    });
    consumer.completionHandler(res -> {
      if (res.succeeded()) {
        logger.info("deploy-heat-sensor.{} consumer completionHandler succeeded.", deploymentID());
      } else {
        logger.info("deploy-heat-sensor.{} consumer completionHandler failed.", deploymentID());
      }
    });
    consumer.exceptionHandler(res -> {
      logger.info("deploy-heat-sensor.{} consumer exceptionHandler succeeded.", deploymentID());
    });
    consumer.endHandler(res -> {
      logger.info("deploy-heat-sensor.{} consumer endHandler failed.", deploymentID());
    });
  }

  public void deployVerticle(Message<JsonObject> message) {
    JsonObject body = message.body();
    String deploymentName = body.getString("deploymentName");
    DeploymentOptions options = new DeploymentOptions(body.getJsonObject("options"));

    proxy.deployVerticle(deploymentID(), deploymentName, options)
      .onSuccess(deploymentID -> {
        JsonObject reply = new JsonObject()
          .put("status", "success")
          .put("deploymentID", deploymentID);
        message.reply(reply);
      })
      .onFailure(err -> {
        JsonObject reply = new JsonObject()
          .put("status", "failure")
          .put("message", err.getMessage());
        message.reply(reply);
      });
  }

  public void undeployVerticle(Message<JsonObject> message) {
    JsonObject body = message.body();
    String deploymentID = body.getString("deploymentID");
    String deploymentName = body.getString("deploymentName");

    proxy.undeployVerticle(deploymentID(), deploymentName, deploymentID)
      .onSuccess(undeploymentID -> {
        JsonObject reply = new JsonObject()
          .put("status", "success")
          .put("undeploymentID", undeploymentID);
        message.reply(reply);
      })
      .onFailure(err -> {
        JsonObject reply = new JsonObject()
          .put("status", "failure")
          .put("message", err.getMessage());
        message.reply(reply);
      });
  }

}


//  @Override
//  public void start(Promise<Void> startPromise) {
//    String clusterID = deploymentID();
//    JsonObject config = new JsonObject()
//      .put("clusterID", clusterID);
//
//    DeploymentOptions options = new DeploymentOptions()
//      .setConfig(config);
//
//    ClusterRegistrationService service = new ClusterRegistrationServiceImpl(vertx);
//    new ServiceBinder(vertx)
//      .setAddress("cluster.registration")
//      .register(ClusterRegistrationService.class, service);
//
////    deployClusterRegistration(options)
////      .compose(v -> registerVerticle(MainVerticle.class.getName(), deploymentID(), deploymentID()))
////      .compose(v -> registerAndDeployVerticle(HTTPServer.class.getName(), options))
////      .onSuccess(v -> {
////        logger.info("All verticles deployed successfully.");
////        startPromise.complete();
////      })
////      .onFailure(cause -> {
////        logger.error("Failed to deploy verticles", cause);
////        startPromise.fail(cause);
////      });
//  }

//  @Override
//  public void stop(Promise<Void> stopPromise) {
//    String clusterID = deploymentID();
//    if (this.consumer.isRegistered()) {
//      this.consumer.unregister()
//        .onComplete(res -> {
//          if (res.succeeded()) {
//            logger.info("verticle.controller unregistered succeeded. clusterID-[{}].", clusterID);
//          } else {
//            logger.error("verticle.controller unregistered failed. clusterID-[{}].", clusterID);
//          }
//        });
//    }
//  }


//  private Future<String> deployClusterRegistration(DeploymentOptions options) {
//    Promise<String> promise = Promise.promise();
//    vertx.deployVerticle(ClusterRegistration.class.getName(), options, res -> {
//      if (res.succeeded()) {
//        String deploymentID = res.result();
//        logger.info("ClusterRegistration verticle deployed successfully with deploymentID: {}", deploymentID);
//        promise.complete(deploymentID);
//      } else {
//        logger.error("Failed to deploy ClusterRegistration verticle", res.cause());
//        promise.fail(res.cause());
//      }
//    });
//    return promise.future();
//  }

//  private Future<Void> registerVerticle(String deploymentName, String deploymentID, String clusterID) {
//    Promise<Void> promise = Promise.promise();
//    vertx.eventBus().request("cluster.registration", new JsonObject()
//      .put("action", "register")
//      .put("deploymentName", deploymentName)
//      .put("clusterID", clusterID)
//      .put("deploymentID", deploymentID), ar -> {
//      if (ar.succeeded()) {
//        promise.complete();
//      } else {
//        promise.fail(ar.cause());
//      }
//    });
//    return promise.future();
//  }
//
//  private Future<Void> registerAndDeployVerticle(String deploymentName, DeploymentOptions options) {
//    Promise<Void> promise = Promise.promise();
//    vertx.eventBus().request("cluster.registration", new JsonObject()
//      .put("action", "deploy")
//      .put("clusterID", deploymentID())
//      .put("deploymentName", deploymentName)
//      .put("options", options.toJson()), ar -> {
//      if (ar.succeeded()) {
//        promise.complete();
//      } else {
//        promise.fail(ar.cause());
//      }
//    });
//    return promise.future();
//  }
//
//}

//  private void setupVerticleController() {
//    String clusterID = deploymentID();
//    MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer("verticle.controller." + clusterID);
//    verticleControllerHandlers(consumer);
//  }
//
//  private void verticleControllerHandlers(MessageConsumer<JsonObject> consumer) {
//    String clusterID = deploymentID();
//
//    consumer.handler(message -> {
//      JsonObject payload = message.body();
//      String eventType = payload.getString("eventType");
//      switch (eventType) {
//        case "deploy":
//          deployVerticle(HeatSensor.class.getName(), message);
//          break;
//        case "undeploy":
//          String deploymentID = payload.getString("deploymentID");
//          undeployVerticle(message, deploymentID);
//          break;
//      }
//    });
//
//    consumer.completionHandler(res -> {
//      if (res.succeeded()) {
//        logger.info("completionHandler verticle.controller succeeded: clusterID-[{}].", clusterID);
//      } else {
//        logger.info("completionHandler verticle.controller failed: clusterID-[{}].", clusterID);
//      }
//    });
//
//    consumer.endHandler(unused -> {
//      logger.info("endHandler verticle.controller: clusterID-[{}].", clusterID);
//    });
//
//    consumer.exceptionHandler(res -> {
//      logger.error("exceptionHandler verticle.controller: clusterID-[{}]. Cause: {}.", clusterID, res.getCause());
//    });
//
//    this.consumer = consumer;
//  }
//
//  private void deployVerticle(String deploymentName, Message<JsonObject> message) {
//    String clusterID = message.body().getString("clusterID");
//    JsonObject config = new JsonObject()
//      .put("clusterID", clusterID);
//    DeploymentOptions options = new DeploymentOptions().setConfig(config);
//
//    vertx.deployVerticle(deploymentName, options)
//      .onSuccess(deploymentID -> {
//
//        JsonObject registerMessage = new JsonObject()
//          .put("action", "register")
//          .put("type", ClusterRegistration.class.getSimpleName())
//          .put("clusterID", "node1")
//          .put("verticleID", "verticle1");
//
//        vertx.eventBus().request("cluster.registration", registerMessage, reply -> {
//          if (reply.succeeded()) {
//            System.out.println("Verticle registered successfully: " + reply.result().body());
//          } else {
//            System.err.println("Failed to register verticle: " + reply.cause().getMessage());
//          }
//        });
//      })
//      .onFailure(ar -> {
//        JsonObject reply = new JsonObject()
//          .put("status", "failure")
//          .put("message", "Failed to deploy verticle");
//        message.reply(reply);
//      });
//  }
//
//  private void undeployVerticle(Message<JsonObject> message, String deploymentID) {
//    String clusterID = message.body().getString("clusterID");
//
//    vertx.undeploy(deploymentID)
//      .onSuccess(v -> {
////        registrationService.unregisterVerticle("HeatSensor", clusterID, deploymentID)
////          .onSuccess(ar -> {
////            logger.info("Cluster {} undeployed Heat Sensor {}.", clusterID, deploymentID);
////            JsonObject payload = new JsonObject()
////              .put("status", "success")
////              .put("eventType", "undeploy.HeatSensor")
////              .put("clusterID", clusterID)
////              .put("deploymentID", deploymentID);
////            message.reply(payload);
////          })
////          .onFailure(ar -> {
////            JsonObject reply = new JsonObject()
////              .put("status", "failure")
////              .put("message", "Failed to unregister heat sensor");
////            message.reply(reply);
////          });
//      })
//      .onFailure(ar -> {
//        JsonObject reply = new JsonObject()
//          .put("status", "failure")
//          .put("message", "Failed to undeploy verticle");
//        message.reply(reply);
//      });


//  private void deployVerticles(Promise<Void> startPromise, JsonObject config) {
//    String clusterID = config.getString("clusterID");
//    deployHTTPServerVerticle(config).onComplete(res -> {
//      if (res.succeeded()) {
//        String verticleID = res.result();
////        registrationService.registerAndIncrementCounter("HTTPServer", verticleID, clusterID)
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


