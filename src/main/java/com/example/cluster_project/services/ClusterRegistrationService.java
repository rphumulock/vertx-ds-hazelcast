package com.example.cluster_project.services;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public interface ClusterRegistrationService {
  void registerVerticle(String clusterNodeID, String verticleID, Handler<AsyncResult<Void>> resultHandler);

  void unregisterVerticle(String clusterNodeID, String verticleID, Handler<AsyncResult<Void>> resultHandler);
}
