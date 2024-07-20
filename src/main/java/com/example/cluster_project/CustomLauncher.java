package com.example.cluster_project;

import com.hazelcast.config.Config;
import io.vertx.core.Launcher;
import io.vertx.core.VertxOptions;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

public class CustomLauncher extends Launcher {

  @Override
  public void beforeStartingVertx(VertxOptions options) {
    Config hazelcastConfig = Config.load();
    HazelcastClusterManager mgr = new HazelcastClusterManager(hazelcastConfig);
    options.setClusterManager(mgr);
  }

  public static void main(String[] args) {
    new CustomLauncher().dispatch(args);
  }
}
