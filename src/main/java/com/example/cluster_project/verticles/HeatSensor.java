package com.example.cluster_project.verticles;

import com.example.cluster_project.services.ClusterRegistrationService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class HeatSensor extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(HeatSensor.class);

  private static final int MAX_RETRIES = 3;
  private static final String ACTION_START_UPDATES = "startUpdates";
  private static final String ACTION_STOP_UPDATES = "stopUpdates";

  private ClusterRegistrationService proxy;
  private long timerID;
  private final Random random = new Random();
  private double temperature = 21.0;
  private int retryCount = 0;

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

  private void setupConsumers() {
    Promise<Void> promise = Promise.promise();
    String clusterID = config().getString("clusterID");
    String deploymentID = deploymentID();
    MessageConsumer<JsonObject> updatesConsumer = vertx.eventBus().consumer("HeatSensor." + clusterID + "." + deploymentID);

    updatesConsumer.handler(message -> handleConsumerMessage(message, clusterID, deploymentID));
    updatesConsumer.completionHandler(res -> {
      if (res.succeeded()) {
        logger.info("HeatSensor.{}.{} completionHandler succeeded", clusterID, deploymentID);
        promise.complete();
      } else {
        logger.error("HeatSensor completionHandler failed", res.cause());
        promise.fail(res.cause());
      }
    });

    promise.future();
  }

  private void handleConsumerMessage(io.vertx.core.eventbus.Message<JsonObject> message, String clusterID, String deploymentID) {
    String action = message.headers().get("action");
    JsonObject reply = new JsonObject();

    switch (action) {
      case ACTION_START_UPDATES:
        handleStartUpdates(clusterID, deploymentID, message, reply);
        break;
      case ACTION_STOP_UPDATES:
        handleStopUpdates(clusterID, deploymentID, message, reply);
        break;
      default:
        reply.put("status", "failure").put("message", "Unknown action");
        message.reply(reply);
        break;
    }
  }

  private void handleStartUpdates(String clusterID, String deploymentID, io.vertx.core.eventbus.Message<JsonObject> message, JsonObject reply) {
    startUpdates();
    proxy.registerActivated(HeatSensor.class.getName(), deploymentID)
      .onSuccess(v -> {
        logger.info("Updates started for [{}] deployed on [{}].", clusterID, deploymentID);
        reply.put("status", "success").put("deploymentID", deploymentID);
        message.reply(reply);
      })
      .onFailure(err -> handleFailure(reply, message, err));
  }

  private void handleStopUpdates(String clusterID, String deploymentID, io.vertx.core.eventbus.Message<JsonObject> message, JsonObject reply) {
    stopUpdates();
    proxy.unregisterActivated(HeatSensor.class.getName(), deploymentID)
      .onSuccess(v -> {
        logger.info("Updates stopped for [{}] deployed on [{}].", clusterID, deploymentID);
        reply.put("status", "success").put("deploymentID", deploymentID);
        message.reply(reply);
      })
      .onFailure(err -> handleFailure(reply, message, err));
  }

  private void handleFailure(JsonObject reply, io.vertx.core.eventbus.Message<JsonObject> message, Throwable err) {
    logger.error("Operation failed", err);
    reply.put("status", "failure").put("message", err.getMessage());
    message.reply(reply);
  }

  private void startUpdates() {
    setUpdateTimer();
  }

  private void stopUpdates() {
    vertx.cancelTimer(timerID);
  }

  private void setUpdateTimer() {
    try {
      timerID = vertx.setTimer(random.nextInt(2, 2000), this::update);
      retryCount = 0; // Reset retry count on successful timer set
    } catch (Exception e) {
      handleTimerFailure(e);
    }
  }

  private void handleTimerFailure(Exception e) {
    if (retryCount < MAX_RETRIES) {
      retryCount++;
      logger.error("Failed to start HeatSensor timer, retrying {}/{}...", retryCount, MAX_RETRIES, e);
      setUpdateTimer(); // Retry setting the timer
    } else {
      logger.error("Failed to start HeatSensor timer after {} retries", MAX_RETRIES, e);
    }
  }

  private void update(long timerId) {
    temperature += delta() / 10;
    String clusterID = config().getString("clusterID");
    String deploymentID = deploymentID();

    JsonObject message = new JsonObject()
      .put("clusterID", clusterID)
      .put("deploymentID", deploymentID)
      .put("temperature", temperature);

    DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("action", "consumeUpdates");
    vertx.eventBus().publish("cluster.HeatSensors", message, deliveryOptions);

    setUpdateTimer(); // Schedule the next update
  }

  private double delta() {
    return random.nextBoolean() ? random.nextGaussian() : -random.nextGaussian();
  }
}
