package com.example.cluster_project;

import com.example.cluster_project.services.ClusterRegistrationService;
import com.example.cluster_project.services.ClusterRegistrationServiceImpl;
import com.example.cluster_project.utils.MessageWrapper;
import com.example.cluster_project.verticles.HTTPServer;

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ServiceBinder;

import com.hazelcast.config.Config;

import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MainVerticle extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

  private ClusterRegistrationService proxy;
  private final List<MessageConsumer<JsonObject>> consumers = new ArrayList<>();

  public static void main(String[] args) {
    Config hazelcastConfig = Config.load();
    hazelcastConfig.getCPSubsystemConfig()
      .setCPMemberCount(3);
    HazelcastClusterManager mgr = new HazelcastClusterManager(hazelcastConfig);
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
  public void start(Promise<Void> startPromise) {
    // Set default port to 8080
    int defaultPort = 8080;

    // Retrieve port from environment variable or use default if not present
    String portEnv = System.getenv("PORT");
    int port = portEnv != null ? Integer.parseInt(portEnv) : defaultPort;

    JsonObject config = new JsonObject()
      .put("clusterID", deploymentID())
      .put("http.port", port);
    DeploymentOptions options = new DeploymentOptions().setConfig(config);

    ClusterRegistrationService service = new ClusterRegistrationServiceImpl(vertx);
    new ServiceBinder(vertx)
      .setAddress("cluster.registration")
      .register(ClusterRegistrationService.class, service);

    proxy = ClusterRegistrationService.createProxy(vertx, "cluster.registration");
    proxy.registerVerticle(deploymentID(), MainVerticle.class.getName(), deploymentID())
      .compose(v -> proxy.deployVerticle(deploymentID(), HTTPServer.class.getName(), options))
      .compose(v -> setupConsumers())
      .onSuccess(v -> {
        logger.info("All verticles deployed successfully.");
        startPromise.complete();
      })
      .onFailure(cause -> {
        logger.error("Failed to deploy verticles", cause);
        startPromise.fail(cause);
      });
  }

  @Override
  public void stop(Promise<Void> stopPromise) throws Exception {
    Future.all(consumers.stream()
        .map(MessageConsumer::unregister)
        .collect(Collectors.toList()))
      .compose(v -> Future.all(vertx.deploymentIDs().stream()
        .map(id -> {
          Promise<Void> promise = Promise.promise();
          vertx.undeploy(id, promise);
          return promise.future();
        })
        .collect(Collectors.toList())))
      .onComplete(ar -> {
        if (ar.succeeded()) {
          logger.info("All consumers unregistered and verticles undeployed successfully");
          stopPromise.complete();
        } else {
          logger.error("Failed to stop verticle cleanly", ar.cause());
          stopPromise.fail(ar.cause());
        }
      });
  }

  public Future<Void> setupConsumers() {
    Promise<Void> promise = Promise.promise();

    MessageConsumer<JsonObject> deployConsumer = vertx.eventBus().consumer("deploy-heat-sensor." + deploymentID());
    handleDeployConsumer(deployConsumer);
    consumers.add(deployConsumer);

    MessageConsumer<JsonObject> undeployConsumer = vertx.eventBus().consumer("undeploy-heat-sensor." + deploymentID());
    handleDeployConsumer(undeployConsumer);
    consumers.add(undeployConsumer);

    promise.complete();
    return promise.future();
  }

  public void handleDeployConsumer(MessageConsumer<JsonObject> consumer) {
    consumer.handler(message -> {
      String action = message.headers().get("action");
      switch (action) {
        case "deploy":
          deployVerticle(message);
          break;
        case "undeploy":
          logger.info("switch.undeploy {}", deploymentID());
          undeployVerticle(message);
          break;
      }
    });
    consumer.completionHandler(res -> {
      if (res.succeeded()) {
        logger.info("deploy-heat-sensor.{} consumer completionHandler succeeded.", deploymentID());
      } else {
        logger.info("deploy-heat-sensor.{} consumer completionHandler failed.", deploymentID());
      }
    });
    consumer.exceptionHandler(res -> logger.info("deploy-heat-sensor.{} consumer exceptionHandler succeeded.", deploymentID()));
    consumer.endHandler(res -> logger.info("deploy-heat-sensor.{} consumer endHandler failed.", deploymentID()));
  }

  public void deployVerticle(Message<JsonObject> message) {
    Buffer buffer = (Buffer) message.body();
    MessageWrapper wrapper = new MessageWrapper();
    wrapper.readFromBuffer(0, buffer);
    JsonObject body = wrapper.getMessage();
    DeploymentOptions options = wrapper.getDeploymentOptions();
    String clusterID = body.getString("clusterID");
    String deploymentName = body.getString("deploymentName");

    proxy.deployVerticle(clusterID, deploymentName, options)
      .onSuccess(deploymentID -> {
        JsonObject reply = new JsonObject()
          .put("status", "success")
          .put("deploymentID", deploymentID);
        message.reply(reply);
      })
      .onFailure(err -> {
        JsonObject reply = new JsonObject()
          .put("status", "failure")
          .put("message", err.getMessage());
        message.reply(reply);
      });
  }

  public void undeployVerticle(Message<JsonObject> message) {
    JsonObject body = message.body();
    String deploymentID = body.getString("deploymentID");
    String deploymentName = body.getString("deploymentName");

    logger.info("success undeployVerticle {} {}", deploymentID, deploymentName);

    proxy.undeployVerticle(deploymentID(), deploymentName, deploymentID)
      .onSuccess(undeploymentID -> {
        logger.info("success undeployVerticle  undeploymentID {} {}", deploymentID, deploymentName);

        JsonObject reply = new JsonObject()
          .put("status", "success")
          .put("undeploymentID", undeploymentID);
        message.reply(reply);
      })
      .onFailure(err -> {
        JsonObject reply = new JsonObject()
          .put("status", "failure")
          .put("message", err.getMessage());
        message.reply(reply);
      });
  }
}
