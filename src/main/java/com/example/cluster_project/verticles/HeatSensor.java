package com.example.cluster_project.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.MessageConsumer;
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
    String clusterNodeID = config().getString("clusterNodeID");
    String deploymentID = deploymentID();

    MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer("HeatSensor." + clusterNodeID + "." + deploymentID);

    consumer.handler(message -> {
      JsonObject messageBody = message.body();
      String eventType = messageBody.getString("eventType");

      JsonObject payload = new JsonObject()
        .put("status", "success")
        .put("clusterNodeID", clusterNodeID)
        .put("deploymentID", deploymentID);

      switch (eventType) {
        case "startUpdates":
          this.startUpdates();
          logger.info("Updates started for [{}] deployed on [{}].", deploymentID, clusterNodeID);
          payload.put("eventType", "start.updates.HeatSensor");
          message.reply(payload);
          break;
        case "stopUpdates":
          this.stopUpdates();
          logger.info("Updates stopped for [{}] deployed on [{}].", deploymentID, clusterNodeID);
          payload.put("eventType", "stop.updates.HeatSensor");
          message.reply(payload);
          break;
      }
    });

    consumer.completionHandler(res -> {
      if (res.succeeded()) {
        logger.info("The verticle.controller handler registration for Cluster Node: {} has reached all nodes.", clusterNodeID);
      } else {
        logger.info("The verticle.controller handler registration for Cluster Node: {} has failed.", clusterNodeID);
      }
    });

    consumer.endHandler(res -> {
      logger.info("Unregistering verticle.controller consumer for Cluster Node: {}.", clusterNodeID);
    });

    consumer.exceptionHandler(res -> {
      logger.error("There was an exception in the verticle.controller consumer for Cluster Node: {}.", clusterNodeID);
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
    String clusterNodeID = config().getString("clusterNodeID");
    String deploymentID = deploymentID();

    JsonObject payload = new JsonObject()
      .put("eventType", "consume.updates.HeatSensor")
      .put("clusterNodeID", clusterNodeID)
      .put("deploymentID", deploymentID)
      .put("temperature", temperature);

    logger.debug("Heat Sensor Data from: {}.", payload.toString());

    vertx.eventBus().publish("cluster.HeatSensors", payload);
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

