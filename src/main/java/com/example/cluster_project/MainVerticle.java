package com.example.cluster_project;

import com.example.cluster_project.services.ClusterRegistrationServiceImpl;
import com.example.cluster_project.verticles.HTTPServer;

import io.vertx.core.*;
import io.vertx.core.json.JsonObject;

import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
              registrationService.logClusterRegistry();
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
