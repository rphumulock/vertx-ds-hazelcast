package com.example.cluster_project.services;

import com.example.cluster_project.MainVerticle;
import io.vertx.core.*;
import io.vertx.core.shareddata.AsyncMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ClusterRegistrationServiceImpl implements ClusterRegistrationService {

  private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

  private final Vertx vertx;

  public ClusterRegistrationServiceImpl(Vertx vertx) {
    this.vertx = vertx;
  }

  private Future<AsyncMap<String, ArrayList<String>>> getClusterRegistry() {
    Promise<AsyncMap<String, ArrayList<String>>> promise = Promise.promise();
    vertx.sharedData().<String, ArrayList<String>>getAsyncMap("registry", promise);
    return promise.future();
  }

  private Future<AsyncMap<String, Integer>> getVerticleCounterMap() {
    Promise<AsyncMap<String, Integer>> promise = Promise.promise();
    vertx.sharedData().<String, Integer>getAsyncMap("verticleCounter", promise);
    return promise.future();
  }

  public Future<Void> registerAndIncrementCounter(String type, String verticleID, String clusterNodeID) {
    Promise<Void> promise = Promise.promise();
    String mapKey = "registered." + type + "." + clusterNodeID;

    getClusterRegistry()
      .compose(map -> addVerticleID(map, mapKey, verticleID))
      .compose(v -> incrementVerticleCounter(type))
      .onComplete(promise);

    return promise.future();
  }

  private Future<Void> incrementVerticleCounter(String type) {
    Promise<Void> promise = Promise.promise();
    getVerticleCounterMap().onComplete(res -> {
      if (res.succeeded()) {
        AsyncMap<String, Integer> counterMap = res.result();
        counterMap.get(type, getRes -> {
          if (getRes.succeeded()) {
            Integer currentCount = getRes.result();
            AtomicInteger count = new AtomicInteger(currentCount != null ? currentCount : 0);
            count.incrementAndGet();
            counterMap.put(type, count.get(), putRes -> {
              if (putRes.succeeded()) {
                logger.debug("Counter incremented for verticle type: {}, new count: {}", type, count.get());
                promise.complete();
              } else {
                promise.fail(putRes.cause());
              }
            });
          } else {
            promise.fail(getRes.cause());
          }
        });
      } else {
        promise.fail(res.cause());
      }
    });
    return promise.future();
  }

  @Override
  public Future<Void> registerVerticle(String type, String clusterNodeID, String verticleID) {
    Promise<Void> promise = Promise.promise();
    String mapKey = "registered." + type + "." + clusterNodeID;
    getClusterRegistry().onComplete(res -> {
      if (res.succeeded()) {
        AsyncMap<String, ArrayList<String>> map = res.result();
        addVerticleID(map, mapKey, verticleID).onComplete(promise);
      } else {
        promise.fail(res.cause());
      }
    });
    return promise.future();
  }

  @Override
  public Future<Void> unregisterVerticle(String type, String clusterNodeID, String verticleID) {
    Promise<Void> promise = Promise.promise();
    String mapKey = "registered." + type + "." + clusterNodeID;
    getClusterRegistry().onComplete(res -> {
      if (res.succeeded()) {
        AsyncMap<String, ArrayList<String>> map = res.result();
        removeDeploymentIDFromMap(map, mapKey, verticleID).onComplete(promise);
      } else {
        promise.fail(res.cause());
      }
    });
    return promise.future();
  }

  public void listDeployedVerticles() {
    logger.debug("Verticles Deployed on this Cluster Node: [{}].", vertx.deploymentIDs().toString());
  }

  private Future<Void> addVerticleID(AsyncMap<String, ArrayList<String>> map, String mapKey, String verticleID) {
    Promise<Void> promise = Promise.promise();
    map.get(mapKey, getRes -> {
      if (getRes.succeeded()) {
        ArrayList<String> verticleIDs = getRes.result();
        if (verticleIDs == null) {
          verticleIDs = new ArrayList<>();
        }

        // Add the current deployment ID to the list
        verticleIDs.add(verticleID);

        // Put the updated list back into the map
        map.put(mapKey, verticleIDs, putRes -> {
          if (putRes.succeeded()) {
            logger.debug("Cluster-wide map entry created with key: [{}].", mapKey);
            promise.complete();
          } else {
            promise.fail(putRes.cause());
          }
        });
      } else {
        promise.fail(getRes.cause());
      }
    });
    return promise.future();
  }

  private Future<Void> removeDeploymentIDFromMap(AsyncMap<String, ArrayList<String>> map, String mapKey, String deploymentID) {
    Promise<Void> promise = Promise.promise();
    map.get(mapKey, getRes -> {
      if (getRes.succeeded()) {
        ArrayList<String> deploymentIds = getRes.result();
        if (deploymentIds != null) {
          // Remove the deployment ID from the list
          deploymentIds.remove(deploymentID);

          // Update the map with the new list or remove the key if the list is empty
          if (deploymentIds.isEmpty()) {
            map.remove(mapKey, removeRes -> {
              if (removeRes.succeeded()) {
                logger.debug("Cluster-wide map entry removed with key: {}.", mapKey);
                promise.complete();
              } else {
                promise.fail(removeRes.cause());
              }
            });
          } else {
            map.put(mapKey, deploymentIds, putRes -> {
              if (putRes.succeeded()) {
                logger.debug("Cluster-wide map entry updated with key: {}.", mapKey);
                promise.complete();
              } else {
                promise.fail(putRes.cause());
              }
            });
          }
        } else {
          promise.complete();
        }
      } else {
        promise.fail(getRes.cause());
      }
    });
    return promise.future();
  }

  public void logClusterRegistry() {
    Promise<Void> promise = Promise.promise();
    getClusterRegistry().onComplete(res -> {
      if (res.succeeded()) {
        AsyncMap<String, ArrayList<String>> map = res.result();
        map.entries(entriesRes -> {
          if (entriesRes.succeeded()) {
            entriesRes.result().forEach((key, value) -> {
              logger.debug("{} : {}", key, value);
            });
            promise.complete();
          } else {
            promise.fail(entriesRes.cause());
          }
        });
      } else {
        promise.fail(res.cause());
      }
    });
  }
}
