//package com.example.cluster_project.services;
//
//import com.example.cluster_project.ui.partials.Partials;
//import com.example.cluster_project.utils.DatastarUtils;
//import io.vertx.core.Vertx;
//import io.vertx.core.shareddata.AsyncMap;
//import io.vertx.core.shareddata.SharedData;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.util.UUID;
//
//import static com.example.cluster_project.utils.DatastarUtils.sendSSE;
//
//public class HeatSensorManagerService {
//
//  private static final Logger logger = LoggerFactory.getLogger(HeatSensorManagerService.class);
//  private final SharedData sharedData;
//
//  public HeatSensorManagerService(Vertx vertx) {
//    this.sharedData = vertx.sharedData();
//  }
//
//
//  public void addSensorData(String id, String sessionId) {
//    vertx.sharedData().<String, String>getAsyncMap("sensorDataMap", res -> {
//      if (res.succeeded()) {
//        AsyncMap<String, String> sensorDataMap = res.result();
//        sensorDataMap.get(id, asyncResult -> {
//          if (asyncResult.succeeded() && asyncResult.result() == null) {
//            // ID does not exist, perform DELETE and APPEND
//            sensorDataMap.put(id, sessionId, putRes -> {
//              if (putRes.succeeded()) {
//
//              } else {
//                logger.error("Failed to put ID into sensorDataMap", putRes.cause());
//              }
//            });
//          } else {
//
//          }
//        });
//      } else {
//        logger.error("Failed to get sensorDataMap", res.cause());
//      }
//    });
//  }
//
//  public void clearSensorDataMap(String sessionId) {
//    // Clear sensorDataMap entries associated with the session
//    sharedData.<String, String>getAsyncMap("sensorDataMap", res -> {
//      if (res.succeeded()) {
//        AsyncMap<String, String> sensorDataMap = res.result();
//        // Iterate through the map to find and remove entries associated with the session
//        sensorDataMap.entries(mapRes -> {
//          if (mapRes.succeeded()) {
//            mapRes.result().forEach((id, storedSessionId) -> {
//              if (storedSessionId.equals(sessionId)) {
//                sensorDataMap.remove(id, removeRes -> {
//                  if (removeRes.succeeded()) {
//                    logger.info("Removed sensor data for ID: {}", id);
//                  } else {
//                    logger.error("Failed to remove sensor data for ID: {}", id, removeRes.cause());
//                  }
//                });
//              }
//            });
//          } else {
//            logger.error("Failed to retrieve entries from sensorDataMap", mapRes.cause());
//          }
//        });
//      } else {
//        logger.error("Failed to get sensorDataMap", res.cause());
//      }
//    });
//  }
//
//}
