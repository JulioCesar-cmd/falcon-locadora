#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
sh ./mvnw -q -DskipTests package
exec java -jar target/falcon-locadora-2.3.0.jar "$@"
