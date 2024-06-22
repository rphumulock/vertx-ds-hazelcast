package com.example.cluster_project;

import com.example.cluster_project.services.ClusterRegistrationServiceImpl;
import com.example.cluster_project.verticles.HTTPServer;

import com.example.cluster_project.verticles.HeatSensor;
import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;

import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.example.cluster_project.utils.DatastarUtils.*;

public class MainVerticle extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

  private ClusterRegistrationServiceImpl registrationService;

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

    setupVerticleController(clusterNodeID);

    registrationService = new ClusterRegistrationServiceImpl(vertx);
    registrationService.registerAndIncrementCounter("MainVerticle", clusterNodeID, clusterNodeID)
      .onComplete(regRes -> {
        if (regRes.succeeded()) {
          vertx.sharedData().<String, Long>getClusterWideMap("activeNodes", res -> {
            if (res.succeeded()) {
              res.result().put(clusterNodeID, System.currentTimeMillis(), ar -> {
                if (ar.succeeded()) {
                  logger.info("Node {} registered in cluster.", clusterNodeID);
                  this.deployVerticles(startPromise, config);
                } else {
                  logger.error("Failed to register node {} in cluster.", clusterNodeID, ar.cause());
                }
              });
            } else {
              logger.error("Failed to get cluster-wide map.", res.cause());
            }
          });
        } else {
          startPromise.fail(regRes.cause());
        }
      });
  }

  private void setupVerticleController(String clusterNodeID) {
    MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer("verticle.controller." + clusterNodeID);

    consumer.handler(message -> {
      JsonObject payload = message.body();
      String eventType = payload.getString("eventType");
      if (eventType.equals("deploy.HeatSensor")) {
        deployVerticle(HeatSensor.class.getName(), message);
      } else if (eventType.equals("undeploy.HeatSensor")) {
        String heatSensorID = payload.getString("heatSensorID");
        undeployVerticle(message, heatSensorID);
      }
    });

    consumer.completionHandler(res -> {
      if (res.succeeded()) {
        logger.info("The verticle.controller handler registration for Cluster Node: {} has reached all nodes.", clusterNodeID);
      } else {
        logger.info("The verticle.controller handler registration for Cluster Node: {} has failed.", clusterNodeID);
      }
    });

    consumer.endHandler(res -> {
      logger.info("Unregistering verticle.controller consumer for Cluster Node: {}.", clusterNodeID);
    });

    consumer.exceptionHandler(res -> {
      logger.error("There was an exception in the verticle.controller consumer for Cluster Node: {}.", clusterNodeID);
    });
  }

  private void deployVerticle(String verticleName, Message<JsonObject> message) {
    String clusterNodeID = message.body().getString("clusterNodeID");
    JsonObject config = new JsonObject()
      .put("clusterNodeID", clusterNodeID);
    DeploymentOptions options = new DeploymentOptions().setConfig(config);

    vertx.deployVerticle(verticleName, options)
      .onSuccess(deploymentID -> {
        registrationService.registerAndIncrementCounter(verticleName, clusterNodeID, deploymentID)
          .onSuccess(v -> {
            logger.info("Cluster {} deployed {} {}.", clusterNodeID, verticleName, deploymentID);
            JsonObject payload = new JsonObject()
              .put("status", "success")
              .put("eventType", "deploy.HeatSensor")
              .put("deploymentID", deploymentID)
              .put("clusterNodeID", clusterNodeID);
            message.reply(payload);
          })
          .onFailure(ar -> {
            JsonObject reply = new JsonObject()
              .put("status", "failure")
              .put("message", "Failed to register verticle");
            message.reply(reply);
          });
      })
      .onFailure(ar -> {
        JsonObject reply = new JsonObject()
          .put("status", "failure")
          .put("message", "Failed to deploy verticle");
        message.reply(reply);
      });
  }

  private void undeployVerticle(Message<JsonObject> message, String heatSensorID) {
    String clusterNodeID = message.body().getString("clusterNodeID");

    vertx.undeploy(heatSensorID)
      .onSuccess(v -> {
        registrationService.unregisterVerticle("HeatSensor", clusterNodeID, heatSensorID)
          .onSuccess(ar -> {
            logger.info("Cluster {} undeployed Heat Sensor {}.", clusterNodeID, heatSensorID);
            JsonObject reply = new JsonObject()
              .put("status", "success")
              .put("heatSensorID", heatSensorID);
            message.reply(reply);
          })
          .onFailure(ar -> {
            JsonObject reply = new JsonObject()
              .put("status", "failure")
              .put("message", "Failed to unregister heat sensor");
            message.reply(reply);
          });
      })
      .onFailure(ar -> {
        JsonObject reply = new JsonObject()
          .put("status", "failure")
          .put("message", "Failed to undeploy verticle");
        message.reply(reply);
      });
  }


  private void deployVerticles(Promise<Void> startPromise, JsonObject config) {
    String clusterNodeID = config.getString("clusterNodeID");
    deployHTTPServerVerticle(config).onComplete(res -> {
      if (res.succeeded()) {
        String verticleID = res.result();
        registrationService.registerAndIncrementCounter("HTTPServer", verticleID, clusterNodeID)
          .onComplete(regRes -> {
            if (regRes.succeeded()) {
              startPromise.complete();
              logger.info("All verticles deployed successfully");
            }
          });
      } else {
        startPromise.fail(res.cause());
        logger.error("Failed to deploy verticles", res.cause());
      }
    });
  }

  private Future<String> deployHTTPServerVerticle(JsonObject config) {
    Promise<String> promise = Promise.promise();
    DeploymentOptions options = new DeploymentOptions().setConfig(config);
    vertx.deployVerticle(new HTTPServer(), options, promise);
    return promise.future();
  }
}
