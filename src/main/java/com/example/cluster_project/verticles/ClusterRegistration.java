package com.example.cluster_project.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.json.JsonObject;

public class ClusterRegistration extends AbstractVerticle {
  private static final Logger logger = LoggerFactory.getLogger(ClusterRegistration.class);
  private static final String CLUSTER_NODE_ID = "yourClusterID"; // Replace with your actual cluster node ID

  @Override
  public void start(Promise<Void> startPromise) {
    String nodeDeploymentID = deploymentID();
    JsonObject config = new JsonObject();
    config.put("nodeDeploymentID", nodeDeploymentID);

    register(nodeDeploymentID, CLUSTER_NODE_ID, startPromise, config);
  }

  private void register(String nodeDeploymentID, String clusterNodeID, Promise<Void> startPromise, JsonObject config) {
    vertx.sharedData().<String, AsyncMap<String, Long>>getClusterWideMap("activeNodes", res -> {
      if (res.succeeded()) {
        AsyncMap<String, AsyncMap<String, Long>> clusterMap = res.result();
        clusterMap.get(clusterNodeID, ar -> {
          if (ar.succeeded()) {
            AsyncMap<String, Long> verticleMap = ar.result();
            if (verticleMap == null) {
              vertx.sharedData().<String, Long>getClusterWideMap("verticles-" + clusterNodeID, ar2 -> {
                if (ar2.succeeded()) {
                  verticleMap = ar2.result();
                  clusterMap.put(clusterNodeID, verticleMap, ar3 -> {
                    if (ar3.succeeded()) {
                      registerVerticle(nodeDeploymentID, verticleMap, startPromise, config);
                    } else {
                      logger.error("Failed to register cluster node {}.", clusterNodeID, ar3.cause());
                      startPromise.fail(ar3.cause());
                    }
                  });
                } else {
                  logger.error("Failed to get verticle map for cluster node {}.", clusterNodeID, ar2.cause());
                  startPromise.fail(ar2.cause());
                }
              });
            } else {
              registerVerticle(nodeDeploymentID, verticleMap, startPromise, config);
            }
          } else {
            logger.error("Failed to get cluster node {}.", clusterNodeID, ar.cause());
            startPromise.fail(ar.cause());
          }
        });
      } else {
        logger.error("Failed to get cluster-wide map.", res.cause());
        startPromise.fail(res.cause());
      }
    });
  }

  private void registerVerticle(String nodeDeploymentID, AsyncMap<String, Long> verticleMap, Promise<Void> startPromise, JsonObject config) {
    verticleMap.put(nodeDeploymentID, System.currentTimeMillis(), ar -> {
      if (ar.succeeded()) {
        logger.info("Verticle {} registered in cluster node {}.", nodeDeploymentID, CLUSTER_NODE_ID);
        this.deployVerticles(startPromise, config);
      } else {
        logger.error("Failed to register verticle {} in cluster node {}.", nodeDeploymentID, CLUSTER_NODE_ID, ar.cause());
        startPromise.fail(ar.cause());
      }
    });
  }

  private void deployVerticles(Promise<Void> startPromise, JsonObject config) {
    // Your verticle deployment logic
    startPromise.complete();
  }

  public void unregister(String nodeDeploymentID) {
    vertx.sharedData().<String, AsyncMap<String, Long>>getClusterWideMap("activeNodes", res -> {
      if (res.succeeded()) {
        AsyncMap<String, AsyncMap<String, Long>> clusterMap = res.result();
        clusterMap.get(CLUSTER_NODE_ID, ar -> {
          if (ar.succeeded()) {
            AsyncMap<String, Long> verticleMap = ar.result();
            if (verticleMap != null) {
              verticleMap.remove(nodeDeploymentID, ar2 -> {
                if (ar2.succeeded()) {
                  logger.info("Verticle {} unregistered from cluster node {}.", nodeDeploymentID, CLUSTER_NODE_ID);
                } else {
                  logger.error("Failed to unregister verticle {} from cluster node {}.", nodeDeploymentID, CLUSTER_NODE_ID, ar2.cause());
                }
              });
            } else {
              logger.warn("No verticle map found for cluster node {}.", CLUSTER_NODE_ID);
            }
          } else {
            logger.error("Failed to get cluster node {}.", CLUSTER_NODE_ID, ar.cause());
          }
        });
      } else {
        logger.error("Failed to get cluster-wide map.", res.cause());
      }
    });
  }
}
