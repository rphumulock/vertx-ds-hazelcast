package com.example.cluster_project.services;

import com.example.cluster_project.services.ClusterRegistrationService;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

public class ClusterRegistrationServiceImpl implements ClusterRegistrationService {

  private static final Logger logger = LoggerFactory.getLogger(ClusterRegistrationServiceImpl.class);
  private final Vertx vertx;

  // Method to create the proxy.
  static SomeDatabaseService createProxy(Vertx vertx, String address) {
    return new SomeDatabaseServiceVertxEBProxy(vertx, address);
  }


  public ClusterRegistrationServiceImpl(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public Future<Void> deployVerticle(String deploymentName, DeploymentOptions options) {
    Promise<Void> promise = Promise.promise();
    String lockName = "deploy-lock-" + deploymentName;
    vertx.sharedData().getLockWithTimeout(lockName, 10000, lockRes -> {
      if (lockRes.succeeded()) {
        Lock lock = lockRes.result();
        vertx.deployVerticle(deploymentName, new DeploymentOptions(options), res -> {
          lock.release();
          if (res.succeeded()) {
            String deploymentID = res.result();
            registerVerticle(deploymentName, deploymentID).onComplete(ar -> {
              if (ar.succeeded()) {
                promise.complete();
              } else {
                promise.fail(ar.cause());
              }
            });
          } else {
            promise.fail(res.cause());
          }
        });
      } else {
        promise.fail(lockRes.cause());
      }
    });
    return promise.future();
  }

  @Override
  public Future<Void> undeployVerticle(String deploymentID) {
    Promise<Void> promise = Promise.promise();
    String lockName = "undeploy-lock-" + deploymentID;
    vertx.sharedData().getLockWithTimeout(lockName, 10000, lockRes -> {
      if (lockRes.succeeded()) {
        Lock lock = lockRes.result();
        vertx.undeploy(deploymentID, res -> {
          lock.release();
          if (res.succeeded()) {
            unregisterVerticle("", deploymentID).onComplete(ar -> {
              if (ar.succeeded()) {
                promise.complete();
              } else {
                promise.fail(ar.cause());
              }
            });
          } else {
            promise.fail(res.cause());
          }
        });
      } else {
        promise.fail(lockRes.cause());
      }
    });
    return promise.future();
  }

  @Override
  public Future<Void> registerVerticle(String deploymentName, String deploymentID) {
    Promise<Void> promise = Promise.promise();
    String lockName = "register-lock-" + deploymentName;
    vertx.sharedData().getLockWithTimeout(lockName, 10000, lockRes -> {
      if (lockRes.succeeded()) {
        Lock lock = lockRes.result();
        getClusterVerticleRegistry().onSuccess(map -> {
          String mapKey = "registry." + deploymentName;
          map.get(mapKey, getRes -> {
            if (getRes.succeeded()) {
              JsonObject clusterMap = getRes.result();
              if (clusterMap == null) {
                clusterMap = new JsonObject();
              }
              JsonArray deploymentIDs = clusterMap.getJsonArray(deploymentID);
              if (deploymentIDs == null) {
                deploymentIDs = new JsonArray();
              }
              deploymentIDs.add(deploymentID);
              clusterMap.put(deploymentID, deploymentIDs);

              map.put(mapKey, clusterMap, putRes -> {
                lock.release();
                if (putRes.succeeded()) {
                  promise.complete();
                } else {
                  promise.fail(putRes.cause());
                }
              });
            } else {
              lock.release();
              promise.fail(getRes.cause());
            }
          });
        }).onFailure(cause -> {
          lock.release();
          promise.fail(cause);
        });
      } else {
        promise.fail(lockRes.cause());
      }
    });
    return promise.future();
  }

  @Override
  public Future<Void> unregisterVerticle(String deploymentName, String deploymentID) {
    Promise<Void> promise = Promise.promise();
    String lockName = "unregister-lock-" + deploymentID;
    vertx.sharedData().getLockWithTimeout(lockName, 10000, lockRes -> {
      if (lockRes.succeeded()) {
        Lock lock = lockRes.result();
        getClusterVerticleRegistry().onSuccess(map -> {
          String mapKey = "registry." + deploymentName;
          map.get(mapKey, getRes -> {
            if (getRes.succeeded()) {
              JsonObject clusterMap = getRes.result();
              if (clusterMap != null) {
                JsonArray deploymentIDs = clusterMap.getJsonArray(deploymentID);
                if (deploymentIDs != null) {
                  deploymentIDs.remove(deploymentID);
                  if (deploymentIDs.isEmpty()) {
                    clusterMap.remove(deploymentID);
                  } else {
                    clusterMap.put(deploymentID, deploymentIDs);
                  }
                  map.put(mapKey, clusterMap, putRes -> {
                    lock.release();
                    if (putRes.succeeded()) {
                      promise.complete();
                    } else {
                      promise.fail(putRes.cause());
                    }
                  });
                } else {
                  lock.release();
                  promise.complete();
                }
              } else {
                lock.release();
                promise.complete();
              }
            } else {
              lock.release();
              promise.fail(getRes.cause());
            }
          });
        }).onFailure(cause -> {
          lock.release();
          promise.fail(cause);
        });
      } else {
        promise.fail(lockRes.cause());
      }
    });
    return promise.future();
  }

  @Override
  public Future<JsonObject> getRegistry() {
    return null;
  }

  @Override
  public Future<JsonObject> getVerticles(String deploymentName, String clusterID) {
    return null;
  }

  private Future<AsyncMap<String, JsonObject>> getClusterVerticleRegistry() {
    Promise<AsyncMap<String, JsonObject>> promise = Promise.promise();
    vertx.sharedData().<String, JsonObject>getAsyncMap("registry", promise);
    return promise.future();
  }
}
