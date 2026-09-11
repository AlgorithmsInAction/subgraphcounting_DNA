#!/bin/bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

mkdir -p bin

javac --release 8 \
  -cp "lib/guava-16.0.1.jar" \
  -sourcepath src \
  -d bin \
  src/dna/io/InvalidFormatException.java \
  src/dna/tools/DynamicUndirectedSubgraphCounter.java
