#!/bin/bash

play_cmd=./sbt

distro=payments/target/cjp-paymentdb.tar.gz

echo "[build.sh] using play command: $play_cmd"

echo "[build.sh] building..."
$play_cmd "test" dist

if [ $? -ne 0 ]; then
  echo "[build.sh] failure"
  exit 1
else
  echo "[build.sh] adding build info properties file"
  build_info_file=payments/target/universal/build-info.yaml
  echo "buildNumber: ${BUILD_NUMBER:-"LOCAL"}
lastCommit:  $(git rev-parse HEAD)" > $build_info_file
  zip -qj payments/target/universal/payments-1.0.zip $build_info_file

  echo "[build.sh] done"
fi
