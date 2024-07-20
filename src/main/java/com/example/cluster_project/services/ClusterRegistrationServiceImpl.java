package com.example.cluster_project.services;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusterRegistrationServiceImpl implements ClusterRegistrationService {

  private static final Logger logger = LoggerFactory.getLogger(ClusterRegistrationServiceImpl.class);
  private final Vertx vertx;

  public ClusterRegistrationServiceImpl(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public Future<String> deployVerticle(String clusterID, String deploymentName, DeploymentOptions options) {
    Promise<String> promise = Promise.promise();

    vertx.deployVerticle(deploymentName, options)
      .compose(deploymentID -> registerVerticle(clusterID, deploymentName, deploymentID).map(v -> deploymentID))
      .onSuccess(promise::complete)
      .onFailure(promise::fail);

    return promise.future();
  }

  @Override
  public Future<String> undeployVerticle(String clusterID, String deploymentName, String deploymentID) {
    Promise<String> promise = Promise.promise();

    vertx.undeploy(deploymentID)
      .compose(v -> unregisterVerticle(clusterID, deploymentName, deploymentID))
      .onSuccess(promise::complete)
      .onFailure(promise::fail);

    return promise.future();
  }

  @Override
  public Future<JsonArray> registerVerticle(String clusterID, String deploymentName, String deploymentID) {
    Promise<JsonArray> promise = Promise.promise();

    vertx.sharedData().getLockWithTimeout(deploymentName, 10000)
      .compose(lock ->
        vertx.sharedData().<String, JsonArray>getAsyncMap(deploymentName)
          .compose(map ->
            map.get(deploymentName).compose(fetchedJsonArray -> {
              JsonArray jsonArray = fetchedJsonArray != null ? fetchedJsonArray : new JsonArray();
              JsonObject jsonObject = new JsonObject()
                .put("clusterID", clusterID)
                .put("deploymentID", deploymentID);
              jsonArray.add(jsonObject);
              logDeployment(clusterID, deploymentName, deploymentID, jsonArray);
              return map.put(deploymentName, jsonArray).map(v -> jsonArray);
            })
          )
          .onComplete(ar -> {
            lock.release();
            logger.info("Lock {} released", deploymentName);
          })
      )
      .onSuccess(promise::complete)
      .onFailure(promise::fail);

    return promise.future();
  }

  @Override
  public Future<String> unregisterVerticle(String clusterID, String deploymentName, String deploymentID) {
    Promise<String> promise = Promise.promise();

    vertx.sharedData().getLockWithTimeout(deploymentName, 10000)
      .compose(lock ->
        vertx.sharedData().<String, JsonArray>getAsyncMap(deploymentName)
          .compose(map -> map.get(deploymentName)
            .compose(fetchedJsonArray -> {
              if (fetchedJsonArray != null) {
                JsonArray updatedJsonArray = new JsonArray();
                for (int i = 0; i < fetchedJsonArray.size(); i++) {
                  JsonObject obj = fetchedJsonArray.getJsonObject(i);
                  if (!obj.getString("deploymentID").equals(deploymentID)) {
                    updatedJsonArray.add(obj);
                  }
                }
                return map.put(deploymentName, updatedJsonArray)
                  .map(v -> {
                    logUndeployment(clusterID, deploymentName, deploymentID, updatedJsonArray);
                    return deploymentID;
                  });
              } else {
                return Future.succeededFuture(deploymentID);
              }
            })
          )
          .onComplete(ar -> lock.release())
          .onFailure(promise::fail)
      )
      .onSuccess(promise::complete)
      .onFailure(promise::fail);

    return promise.future();
  }

  @Override
  public Future<JsonArray> getRegistry(String deploymentName) {
    Promise<JsonArray> promise = Promise.promise();

    vertx.sharedData().<String, JsonArray>getAsyncMap(deploymentName)
      .compose(map -> map.get(deploymentName).compose(fetchedJsonArray -> {
        JsonArray jsonArray = fetchedJsonArray != null ? fetchedJsonArray : new JsonArray();
        return Future.succeededFuture(jsonArray);
      }))
      .onSuccess(promise::complete)
      .onFailure(promise::fail);

    return promise.future();
  }

  @Override
  public Future<JsonArray> registerActivated(String deploymentName, String deploymentID) {
    Promise<JsonArray> promise = Promise.promise();
    String lockName = "active." + deploymentName;
    String key = "active." + deploymentName;

    vertx.sharedData().getLockWithTimeout(lockName, 10000)
      .compose(lock ->
        vertx.sharedData().<String, JsonArray>getAsyncMap(key)
          .compose(map ->
            map.get(key).compose(fetchedJsonArray -> {
              JsonArray jsonArray = fetchedJsonArray != null ? fetchedJsonArray : new JsonArray();
              jsonArray.add(deploymentID);
              logActivated(key, jsonArray);
              return map.put(key, jsonArray).map(v -> jsonArray);
            })
          )
          .onComplete(ar -> lock.release())
      )
      .onSuccess(promise::complete)
      .onFailure(promise::fail);

    return promise.future();
  }

  @Override
  public Future<JsonArray> unregisterActivated(String deploymentName, String deploymentID) {
    Promise<JsonArray> promise = Promise.promise();
    String lockName = "active." + deploymentName;
    String key = "active." + deploymentName;

    vertx.sharedData().getLockWithTimeout(lockName, 10000)
      .compose(lock ->
        vertx.sharedData().<String, JsonArray>getAsyncMap(key)
          .compose(map ->
            map.get(key).compose(fetchedJsonArray -> {
              JsonArray jsonArray = fetchedJsonArray != null ? fetchedJsonArray : new JsonArray();
              jsonArray.remove(deploymentID);
              logActivated(key, jsonArray); // You might want to change this method name to something like logUnactivated
              return map.put(key, jsonArray).map(v -> jsonArray);
            })
          )
          .onComplete(ar -> lock.release())
      )
      .onSuccess(promise::complete)
      .onFailure(promise::fail);

    return promise.future();
  }

  @Override
  public Future<Void> logDeployment(String clusterID, String deploymentName, String deploymentID, JsonArray jsonArray) {
    Promise<Void> promise = Promise.promise();

    logger.info("Verticle deployed and registered: deploymentName={}, currentRegistry={}",
      deploymentName, jsonArray.encode());
    promise.complete();

    return promise.future();
  }

  @Override
  public Future<Void> logUndeployment(String clusterID, String deploymentName, String deploymentID, JsonArray jsonArray) {
    Promise<Void> promise = Promise.promise();

    logger.info("Verticle undeployed and unregistered: deploymentName={}, currentRegistry={}",
      deploymentName, jsonArray.encode());
    promise.complete();

    return promise.future();
  }

  @Override
  public Future<Void> logActivated(String deploymentName, JsonArray jsonArray) {
    Promise<Void> promise = Promise.promise();

    logger.info("Verticle activated: deploymentName={}, currentRegistry={}",
      deploymentName, jsonArray.encode());
    promise.complete();

    return promise.future();
  }

}
