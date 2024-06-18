package com.example.cluster_project.verticles;

import io.vertx.core.AbstractVerticle;
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
    this.startUpdates();
  }

  private void startUpdates() {
    this.timerID = vertx.setTimer(random.nextInt(2000), this::update);
  }

  public void stopUpdates() {
    vertx.cancelTimer(this.timerID);
  }

  private void update(long timerId) {
    temperature = temperature + (delta() / 10);
    String nodeDeploymentID = config().getString("nodeDeploymentID");
    String heatSensorDeploymentID = deploymentID();

    JsonObject payload = new JsonObject()
      .put("nodeDeploymentID", nodeDeploymentID)
      .put("heatSensorDeploymentID", heatSensorDeploymentID)
      .put("temp", temperature);

    vertx.eventBus().publish("sensor.updates", payload);
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

