#!/bin/bash
# Run FlowWorkerDemo using Maven exec plugin

cd "$(dirname "$0")/.."

echo "Building project..."
mvn clean compile -DskipTests -pl casehub-core,casehub-flow-worker -q
if [ $? -ne 0 ]; then
    echo "Build failed!"
    exit 1
fi

echo "Running FlowWorkerDemo..."
echo ""

cd casehub-flow-worker
mvn exec:java -q
