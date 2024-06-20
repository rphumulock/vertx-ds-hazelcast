package com.example.cluster_project.utils;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

import java.util.ArrayList;
import java.util.List;

public class ClusterUtils {

  public static void getClusterNodes(Vertx vertx, Handler<AsyncResult<List<String>>> resultHandler) {
    vertx.sharedData().<String, Long>getClusterWideMap("activeNodes", res -> {
      if (res.succeeded()) {
        res.result().keys(ar -> {
          if (ar.succeeded()) {
            resultHandler.handle(Future.succeededFuture(new ArrayList<>(ar.result())));
          } else {
            resultHandler.handle(Future.failedFuture(ar.cause()));
          }
        });
      } else {
        resultHandler.handle(Future.failedFuture(res.cause()));
      }
    });
  }

}
