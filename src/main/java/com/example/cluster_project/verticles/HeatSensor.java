package com.example.cluster_project.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.SharedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

import static com.example.cluster_project.utils.DatastarUtils.addHeatSensor;
import static com.example.cluster_project.utils.DatastarUtils.consumeSensorData;

public class HeatSensor extends AbstractVerticle {

  private final Logger logger = LoggerFactory.getLogger(HeatSensor.class);

  private long timerID;
  private final Random random = new Random();
  private double temperature = 21.0;

  @Override
  public void start() {
    logger.debug("Starting HeatSensor: {}", getClass().getName());
    MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer("heatSensors", this::consumeMessage);
  }

  @Override
  public void stop() {
    stopUpdates();
  }

  private void consumeMessage(Message<JsonObject> msg) {
    String nodeDeploymentID = config().getString("nodeDeploymentID");

    JsonObject payload = msg.body();
    String eventType = payload.getString("eventType");
    String sensorNodeDeploymentID = payload.getString("sensorNodeDeploymentID");
    String heatSensorDeploymentID = payload.getString("heatSensorDeploymentID");

    if (nodeDeploymentID.equals(sensorNodeDeploymentID) && heatSensorDeploymentID.equals(deploymentID())) {
      if (eventType.equals("startUpdates")) {
        this.startUpdates();
      } else if (eventType.equals("stopUpdates")) {
        this.stopUpdates();
      }
    }
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
    String nodeDeploymentID = config().getString("nodeDeploymentID");
    String heatSensorDeploymentID = deploymentID();

    JsonObject payload = new JsonObject()
      .put("sensorNodeDeploymentID", nodeDeploymentID)
      .put("heatSensorDeploymentID", heatSensorDeploymentID)
      .put("eventType", "sensorUpdate")
      .put("temp", temperature);

    vertx.eventBus().publish("heatSensors", payload);
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

