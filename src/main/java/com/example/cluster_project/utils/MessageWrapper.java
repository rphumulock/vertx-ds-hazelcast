package com.example.cluster_project.utils;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.ClusterSerializable;

public class MessageWrapper implements ClusterSerializable {
  private JsonObject message;
  private DeploymentOptions deploymentOptions;

  public MessageWrapper() {
  }

  public MessageWrapper(JsonObject message, DeploymentOptions deploymentOptions) {
    this.message = message;
    this.deploymentOptions = deploymentOptions;
  }

  public JsonObject getMessage() {
    return message;
  }

  public DeploymentOptions getDeploymentOptions() {
    return deploymentOptions;
  }

  @Override
  public void writeToBuffer(Buffer buffer) {
    JsonObject json = new JsonObject()
      .put("message", message)
      .put("deploymentOptions", deploymentOptions.toJson());
    byte[] bytes = json.encode().getBytes();
    buffer.appendInt(bytes.length);
    buffer.appendBytes(bytes);
  }

  @Override
  public int readFromBuffer(int pos, Buffer buffer) {
    int length = buffer.getInt(pos);
    byte[] bytes = buffer.getBytes(pos + 4, pos + 4 + length);
    String jsonString = new String(bytes);
    JsonObject json = new JsonObject(jsonString);
    this.message = json.getJsonObject("message");
    this.deploymentOptions = new DeploymentOptions(json.getJsonObject("deploymentOptions"));
    return length;
  }
}
