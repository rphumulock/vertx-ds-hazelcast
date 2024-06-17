package com.example.cluster_project.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class HeatSensor extends AbstractVerticle {

  private final Logger logger = LoggerFactory.getLogger(HeatSensor.class);

  private final Random random = new Random();
  //  private final String sensorId = "id_" + UUID.randomUUID();
  private double temperature = 21.0;

  @Override
  public void start() {
    logger.debug("Starting HeatSensor: {}", getClass().getName());
    scheduleNextUpdate();
  }

  private void scheduleNextUpdate() {
    vertx.setTimer(random.nextInt(2000), this::update);
  }

  private void update(long timerId) {
    temperature = temperature + (delta() / 10);
    String nodeDeploymentID = config().getString("nodeDeploymentID");
    String heatSensorDeploymentID = deploymentID();
    String sensorId = "id_" + heatSensorDeploymentID;

    JsonObject payload = new JsonObject()
      .put("nodeDeploymentID", nodeDeploymentID)
      .put("heatSensorDeploymentID", heatSensorDeploymentID)
      .put("id", sensorId)
      .put("temp", temperature);

    vertx.eventBus().publish("sensor.updates", payload);
    scheduleNextUpdate();
  }

  private double delta() {
    if (random.nextInt() > 0) {
      return random.nextGaussian();
    } else {
      return -random.nextGaussian();
    }
  }

}

