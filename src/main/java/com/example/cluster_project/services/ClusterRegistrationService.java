package com.example.cluster_project.services;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;

public interface ClusterRegistrationService {

  Future<Void> registerVerticle(String type, String clusterNodeID, String verticleID);

  Future<Void> unregisterVerticle(String type, String clusterNodeID, String verticleID);
}
