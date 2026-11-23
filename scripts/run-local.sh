#!/usr/bin/env sh
set -eu
: "${INFRAI_API_KEY:?Set INFRAI_API_KEY}"
exec mvn spring-boot:run
