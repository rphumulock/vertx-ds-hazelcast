package com.example.cluster_project.services;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;

@ProxyGen
@VertxGen
public interface ClusterRegistrationService {

  static ClusterRegistrationService create(Vertx vertx) {
    return new ClusterRegistrationServiceImpl(vertx);
  }

  static ClusterRegistrationService createProxy(Vertx vertx, String address) {
    return new ClusterRegistrationServiceVertxEBProxy(vertx, address);
  }

  Future<String> deployVerticle(String clusterID, String deploymentName, DeploymentOptions options);

  Future<String> undeployVerticle(String clusterID, String deploymentName, String deploymentID);

  Future<JsonArray> registerVerticle(String clusterID, String deploymentName, String deploymentID);

  Future<String> unregisterVerticle(String clusterID, String deploymentName, String deploymentID);

  Future<JsonArray> getRegistry(String deploymentName);

  Future<JsonArray> registerActivated(String deploymentName, String deploymentID);

  Future<JsonArray> unregisterActivated(String deploymentName, String deploymentID);

  Future<Void> logDeployment(String clusterID, String deploymentName, String deploymentID, JsonArray jsonArray);

  Future<Void> logUndeployment(String clusterID, String deploymentName, String deploymentID, JsonArray jsonArray);

  Future<Void> logActivated(String deploymentID, JsonArray jsonArray);
}
