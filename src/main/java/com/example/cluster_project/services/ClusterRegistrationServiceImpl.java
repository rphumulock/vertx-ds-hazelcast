package com.example.cluster_project.services;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
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
    String lockName = deploymentName + "." + clusterID;

    vertx.sharedData().getLockWithTimeout(lockName, 10000)
      .compose(lock ->
        vertx.deployVerticle(deploymentName, options)
          .compose(deploymentID ->
            registerVerticle(clusterID, deploymentName, deploymentID)
              .compose(jsonArray -> logDeployment(clusterID, deploymentName, deploymentID, jsonArray))
              .map(v -> deploymentID)
          )
          .onComplete(ar -> lock.release())
      )
      .onSuccess(promise::complete)
      .onFailure(promise::fail);

    return promise.future();
  }

  @Override
  public Future<JsonArray> registerVerticle(String clusterID, String deploymentName, String deploymentID) {
    Promise<JsonArray> promise = Promise.promise();

    String key = clusterID + "." + deploymentName;

    vertx.sharedData().<String, JsonArray>getAsyncMap(key)
      .compose(map ->
        map.get(clusterID).compose(fetchedJsonArray -> {
          JsonArray jsonArray = fetchedJsonArray != null ? fetchedJsonArray : new JsonArray();
          jsonArray.add(deploymentID);
          return map.put(clusterID, jsonArray).map(v -> jsonArray);
        })
      )
      .onSuccess(promise::complete)
      .onFailure(promise::fail);

    return promise.future();
  }

  @Override
  public Future<Void> logDeployment(String clusterID, String deploymentName, String deploymentID, JsonArray jsonArray) {
    Promise<Void> promise = Promise.promise();

    logger.info("Verticle deployed and registered: deploymentName={}, deploymentID={}, clusterID={}, currentRegistry={}",
      deploymentName, deploymentID, clusterID, jsonArray.encode());
    promise.complete();

    return promise.future();
  }

}
