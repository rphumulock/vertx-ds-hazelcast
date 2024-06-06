package com.example.cluster_project.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.SharedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsumerManagerVerticle extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(ConsumerManagerVerticle.class);

  @Override
  public void start() {
    SharedData sharedData = vertx.sharedData();

    vertx.eventBus().consumer("setup.consumer", message -> {
      JsonObject request = (JsonObject) message.body();
      String sessionId = request.getString("sessionId");

      sharedData.<String, MessageConsumer<JsonObject>>getAsyncMap("consumers", res -> {
        if (res.succeeded()) {
          AsyncMap<String, MessageConsumer<JsonObject>> consumers = res.result();
          MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer("sensor.updates", msg -> {
            JsonObject response = new JsonObject()
              .put("id", msg.body().getString("id"))
              .put("temp", msg.body().getString("temp"));
            vertx.eventBus().send("consumer.response." + sessionId, response);
          });
          consumers.put(sessionId, consumer, putRes -> {
            if (putRes.succeeded()) {
              message.reply(new JsonObject().put("status", "consumer setup successfully"));
            } else {
              message.fail(500, "Failed to setup consumer");
            }
          });
        } else {
          message.fail(500, "Failed to access consumers map");
        }
      });
    });

    vertx.eventBus().consumer("cleanup.consumer", message -> {
      String sessionId = (String) message.body();

      sharedData.<String, MessageConsumer<JsonObject>>getAsyncMap("consumers", res -> {
        if (res.succeeded()) {
          AsyncMap<String, MessageConsumer<JsonObject>> consumers = res.result();
          consumers.get(sessionId, getRes -> {
            if (getRes.succeeded()) {
              MessageConsumer<JsonObject> consumer = getRes.result();
              if (consumer != null && consumer.isRegistered()) {
                consumer.unregister().onComplete(unregRes -> {
                  if (unregRes.succeeded()) {
                    consumers.remove(sessionId);
                    message.reply(new JsonObject().put("status", "consumer cleaned up successfully"));
                  } else {
                    message.fail(500, "Failed to unregister consumer");
                  }
                });
              } else {
                message.fail(404, "Consumer not found");
              }
            } else {
              message.fail(500, "Failed to get consumer from map");
            }
          });
        } else {
          message.fail(500, "Failed to access consumers map");
        }
      });
    });
  }
}
