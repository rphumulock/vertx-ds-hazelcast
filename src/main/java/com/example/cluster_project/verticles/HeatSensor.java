package com.example.cluster_project.verticles;

import com.example.cluster_project.MainVerticle;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.UUID;

public class HeatSensor extends AbstractVerticle {

  private final Logger logger = LoggerFactory.getLogger(HeatSensor.class);

  private final Random random = new Random();
  private final String sensorId = "id_" + UUID.randomUUID();
  private double temperature = 21.0;

  @Override
  public void start() {
    logger.debug("Start {}", getClass().getName());
    scheduleNextUpdate();
  }

  private void scheduleNextUpdate() {
    vertx.setTimer(random.nextInt(2000), this::update);
  }

  private void update(long timerId) {
    temperature = temperature + (delta() / 10);
    String deploymentID = config().getString("deploymentID");
    JsonObject payload = new JsonObject()
      .put("deploymentID", deploymentID)
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

