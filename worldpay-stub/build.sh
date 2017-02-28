#!/bin/bash -e

echo "[build.sh] building..."
./sbt dist
echo "[build.sh] done"
