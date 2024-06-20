package com.example.cluster_project.services;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.AsyncMap;

public class ClusterRegistrationServiceImpl implements ClusterRegistrationService {
  private final Vertx vertx;

  public ClusterRegistrationServiceImpl(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public void registerVerticle(String clusterNodeID, String nodeDeploymentID, Handler<AsyncResult<Void>> resultHandler) {
    vertx.sharedData().<String, AsyncMap<String, Long>>getClusterWideMap("activeNodes", res -> {
      if (res.succeeded()) {
        AsyncMap<String, AsyncMap<String, Long>> clusterMap = res.result();
        clusterMap.get(clusterNodeID, ar -> {
          if (ar.succeeded()) {
            AsyncMap<String, Long> verticleMap = ar.result();
//            if (verticleMap == null) {
//              vertx.sharedData().<String, Long>getClusterWideMap("verticles-" + clusterNodeID, ar2 -> {
//                if (ar2.succeeded()) {
//                  verticleMap = ar2.result();
//                  clusterMap.put(clusterNodeID, verticleMap, ar3 -> {
//                    if (ar3.succeeded()) {
//                      verticleMap.put(nodeDeploymentID, System.currentTimeMillis(), resultHandler);
//                    } else {
//                      resultHandler.handle(Future.failedFuture(ar3.cause()));
//                    }
//                  });
//                } else {
//                  resultHandler.handle(Future.failedFuture(ar2.cause()));
//                }
//              });
//            } else {
//              verticleMap.put(nodeDeploymentID, System.currentTimeMillis(), resultHandler);
//            }
          } else {
            resultHandler.handle(Future.failedFuture(ar.cause()));
          }
        });
      } else {
        resultHandler.handle(Future.failedFuture(res.cause()));
      }
    });
  }

  @Override
  public void unregisterVerticle(String clusterNodeID, String nodeDeploymentID, Handler<AsyncResult<Void>> resultHandler) {
    vertx.sharedData().<String, AsyncMap<String, Long>>getClusterWideMap("activeNodes", res -> {
      if (res.succeeded()) {
        AsyncMap<String, AsyncMap<String, Long>> clusterMap = res.result();
        clusterMap.get(clusterNodeID, ar -> {
          if (ar.succeeded()) {
            AsyncMap<String, Long> verticleMap = ar.result();
//            if (verticleMap != null) {
//              verticleMap.remove(nodeDeploymentID, resultHandler);
//            } else {
//              resultHandler.handle(Future.succeededFuture());
//            }
          } else {
            resultHandler.handle(Future.failedFuture(ar.cause()));
          }
        });
      } else {
        resultHandler.handle(Future.failedFuture(res.cause()));
      }
    });
  }
}
