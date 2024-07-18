package com.example.cluster_project.services;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

@ProxyGen
@VertxGen
public interface ClusterRegistrationService {

  static ClusterRegistrationService create(Vertx vertx) {
    return new ClusterRegistrationServiceImpl(vertx);
  }

  static ClusterRegistrationService createProxy(Vertx vertx, String address) {
    return new ClusterRegistrationServiceVertxEBProxy(vertx, address);
  }

  Future<Void> deployVerticle(String deploymentName, DeploymentOptions options);

  Future<Void> undeployVerticle(String deploymentID);

  Future<Void> registerVerticle(String deploymentName, String deploymentID);

  Future<Void> unregisterVerticle(String deploymentName, String deploymentID);

  Future<JsonObject> getRegistry();

  Future<JsonObject> getVerticles(String deploymentName, String clusterID);
}
