#!/bin/bash

# Script to run the CaseHub DocumentAnalysisApp example
# This demonstrates the real CaseHub implementation in action

set -e

cd "$(dirname "$0")"

echo "════════════════════════════════════════════════════════════"
echo "  CaseHub Document Analysis Example"
echo "  Using Real Implementation"
echo "════════════════════════════════════════════════════════════"
echo ""

# Check if Quarkus is available
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven not found. Please install Maven first."
    exit 1
fi

echo "🔨 Compiling CaseHub..."
mvn compile -q

echo "🚀 Starting example..."
echo ""

# Run in Quarkus dev mode
mvn quarkus:dev -Dquarkus.args="--run-example"
