package com.example.cluster_project;

import com.example.cluster_project.verticles.HTTPServer;

import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

  public static void main(String[] args) {
    HazelcastClusterManager mgr = new HazelcastClusterManager();
    VertxOptions options = new VertxOptions().setClusterManager(mgr);

    Vertx.clusteredVertx(options, res -> {
      if (res.succeeded()) {
        Vertx vertx = res.result();
        vertx.deployVerticle(new MainVerticle(), deployRes -> {
          if (deployRes.succeeded()) {
            logger.info("MainVerticle deployed successfully");
          } else {
            logger.error("Failed to deploy MainVerticle", deployRes.cause());
          }
        });
      } else {
        logger.error("Failed to start clustered Vert.x instance", res.cause());
      }
    });
  }

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    JsonObject config = new JsonObject();
    config.put("deploymentID", deploymentID());
    deployHTTPServerVerticle(config).onComplete(res -> {
      if (res.succeeded()) {
        startPromise.complete();
        logger.info("All verticles deployed successfully");
      } else {
        startPromise.fail(res.cause());
        logger.error("Failed to deploy verticles", res.cause());
      }
    });
  }

  private Future<String> deployHTTPServerVerticle(JsonObject config) {
    Promise<String> promise = Promise.promise();
    DeploymentOptions options = new DeploymentOptions().setConfig(config);
    vertx.deployVerticle(new HTTPServer(), options, promise);
    return promise.future();
  }
}
