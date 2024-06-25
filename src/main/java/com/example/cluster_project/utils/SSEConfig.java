package com.example.cluster_project.utils;

public final class SSEConfig {  // Class declared final
  private final String event = "event: datastar-fragment\n";
  private final String selector;
  private final String id;
  private final String mergeType;
  private final Number settle;  // Consider changing Number to a specific type like Double if mutability is a concern
  private final String fragment;
  private final boolean vt;

  private SSEConfig(Builder builder) {
    this.selector = builder.selector;
    this.id = builder.id;
    this.mergeType = builder.mergeType;
    this.settle = builder.settle;
    this.fragment = builder.fragment;
    this.vt = builder.vt;
  }

  public String getEvent() {
    return this.event;
  }

  public String getSelector() {
    return selector;
  }

  public String getId() {
    return id;
  }

  public String getMergeType() {
    return mergeType;
  }

  public Number getSettle() {
    return settle;
  }

  public String getFragment() {
    return fragment;
  }

  public boolean isVT() {
    return vt;
  }

  // Builder static inner class
  public static class Builder {
    private String selector;
    private String id;
    private String mergeType;
    private Number settle;
    private String fragment;
    private boolean vt;

    public Builder withSelector(String selector) {
      this.selector = selector;
      return this;
    }

    public Builder withId(String id) {
      this.id = id;
      return this;
    }

    public Builder withMergeType(String mergeType) {
      this.mergeType = mergeType;
      return this;
    }

    public Builder withSettle(Number settle) {
      this.settle = settle;
      return this;
    }

    public Builder withFragment(String fragment) {
      this.fragment = fragment;
      return this;
    }

    public Builder withVT(boolean vt) {
      this.vt = vt;
      return this;
    }

    public SSEConfig build() {
      return new SSEConfig(this);
    }
  }
}
