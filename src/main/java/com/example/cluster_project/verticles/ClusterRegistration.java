package com.example.cluster_project.verticles;

import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ClusterRegistration extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(ClusterRegistration.class);

  @Override
  public void start(Promise<Void> startPromise) {
    setupConsumer();
    startPromise.complete();
  }

  private void setupConsumer() {
    String thisClusterNodeID = config().getString("clusterNodeID");
    vertx.eventBus().consumer("cluster.registration", message -> {
      JsonObject body = (JsonObject) message.body();
      String action = body.getString("action");
      String clusterNodeID = body.getString("clusterNodeID");
      String deploymentName = body.getString("deploymentName");
      String deploymentID = body.getString("deploymentID");


      DeploymentOptions options = new DeploymentOptions(body.getJsonObject("options", new JsonObject()));

      switch (action) {
        case "deploy":
          if (clusterNodeID.equals(thisClusterNodeID)) {
            handleDeploy(message, deploymentName, options)
              .onSuccess(v -> logClusterVerticleRegistry())
              .onFailure(cause -> logger.error("Failed to deploy verticle: {}", deploymentName, cause));
          }
          break;
        case "undeploy":
          if (clusterNodeID.equals(thisClusterNodeID)) {
            handleUndeploy(message, deploymentID, deploymentName)
              .onSuccess(v -> logClusterVerticleRegistry())
              .onFailure(cause -> logger.error("Failed to undeploy verticle: {}", deploymentName, cause));
          }
          break;
        case "register":
          handleRegister(message, deploymentName, deploymentID)
            .onSuccess(v -> logClusterVerticleRegistry())
            .onFailure(cause -> logger.error("Failed to register verticle: {}", deploymentName, cause));
          break;
        case "unregister":
          handleUnregister(message, deploymentName, deploymentID)
            .onSuccess(v -> logClusterVerticleRegistry())
            .onFailure(cause -> logger.error("Failed to unregister verticle: {}", deploymentName, cause));
          break;
        case "getRegisteredVerticles":
          handleGetRegisteredVerticles(message, deploymentName);
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

  private Future<Void> handleDeploy(Message<Object> message, String deploymentName, DeploymentOptions options) {
    Promise<Void> promise = Promise.promise();
    deployAndRegisterVerticle(deploymentName, options).onComplete(ar -> {
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

  private Future<Void> handleUndeploy(Message<Object> message, String deploymentID, String deploymentName) {
    Promise<Void> promise = Promise.promise();
    undeployAndUnregisterVerticle(deploymentID, deploymentName).onComplete(ar -> {
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

  private Future<Void> handleRegister(Message<Object> message, String deploymentName, String deploymentID) {
    Promise<Void> promise = Promise.promise();
    registerVerticle(deploymentName, deploymentID)
      .onSuccess(result -> {
        logger.info("Successfully registered verticle: {}", deploymentName);
        message.reply(new JsonObject().put("deploymentID", result));
        message.reply("Registered successfully.");
      })
      .onFailure(cause -> {
        logger.error("Failed to register verticle: {}", deploymentName, cause);
        message.fail(500, cause.getMessage());
      });
    return promise.future();
  }

  private Future<Void> handleUnregister(Message<Object> message, String deploymentName, String deploymentID) {
    Promise<Void> promise = Promise.promise();
    unregisterVerticle(deploymentName, deploymentID)
      .onSuccess(result -> {
        logger.info("Successfully unregistered verticle: {}", deploymentName);
        message.reply("Unregistered successfully.");
      })
      .onFailure(cause -> {
        logger.error("Failed to unregister verticle: {}", deploymentName, cause);
        message.fail(500, cause.getMessage());
      });
    return promise.future();
  }

  private void handleGetRegisteredVerticles(Message<Object> message, String deploymentName) {
    getRegisteredVerticles(deploymentName).onComplete(ar -> {
      if (ar.succeeded()) {
        message.reply(new JsonObject().put("deploymentIDs", ar.result()));
      } else {
        message.fail(500, ar.cause().getMessage());
      }
    });
  }


  private Future<JsonObject> deployAndRegisterVerticle(String deploymentName, DeploymentOptions options) {
    Promise<JsonObject> promise = Promise.promise();
    vertx.deployVerticle(deploymentName, options, res -> {
      if (res.succeeded()) {
        String deploymentID = res.result();
        registerVerticle(deploymentName, deploymentID).onComplete(ar -> {
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

  private Future<JsonObject> undeployAndUnregisterVerticle(String deploymentID, String deploymentName) {
    Promise<JsonObject> promise = Promise.promise();
    vertx.undeploy(deploymentID, res -> {
      if (res.succeeded()) {
        unregisterVerticle(deploymentName, deploymentID).onComplete(ar -> {
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

  private Future<JsonObject> registerVerticle(String deploymentName, String deploymentID) {
    String mapKey = "registry." + deploymentName;
    return getClusterVerticleRegistry()
      .compose(map -> registryAddVerticle(map, mapKey, deploymentID))
      .compose(registrationResult -> updateVerticleCounter(deploymentName, true).map(counterResult -> {
        registrationResult.mergeIn(counterResult);
        return registrationResult;
      }));
  }

  private Future<JsonObject> unregisterVerticle(String deploymentName, String deploymentID) {
    String mapKey = "registry." + deploymentName;
    return getClusterVerticleRegistry()
      .compose(map -> registryRemoveVerticle(map, mapKey, deploymentID))
      .compose(unregistrationResult -> updateVerticleCounter(deploymentName, false).map(counterResult -> {
        unregistrationResult.mergeIn(counterResult);
        return unregistrationResult;
      }));
  }

  private Future<AsyncMap<String, ArrayList<String>>> getClusterVerticleRegistry() {
    Promise<AsyncMap<String, ArrayList<String>>> promise = Promise.promise();
    vertx.sharedData().<String, ArrayList<String>>getAsyncMap("registry", promise);
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

  private Future<JsonObject> registryAddVerticle(AsyncMap<String, ArrayList<String>> map, String mapKey, String deploymentID) {
    Promise<JsonObject> promise = Promise.promise();
    map.get(mapKey, getRes -> {
      if (getRes.succeeded()) {
        ArrayList<String> deploymentIDs = getRes.result();
        if (deploymentIDs == null) {
          deploymentIDs = new ArrayList<>();
        }
        deploymentIDs.add(deploymentID);
        map.put(mapKey, deploymentIDs, putRes -> {
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

  private Future<JsonObject> registryRemoveVerticle(AsyncMap<String, ArrayList<String>> map, String mapKey, String deploymentID) {
    Promise<JsonObject> promise = Promise.promise();
    map.get(mapKey, getRes -> {
      if (getRes.succeeded()) {
        ArrayList<String> deploymentIds = getRes.result();
        if (deploymentIds != null) {
          deploymentIds.remove(deploymentID);
          if (deploymentIds.isEmpty()) {
            map.remove(mapKey, removeRes -> {
              if (removeRes.succeeded()) {
                JsonObject result = new JsonObject().put("deploymentName", mapKey).put("deploymentID", deploymentID);
                promise.complete(result);
              } else {
                promise.fail(removeRes.cause());
              }
            });
          } else {
            map.put(mapKey, deploymentIds, putRes -> {
              if (putRes.succeeded()) {
                JsonObject result = new JsonObject().put("deploymentName", mapKey).put("deploymentID", deploymentID);
                promise.complete(result);
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

  private Future<List<String>> getRegisteredVerticles(String verticleName) {
    Promise<List<String>> promise = Promise.promise();
    getClusterVerticleRegistry().onComplete(ar -> {
      if (ar.succeeded()) {
        AsyncMap<String, ArrayList<String>> registryMap = ar.result();
        registryMap.get("registry." + verticleName, res -> {
          if (res.succeeded()) {
            ArrayList<String> deploymentIds = res.result();
            if (deploymentIds != null) {
              promise.complete(deploymentIds);
            } else {
              promise.complete(new ArrayList<>());
            }
          } else {
            promise.fail(res.cause());
          }
        });
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
