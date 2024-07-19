# Use an official OpenJDK 11 runtime as a parent image
FROM container-registry.oracle.com/java/openjdk:22.0.2

# Set environment variables
ENV VERTICLE_FILE=cluster-project-1.0.0-SNAPSHOT-fat.jar

# Set the location of the verticles
ENV VERTICLE_HOME=/usr/verticles

# Set JVM options
ENV JDK_JAVA_OPTIONS="--add-modules java.se \
  --add-exports java.base/jdk.internal.ref=ALL-UNNAMED \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.nio=ALL-UNNAMED \
  --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
  --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
  --add-opens java.management/sun.management=ALL-UNNAMED \
  --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED \
  -Dhazelcast.diagnostics.enabled=true"

ARG UID=2604
ARG GID=2604

# Expose the application port
EXPOSE 8080

# Copy the fat JAR file to the container
COPY target/$VERTICLE_FILE $VERTICLE_HOME/

# Copy the entrypoint script
COPY entrypoint.sh /entrypoint.sh

# Ensure the entrypoint script is executable
RUN chmod +x /entrypoint.sh

# Set the working directory
WORKDIR $VERTICLE_HOME

# Command to run the fat JAR file
ENTRYPOINT ["/entrypoint.sh", "/usr/verticles/cluster-project-1.0.0-SNAPSHOT-fat.jar"]
