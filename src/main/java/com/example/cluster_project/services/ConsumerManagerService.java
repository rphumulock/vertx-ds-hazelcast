package com.example.cluster_project.services;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.SharedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConsumerManagerService {

  private static final Logger logger = LoggerFactory.getLogger(ConsumerManagerService.class);
  private final Vertx vertx;
  private final SharedData sharedData;
  private final Map<String, MessageConsumer<JsonObject>> localConsumers;

  public ConsumerManagerService(Vertx vertx) {
    this.vertx = vertx;
    this.sharedData = vertx.sharedData();
    this.localConsumers = new ConcurrentHashMap<>();
  }

  public void setupConsumer(String sessionId) {
    sharedData.<String, String>getAsyncMap("consumerSessions", res -> {
      if (res.succeeded()) {
        AsyncMap<String, String> consumerSessions = res.result();
        MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer("sensor.updates", msg -> {
          JsonObject response = new JsonObject()
            .put("id", msg.body().getString("id"))
            .put("temp", msg.body().getString("temp"));
          vertx.eventBus().send("consumer.response." + sessionId, response);
        });

        localConsumers.put(sessionId, consumer);
        consumerSessions.put(sessionId, "active", putRes -> {
          if (putRes.succeeded()) {
            logger.info("Consumer setup successfully for session: {}", sessionId);
          } else {
            logger.error("Failed to setup consumer for session: {}", sessionId, putRes.cause());
          }
        });
      } else {
        logger.error("Failed to access consumerSessions map", res.cause());
      }
    });
  }

  public void cleanupConsumer(String sessionId) {
    sharedData.<String, String>getAsyncMap("consumerSessions", res -> {
      if (res.succeeded()) {
        AsyncMap<String, String> consumerSessions = res.result();
        MessageConsumer<JsonObject> consumer = localConsumers.remove(sessionId);
        if (consumer != null && consumer.isRegistered()) {
          consumer.unregister().onComplete(unregRes -> {
            if (unregRes.succeeded()) {
              consumerSessions.remove(sessionId);
              logger.info("Consumer cleaned up successfully for session: {}", sessionId);
            } else {
              logger.error("Failed to unregister consumer for session: {}", sessionId, unregRes.cause());
            }
          });
        } else {
          logger.error("Consumer not found or already unregistered for session: {}", sessionId);
        }
      } else {
        logger.error("Failed to access consumerSessions map", res.cause());
      }
    });
  }
}
