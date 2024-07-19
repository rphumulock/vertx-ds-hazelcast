package com.example.cluster_project.verticles;

import com.example.cluster_project.services.ClusterRegistrationService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class HeatSensor extends AbstractVerticle {

  private final Logger logger = LoggerFactory.getLogger(HeatSensor.class);

  private ClusterRegistrationService proxy;

  private long timerID;
  private final Random random = new Random();
  private double temperature = 21.0;

  @Override
  public void start() {
    logger.debug("Starting HeatSensor: {}", getClass().getName());
    proxy = ClusterRegistrationService.createProxy(vertx, "cluster.registration");
    setupConsumers();
  }

  @Override
  public void stop() {
    stopUpdates();
  }

  public Future<Void> setupConsumers() {
    Promise<Void> promise = Promise.promise();

    String clusterID = config().getString("clusterID");
    String deploymentID = deploymentID();

    MessageConsumer<JsonObject> updatesConsumer = vertx.eventBus().consumer("HeatSensor." + clusterID + "." + deploymentID);
    handleUpdatesConsumer(updatesConsumer, clusterID, deploymentID);

    promise.complete();
    return promise.future();
  }

  public void handleUpdatesConsumer(MessageConsumer<JsonObject> consumer, String clusterIDs, String deploymentIDs) {
    consumer.handler(message -> {
      String action = message.headers().get("action");
      JsonObject body = message.body();
      String clusterID = body.getString("clusterID");
      String deploymentID = body.getString("deploymentID");

      JsonObject reply = new JsonObject();

      switch (action) {
        case "startUpdates":
          this.startUpdates();
          proxy.registerActivated(HeatSensor.class.getName(), deploymentID)
            .onSuccess(v -> {
              logger.info("Updates started for [{}] deployed on [{}].", clusterID, deploymentID);
              reply.put("status", "success").put("deploymentID", deploymentID);
              message.reply(reply);
            })
            .onFailure(err -> {
              logger.error(err.getMessage());
              reply.put("status", "failure").put("message", err.getMessage());
              message.reply(reply);
            });
          break;
        case "stopUpdates":
          this.stopUpdates();
          proxy.unregisterActivated(HeatSensor.class.getName(), deploymentID)
            .onSuccess(v -> {
              logger.info("Updates stopped for [{}] deployed on [{}].", clusterID, deploymentID);
              reply.put("status", "success").put("deploymentID", deploymentID);
              message.reply(reply);
            })
            .onFailure(err -> {
              logger.error(err.getMessage());
              reply.put("status", "failure").put("message", err.getMessage());
              message.reply(reply);
            });
          break;
        default:
          reply.put("status", "failure").put("message", "Unknown action");
          message.reply(reply);
          break;
      }
    });

    consumer.completionHandler(res -> {
      if (res.succeeded()) {
        logger.info("HeatSensor.{}.{} completionHandler succeeded", clusterIDs, deploymentIDs);
      } else {
        logger.info("HeatSensor completionHandler failed", res.cause());
      }
    });

    consumer.endHandler(res -> {
      logger.info("HeatSensor endHandler");
    });

    consumer.exceptionHandler(res -> {
      logger.error("HeatSensor exceptionHandler");
    });
  }

  private void startUpdates() {
    try {
      this.timerID = vertx.setTimer(random.nextInt(2000), this::update);
    } catch (Exception e) {
      logger.error("Failed to start HeatSensor timer", e);
    }
  }

  public void stopUpdates() {
    vertx.cancelTimer(this.timerID);
  }

  private void update(long timerId) {
    temperature = temperature + (delta() / 10);
    String clusterID = config().getString("clusterID");
    String deploymentID = deploymentID();

    JsonObject message = new JsonObject()
      .put("clusterID", clusterID)
      .put("deploymentID", deploymentID)
      .put("temperature", temperature);

    DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("action", "consumeUpdates");

//    logger.debug("Heat Sensor Data from: {}.", message.toString());

    vertx.eventBus().publish("cluster.HeatSensors", message, deliveryOptions);
    startUpdates();
  }

  private double delta() {
    if (random.nextInt() > 0) {
      return random.nextGaussian();
    } else {
      return -random.nextGaussian();
    }
  }

}

