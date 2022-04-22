#!/bin/sh

BUCKET_ID=$( docker compose exec -it influxdb influx bucket find -n webcrawler-metrics | awk 'FNR == 2 {print $1}' )

docker compose exec -it influxdb influx v1 dbrp create --db webcrawler-metrics --rp week --default --bucket-id "$BUCKET_ID"