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
    vertx.eventBus().consumer("cluster.registration", message -> {
      JsonObject body = (JsonObject) message.body();
      String action = body.getString("action");
      String deploymentName = body.getString("deploymentName");
      String deploymentID = body.getString("deploymentID");
      DeploymentOptions options = new DeploymentOptions(body.getJsonObject("options", new JsonObject()));

      switch (action) {
        case "deploy":
          handleDeploy(message, deploymentName, options);
          break;
        case "undeploy":
          handleUndeploy(message, deploymentID, deploymentName);
          break;
        case "register":
          handleRegister(message, deploymentName, deploymentID);
          break;
        case "unregister":
          handleUnregister(message, deploymentName, deploymentID);
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

  private void handleDeploy(Message<Object> message, String deploymentName, DeploymentOptions options) {
    deployAndRegisterVerticle(deploymentName, options).onComplete(ar -> {
      if (ar.succeeded()) {
        logger.info("deployAndRegisterVerticle {}.", ar.result());
        message.reply(ar.result());
      } else {
        message.fail(500, ar.cause().getMessage());
      }
    });
  }

  private void handleUndeploy(Message<Object> message, String deploymentID, String deploymentName) {
    undeployAndUnregisterVerticle(deploymentID, deploymentName).onComplete(ar -> {
      if (ar.succeeded()) {
        message.reply(ar.result());
      } else {
        message.fail(500, ar.cause().getMessage());
      }
    });
  }

  private void handleRegister(Message<Object> message, String deploymentName, String deploymentID) {
    registerVerticle(deploymentName, deploymentID).onComplete(ar -> {
      if (ar.succeeded()) {
        logger.info("register {}.", ar.result());
        message.reply(new JsonObject().put("deploymentID", ar.result()));
        message.reply("Registered successfully.");
      } else {
        message.fail(500, ar.cause().getMessage());
      }
    });
  }

  private void handleUnregister(Message<Object> message, String deploymentName, String deploymentID) {
    unregisterVerticle(deploymentName, deploymentID).onComplete(ar -> {
      if (ar.succeeded()) {
        message.reply("Unregistered successfully.");
      } else {
        message.fail(500, ar.cause().getMessage());
      }
    });
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
