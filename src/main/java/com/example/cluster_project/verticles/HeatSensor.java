package com.example.cluster_project.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
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
      JsonObject messageBody = message.body();
      String action = messageBody.getString("action");

      JsonObject payload = new JsonObject()
        .put("status", "success")
        .put("clusterID", clusterID)
        .put("deploymentID", deploymentID);

      switch (action) {
        case "start.updates":
          this.startUpdates();
          addDeploymentToActiveSensors(deploymentID)
            .onSuccess(v -> logger.info("Deployment ID {} added to activeSensors", deploymentID))
            .onFailure(cause -> logger.error("Failed to add Deployment ID {} to activeSensors", deploymentID, cause));
          logger.info("Updates started for [{}] deployed on [{}].", deploymentID, clusterID);
          payload.put("eventType", "start.updates.HeatSensor");
          message.reply(payload);
          break;
        case "stop.updates":
          this.stopUpdates();
          removeDeploymentFromActiveSensors(deploymentID)
            .onSuccess(v -> logger.info("Deployment ID {} removed from activeSensors", deploymentID))
            .onFailure(cause -> logger.error("Failed to remove Deployment ID {} from activeSensors", deploymentID, cause));
          logger.info("Updates stopped for [{}] deployed on [{}].", deploymentID, clusterID);
          payload.put("eventType", "stop.updates.HeatSensor");
          message.reply(payload);
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


  private Future<Void> addDeploymentToActiveSensors(String deploymentID) {
    Promise<Void> promise = Promise.promise();
    vertx.sharedData().<String, JsonArray>getAsyncMap("activeSensors")
      .onSuccess(map -> {
        map.get("activeSensors")
          .onSuccess(deploymentIDs -> {
            if (deploymentIDs == null) {
              deploymentIDs = new JsonArray();
            }
            deploymentIDs.add(deploymentID);
            map.put("activeSensors", deploymentIDs)
              .onSuccess(v -> promise.complete())
              .onFailure(promise::fail);
          })
          .onFailure(promise::fail);
      })
      .onFailure(promise::fail);
    return promise.future();
  }

  private Future<Void> removeDeploymentFromActiveSensors(String deploymentID) {
    Promise<Void> promise = Promise.promise();
    vertx.sharedData().<String, JsonArray>getAsyncMap("activeSensors")
      .onSuccess(map -> {
        map.get("activeSensors")
          .onSuccess(deploymentIDs -> {
            if (deploymentIDs != null && deploymentIDs.contains(deploymentID)) {
              deploymentIDs.remove(deploymentID);
              map.put("activeSensors", deploymentIDs)
                .onSuccess(v -> promise.complete())
                .onFailure(promise::fail);
            } else {
              promise.complete();
            }
          })
          .onFailure(promise::fail);
      })
      .onFailure(promise::fail);
    return promise.future();
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

    JsonObject payload = new JsonObject()
      .put("action", "consume.updates")
      .put("clusterID", clusterID)
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

