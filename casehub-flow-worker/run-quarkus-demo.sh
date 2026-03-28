#!/bin/bash
# Run FlowWorkerQuarkusDemo using Quarkus dev mode
# This version uses QuarkusFlowDocumentWorkflow with full Quarkus runtime

cd "$(dirname "$0")"

echo "Building project..."
cd ..
mvn clean compile -DskipTests -pl casehub-core,casehub-flow-worker -q
if [ $? -ne 0 ]; then
    echo "Build failed!"
    exit 1
fi

echo "Starting Quarkus Flow Worker Demo (Quarkus runtime)..."
echo ""
echo "Press Ctrl+C to stop"
echo ""

cd casehub-flow-worker
mvn quarkus:dev -Dquarkus.args="" -Dquarkus.log.console.level=INFO
