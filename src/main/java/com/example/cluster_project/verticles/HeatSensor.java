package com.example.cluster_project.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.SharedData;
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
    JsonObject config = config();
    String nodeDeploymentID = config.getString("nodeDeploymentID");
    String heatSensorDeploymentID = deploymentID();
    registerVerticle(nodeDeploymentID, heatSensorDeploymentID).onComplete(ar -> {

      this.startUpdates();
    });
  }

  private Future<Long> registerVerticle(String nodeDeploymentID, String heatSensorDeploymentID) {
    SharedData sharedData = vertx.sharedData();

    Future<Long> incrementFuture = sharedData.getCounter("heatSensorVerticleCount").compose(counter ->
      counter.incrementAndGet()
    );

    Future<Void> mapUpdateFuture = sharedData.<String, String>getAsyncMap("verticleRegistry").compose(map ->
      map.put(nodeDeploymentID, heatSensorDeploymentID).mapEmpty()
    );

    return CompositeFuture.all(incrementFuture, mapUpdateFuture).compose(result -> {
      Long incrementedCount = result.resultAt(0);
      return Future.succeededFuture(incrementedCount);
    });
  }


  @Override
  public void stop() {
    stopUpdates();
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
      .put("nodeDeploymentID", nodeDeploymentID)
      .put("heatSensorDeploymentID", heatSensorDeploymentID)
      .put("temp", temperature);

    vertx.eventBus().publish("sensor.updates." + heatSensorDeploymentID, payload);
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

