#!/usr/bin/env bash

# Start
echo "Starting Cluster"

java -Dhazelcast.diagnostics.enabled=true -Dhazelcast.security.recommendations -jar $1 -cluster

