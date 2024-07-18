package com.example.cluster_project.verticles;

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

  private long timerID;
  private final Random random = new Random();
  private double temperature = 21.0;

  @Override
  public void start() {
    logger.debug("Starting HeatSensor: {}", getClass().getName());
    setupConsumer();
  }

  @Override
  public void stop() {
    stopUpdates();
  }

  private void setupConsumer() {
    String clusterID = config().getString("clusterID");
    String deploymentID = deploymentID();

    MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer("HeatSensor." + clusterID + "." + deploymentID);

    consumer.handler(message -> {
      String action = message.headers().get("action");
      JsonObject body = message.body();
      String messageClusterID = body.getString("clusterID");
      String messageDeploymentID = body.getString("messageDeploymentID");

      JsonObject reply = new JsonObject()
        .put("status", "success")
        .put("deploymentID", deploymentID);

      switch (action) {
        case "startUpdates":
          this.startUpdates();
          return proxy.activated(HeatSensor.class.getName(), startedID)
          logger.info("Updates started for [{}] deployed on [{}].", messageClusterID, messageDeploymentID);
          message.reply(reply);
          break;
        case "stopUpdates":
          this.stopUpdates();
          logger.info("Updates stopped for [{}] deployed on [{}].", messageClusterID, messageDeploymentID);
          message.reply(reply);
          break;
      }
    });

    consumer.completionHandler(res -> {
      if (res.succeeded()) {
        logger.info("The verticle.controller handler registration for Cluster Node: {} has reached all nodes.", clusterID);
      } else {
        logger.info("The verticle.controller handler registration for Cluster Node: {} has failed.", clusterID);
      }
    });

    consumer.endHandler(res -> {
      logger.info("Unregistering verticle.controller consumer for Cluster Node: {}.", clusterID);
    });

    consumer.exceptionHandler(res -> {
      logger.error("There was an exception in the verticle.controller consumer for Cluster Node: {}.", clusterID);
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

    logger.debug("Heat Sensor Data from: {}.", message.toString());

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

