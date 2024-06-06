package com.example.cluster_project;

import com.example.cluster_project.verticles.HTTPServer;
import com.example.cluster_project.verticles.HeatSensor;
import com.example.cluster_project.verticles.SensorData;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.SharedData;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

  public static void main(String[] args) {
    HazelcastClusterManager mgr = new HazelcastClusterManager();
    VertxOptions options = new VertxOptions().setClusterManager(mgr);

    Vertx.clusteredVertx(options, res -> {
      if (res.succeeded()) {
        Vertx vertx = res.result();
        vertx.deployVerticle(new MainVerticle(), deployRes -> {
          if (deployRes.succeeded()) {
            logger.info("MainVerticle deployed successfully");
          } else {
            logger.error("Failed to deploy MainVerticle", deployRes.cause());
          }
        });
      } else {
        logger.error("Failed to start clustered Vert.x instance", res.cause());
      }
    });
  }

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    deployHTTPServerVerticle()
      .compose(id -> deployHeatSensorVerticle())
      .compose(id -> deploySensorDataVerticle())
      .onComplete(res -> {
        if (res.succeeded()) {
          storeDeploymentIDInSharedData();
          startPromise.complete();
          logger.info("All verticles deployed successfully");
        } else {
          startPromise.fail(res.cause());
          logger.error("Failed to deploy verticles", res.cause());
        }
      });
  }

  private Future<String> deployHTTPServerVerticle() {
    Promise<String> promise = Promise.promise();
    vertx.deployVerticle(new HTTPServer(), promise);
    return promise.future();
  }

  private Future<String> deployHeatSensorVerticle() {
    Promise<String> promise = Promise.promise();
    vertx.deployVerticle(new HeatSensor(), promise);
    return promise.future();
  }

  private Future<String> deploySensorDataVerticle() {
    Promise<String> promise = Promise.promise();
    vertx.deployVerticle(new SensorData(), promise);
    return promise.future();
  }

  private void storeDeploymentIDInSharedData() {
    vertx.sharedData().<String, String>getAsyncMap("deploymentIDs", res -> {
      if (res.succeeded()) {
        AsyncMap<String, String> map = res.result();
        String deploymentID = deploymentID();
        map.put("MainVerticle", deploymentID, putRes -> {
          if (putRes.succeeded()) {
            logger.info("Stored MainVerticle deployment ID: {}", deploymentID);
          } else {
            logger.error("Failed to store MainVerticle deployment ID", putRes.cause());
          }
        });
      } else {
        logger.error("Failed to get AsyncMap for deployment IDs", res.cause());
      }
    });
  }

}


//package com.example.cluster_project;
//
//import com.example.cluster_project.verticles.ConsumerManagerVerticle;
//import com.example.cluster_project.verticles.HTTPServer;
//import com.example.cluster_project.verticles.HeatSensor;
//import com.example.cluster_project.verticles.SensorData;
//import io.vertx.core.*;
//
//import io.vertx.core.json.JsonObject;
//import io.vertx.core.spi.cluster.ClusterManager;
//import io.vertx.ext.web.sstore.ClusteredSessionStore;
//import io.vertx.ext.web.sstore.SessionStore;
//import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.util.UUID;
//
//public class MainVerticle extends AbstractVerticle {
//
//  private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);
//
//  /**
//   * Converts nanoseconds to seconds.
//   *
//   * @param nanoseconds the time in nanoseconds
//   * @return the time in seconds
//   */
//  public static float nanoToSeconds(long nanoseconds) {
//    return (float) (nanoseconds / 1_000_000_000.0);
//  }
//
//  //------------------------------------------------------------------------------------------------------------------
//  // Only called when starting locally
//  public static void main(String[] args) throws Exception {
//    VertxOptions vertxOptions = new VertxOptions();
//    vertxOptions.setWarningExceptionTime(2000);
//    Vertx vertx = Vertx.vertx(vertxOptions);
//    vertx.deployVerticle(MainVerticle.class.getName());
//  }
//
//  @Override
//  public void start(Promise<Void> startPromise) throws Exception {
//    deployConsumerManagerVerticle()
//      .compose(id -> deployHTTPServerVerticle())
//      .compose(id -> deployHeatSensorVerticle())
//      .compose(id -> deploySensorDataVerticle())
//      .onComplete(res -> {
//        if (res.succeeded()) {
//          startPromise.complete();
//          logger.info("All verticles deployed successfully");
//        } else {
//          startPromise.fail(res.cause());
//          logger.error("Failed to deploy verticles", res.cause());
//        }
//      });
//  }
//
//  private Future<String> deployConsumerManagerVerticle() {
//    Promise<String> promise = Promise.promise();
//    vertx.deployVerticle(new ConsumerManagerVerticle(), promise);
//    return promise.future();
//  }
//
//  private Future<String> deployHTTPServerVerticle() {
//    Promise<String> promise = Promise.promise();
//    vertx.deployVerticle(new HTTPServer(), promise);
//    return promise.future();
//  }
//
//  private Future<String> deployHeatSensorVerticle() {
//    Promise<String> promise = Promise.promise();
//    vertx.deployVerticle(new HeatSensor(), promise);
//    return promise.future();
//  }
//
//  private Future<String> deploySensorDataVerticle() {
//    Promise<String> promise = Promise.promise();
//    vertx.deployVerticle(new SensorData(), promise);
//    return promise.future();
//  }
//}
