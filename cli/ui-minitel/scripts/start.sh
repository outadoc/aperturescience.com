#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

podman network exists teletel || podman network create teletel

podman build -t localhost/minipavi ./minipavi
podman build -t localhost/emulminitel ./emulminitel

podman run -d --replace --name minipavi \
  --network teletel \
  -p 8182:8182 \
  -v "$(pwd)/minipavi/config:/config:Z" \
  localhost/minipavi

podman run -d --replace --name emulminitel \
  --network teletel \
  -p 8082:80 \
  localhost/emulminitel

xdg-open "http://localhost:8082/?gw=ws://localhost:8182%3Furl%3Dhttp%3A%2F%2Fhost.containers.internal%3A8080" \
  || open "http://localhost:8082/?gw=ws://localhost:8182%3Furl%3Dhttp%3A%2F%2Fhost.containers.internal%3A8080"
