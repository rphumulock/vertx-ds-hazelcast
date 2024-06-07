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

        // Register Consumer Stream
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

        consumer.endHandler(unused -> {
          sharedData.<String, String>getAsyncMap("sensorDataMap", sensorRes -> {
            if (sensorRes.succeeded()) {
              AsyncMap<String, String> sensorDataMap = sensorRes.result();
              // Iterate through the map to find and remove entries associated with the session
              sensorDataMap.entries(mapRes -> {
                if (mapRes.succeeded()) {
                  mapRes.result().forEach((id, storedSessionId) -> {
                    if (storedSessionId.equals(sessionId)) {
                      sensorDataMap.remove(id, removeRes -> {
                        if (removeRes.succeeded()) {
                          logger.info("Removed sensor data for ID: {}", id);
                        } else {
                          logger.error("Failed to remove sensor data for ID: {}", id, removeRes.cause());
                        }
                      });
                    }
                  });
                } else {
                  logger.error("Failed to retrieve entries from sensorDataMap", mapRes.cause());
                }
              });
            } else {
              logger.error("Failed to get sensorDataMap", sensorRes.cause());
            }
          });

          cleanupConsumer(sessionId);
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
