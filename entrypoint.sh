#!/usr/bin/env bash

# Start
echo "Starting Cluster"

# Run the application with clustering and Hazelcast diagnostics enabled
java -jar $1 -cluster -Dhazelcast.diagnostics.enabled=true
