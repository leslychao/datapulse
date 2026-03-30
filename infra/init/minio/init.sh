#!/bin/sh
set -e
mc alias set datapulse http://minio:9000 datapulse ${MINIO_ROOT_PASSWORD}
mc mb datapulse/datapulse-raw --ignore-existing
mc mb datapulse/datapulse-exports --ignore-existing
echo "MinIO buckets created"
