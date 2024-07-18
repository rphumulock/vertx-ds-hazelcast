package com.example.cluster_project.verticles;

import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

public class ClusterRegistration extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(ClusterRegistration.class);

  @Override
  public void start(Promise<Void> startPromise) {
    setupConsumer();
    startPromise.complete();
  }

  private void setupConsumer() {
    String thisClusterID = config().getString("clusterID");
    vertx.eventBus().consumer("cluster.registration", message -> {
      JsonObject body = (JsonObject) message.body();
      String action = body.getString("action");
      String clusterID = body.getString("clusterID");
      String deploymentName = body.getString("deploymentName");
      String deploymentID = body.getString("deploymentID");

      DeploymentOptions options = new DeploymentOptions(body.getJsonObject("options", new JsonObject()));

      switch (action) {
        case "deploy":
          if (clusterID.equals(thisClusterID)) {
            handleDeploy(message, deploymentName, options, clusterID)
              .onSuccess(v -> logClusterVerticleRegistry())
              .onFailure(cause -> logger.error("Failed to deploy verticle: {}", deploymentName, cause));
          }
          break;
        case "undeploy":
          if (clusterID.equals(thisClusterID)) {
            handleUndeploy(message, deploymentID, deploymentName, clusterID)
              .onSuccess(v -> logClusterVerticleRegistry())
              .onFailure(cause -> logger.error("Failed to undeploy verticle: {}", deploymentName, cause));
          }
          break;
        case "register":
          handleRegister(message, deploymentName, deploymentID, clusterID)
            .onSuccess(v -> logClusterVerticleRegistry())
            .onFailure(cause -> logger.error("Failed to register verticle: {}", deploymentName, cause));
          break;
        case "unregister":
          handleUnregister(message, deploymentName, deploymentID, clusterID)
            .onSuccess(v -> logClusterVerticleRegistry())
            .onFailure(cause -> logger.error("Failed to unregister verticle: {}", deploymentName, cause));
          break;
        case "getEntireRegistry":
          handleGetEntireRegistry(message);
          break;
        case "getRegisteredVerticles":
          if (clusterID != null) {
            handleGetRegisteredVerticlesForCluster(message, deploymentName, clusterID);
          } else {
            handleGetAllRegisteredVerticles(message, deploymentName);
          }
          break;
        case "log":
          logClusterVerticleRegistry();
          message.reply("Logged successfully.");
          break;
        default:
          message.fail(400, "Invalid action");
          break;
      }
    });
  }

  private Future<Void> handleDeploy(Message<Object> message, String deploymentName, DeploymentOptions options, String clusterID) {
    Promise<Void> promise = Promise.promise();
    deployAndRegisterVerticle(deploymentName, options, clusterID).onComplete(ar -> {
      if (ar.succeeded()) {
        logger.info("Successfully deployed and registered verticle: {}", deploymentName);
        message.reply(ar.result());
        promise.complete();
      } else {
        logger.error("Failed to deploy and register verticle: {}", deploymentName, ar.cause());
        message.fail(500, ar.cause().getMessage());
        promise.fail(ar.cause());
      }
    });
    return promise.future();
  }

  private Future<Void> handleUndeploy(Message<Object> message, String deploymentID, String deploymentName, String clusterID) {
    Promise<Void> promise = Promise.promise();
    undeployAndUnregisterVerticle(deploymentID, deploymentName, clusterID).onComplete(ar -> {
      if (ar.succeeded()) {
        logger.info("Successfully undeployed and unregistered verticle: {}", deploymentName);
        message.reply(ar.result());
        promise.complete();
      } else {
        logger.error("Failed to undeploy and unregister verticle: {}", deploymentName, ar.cause());
        message.fail(500, ar.cause().getMessage());
        promise.fail(ar.cause());
      }
    });
    return promise.future();
  }

  private Future<Void> handleRegister(Message<Object> message, String deploymentName, String deploymentID, String clusterID) {
    Promise<Void> promise = Promise.promise();
    registerVerticle(deploymentName, deploymentID, clusterID)
      .onSuccess(result -> {
        logger.info("Successfully registered verticle: {}", deploymentName);
        message.reply(new JsonObject().put("deploymentID", result));
        promise.complete();
      })
      .onFailure(cause -> {
        logger.error("Failed to register verticle: {}", deploymentName, cause);
        message.fail(500, cause.getMessage());
        promise.fail(cause);
      });
    return promise.future();
  }

  private Future<Void> handleUnregister(Message<Object> message, String deploymentName, String deploymentID, String clusterID) {
    Promise<Void> promise = Promise.promise();
    unregisterVerticle(deploymentName, deploymentID, clusterID)
      .onSuccess(result -> {
        logger.info("Successfully unregistered verticle: {}", deploymentName);
        message.reply("Unregistered successfully.");
        promise.complete();
      })
      .onFailure(cause -> {
        logger.error("Failed to unregister verticle: {}", deploymentName, cause);
        message.fail(500, cause.getMessage());
        promise.fail(cause);
      });
    return promise.future();
  }

  private void handleGetEntireRegistry(Message<Object> message) {
    getClusterVerticleRegistry().onSuccess(map -> {
      map.entries().onSuccess(entries -> {
        JsonObject registryMap = new JsonObject();
        entries.forEach(registryMap::put);
        message.reply(registryMap);
      }).onFailure(entriesFailure -> {
        message.fail(500, entriesFailure.getMessage());
      });
    }).onFailure(mapFailure -> {
      message.fail(500, mapFailure.getMessage());
    });
  }

  private void handleGetRegisteredVerticlesForCluster(Message<Object> message, String deploymentName, String clusterID) {
    String mapKey = "registry." + deploymentName;
    getClusterVerticleRegistry().onSuccess(map -> {
      map.get(mapKey).onSuccess(clusterMap -> {
        if (clusterMap != null) {
          JsonArray deploymentIDs = clusterMap.getJsonArray(clusterID);
          if (deploymentIDs != null) {
            message.reply(new JsonObject().put("deploymentIDs", deploymentIDs));
          } else {
            message.reply(new JsonObject().put("deploymentIDs", new JsonArray()));
          }
        } else {
          message.reply(new JsonObject().put("deploymentIDs", new JsonArray()));
        }
      }).onFailure(getRes -> {
        message.fail(500, getRes.getMessage());
      });
    }).onFailure(ar -> {
      message.fail(500, ar.getMessage());
    });
  }

  private void handleGetAllRegisteredVerticles(Message<Object> message, String deploymentName) {
    String mapKey = "registry." + deploymentName;
    getClusterVerticleRegistry().onSuccess(map -> {
      map.get(mapKey).onSuccess(clusterMap -> {
        if (clusterMap != null) {
          message.reply(clusterMap);
        } else {
          message.reply(new JsonObject());
        }
      }).onFailure(getRes -> {
        message.fail(500, getRes.getMessage());
      });
    }).onFailure(ar -> {
      message.fail(500, ar.getMessage());
    });
  }


  private Future<JsonObject> deployAndRegisterVerticle(String deploymentName, DeploymentOptions options, String clusterID) {
    Promise<JsonObject> promise = Promise.promise();
    vertx.deployVerticle(deploymentName, options, res -> {
      if (res.succeeded()) {
        String deploymentID = res.result();
        registerVerticle(deploymentName, deploymentID, clusterID).onComplete(ar -> {
          if (ar.succeeded()) {
            promise.complete(new JsonObject().put("deploymentName", deploymentName).put("deploymentID", deploymentID));
          } else {
            promise.fail(ar.cause());
          }
        });
      } else {
        promise.fail(res.cause());
      }
    });
    return promise.future();
  }

  private Future<JsonObject> undeployAndUnregisterVerticle(String deploymentID, String deploymentName, String clusterID) {
    Promise<JsonObject> promise = Promise.promise();
    vertx.undeploy(deploymentID, res -> {
      if (res.succeeded()) {
        unregisterVerticle(deploymentName, deploymentID, clusterID).onComplete(ar -> {
          if (ar.succeeded()) {
            promise.complete(new JsonObject().put("deploymentName", deploymentName).put("deploymentID", deploymentID));
          } else {
            promise.fail(ar.cause());
          }
        });
      } else {
        promise.fail(res.cause());
      }
    });
    return promise.future();
  }

  private Future<JsonObject> registerVerticle(String deploymentName, String deploymentID, String clusterID) {
    String mapKey = "registry." + deploymentName;
    return getClusterVerticleRegistry()
      .compose(map -> registryAddVerticle(map, mapKey, deploymentID, clusterID))
      .compose(registrationResult -> updateVerticleCounter(deploymentName, true).map(counterResult -> {
        registrationResult.mergeIn(counterResult);
        return registrationResult;
      }));
  }

  private Future<JsonObject> unregisterVerticle(String deploymentName, String deploymentID, String clusterID) {
    String mapKey = "registry." + deploymentName;
    return getClusterVerticleRegistry()
      .compose(map -> registryRemoveVerticle(map, mapKey, deploymentID, clusterID))
      .compose(unregistrationResult -> updateVerticleCounter(deploymentName, false).map(counterResult -> {
        unregistrationResult.mergeIn(counterResult);
        return unregistrationResult;
      }));
  }

  private Future<AsyncMap<String, JsonObject>> getClusterVerticleRegistry() {
    Promise<AsyncMap<String, JsonObject>> promise = Promise.promise();
    vertx.sharedData().<String, JsonObject>getAsyncMap("registry", promise);
    return promise.future();
  }

  private Future<AsyncMap<String, Integer>> getClusterVerticleCounter() {
    Promise<AsyncMap<String, Integer>> promise = Promise.promise();
    vertx.sharedData().<String, Integer>getAsyncMap("verticleCounter", promise);
    return promise.future();
  }

  private Future<JsonObject> updateVerticleCounter(String deploymentName, boolean increment) {
    return getClusterVerticleCounter().compose(counterMap -> {
      Promise<JsonObject> promise = Promise.promise();
      counterMap.get(deploymentName, getRes -> {
        if (getRes.succeeded()) {
          Integer currentCount = getRes.result();
          AtomicInteger newCount = new AtomicInteger((currentCount == null ? 0 : currentCount) + (increment ? 1 : -1));

          if (newCount.get() < 0) {
            newCount.set(0);
          }

          counterMap.put(deploymentName, newCount.get(), putRes -> {
            if (putRes.succeeded()) {
              promise.complete(new JsonObject().put("deploymentName", deploymentName).put("newCount", newCount.get()));
            } else {
              promise.fail(putRes.cause());
            }
          });
        } else {
          promise.fail(getRes.cause());
        }
      });
      return promise.future();
    });
  }

  private Future<JsonObject> registryAddVerticle(AsyncMap<String, JsonObject> map, String mapKey, String deploymentID, String clusterID) {
    Promise<JsonObject> promise = Promise.promise();
    map.get(mapKey, getRes -> {
      if (getRes.succeeded()) {
        JsonObject clusterMap = getRes.result();
        if (clusterMap == null) {
          clusterMap = new JsonObject();
        }

        JsonArray deploymentIDs = clusterMap.getJsonArray(clusterID);
        if (deploymentIDs == null) {
          deploymentIDs = new JsonArray();
        }
        deploymentIDs.add(deploymentID);
        clusterMap.put(clusterID, deploymentIDs);

        map.put(mapKey, clusterMap, putRes -> {
          if (putRes.succeeded()) {
            JsonObject result = new JsonObject().put("deploymentName", mapKey).put("deploymentID", deploymentID);
            promise.complete(result);
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

  private Future<JsonObject> registryRemoveVerticle(AsyncMap<String, JsonObject> map, String mapKey, String deploymentID, String clusterID) {
    Promise<JsonObject> promise = Promise.promise();
    map.get(mapKey, getRes -> {
      if (getRes.succeeded()) {
        JsonObject clusterMap = getRes.result();
        if (clusterMap != null) {
          JsonArray deploymentIDs = clusterMap.getJsonArray(clusterID);
          if (deploymentIDs != null) {
            deploymentIDs.remove(deploymentID);
            if (deploymentIDs.isEmpty()) {
              clusterMap.remove(clusterID);
            } else {
              clusterMap.put(clusterID, deploymentIDs);
            }
            map.put(mapKey, clusterMap, putRes -> {
              if (putRes.succeeded()) {
                JsonObject result = new JsonObject().put("deploymentName", mapKey).put("deploymentID", deploymentID);
                promise.complete(result);
              } else {
                promise.fail(putRes.cause());
              }
            });
          } else {
            promise.complete();
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

  private Future<JsonArray> getRegisteredVerticles(String verticleName, String clusterID) {
    Promise<JsonArray> promise = Promise.promise();
    vertx.eventBus().request("cluster.registration", new JsonObject()
      .put("action", "getRegisteredVerticles")
      .put("deploymentName", verticleName)
      .put("clusterID", clusterID), ar -> {
      if (ar.succeeded()) {
        JsonObject result = (JsonObject) ar.result().body();
        JsonArray deploymentIDs = result.getJsonArray("deploymentIDs");
        promise.complete(deploymentIDs);
      } else {
        promise.fail(ar.cause());
      }
    });
    return promise.future();
  }

  private void logClusterVerticleRegistry() {
    getClusterVerticleRegistry()
      .onSuccess(map -> {
        map.entries(entriesRes -> {
          if (entriesRes.succeeded()) {
            JsonObject prettyPrintedResult = new JsonObject();
            entriesRes.result().forEach(prettyPrintedResult::put);
            logger.info("Cluster Registry: \n{}.", prettyPrintedResult.encodePrettily());
          } else {
            logger.error("Failed to retrieve cluster registry entries", entriesRes.cause());
          }
        });
      })
      .onFailure(cause -> logger.error("Failed to retrieve cluster registry", cause));
  }
}
