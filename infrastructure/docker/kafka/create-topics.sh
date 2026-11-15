
#!/bin/bash

set -e

BOOTSTRAP_SERVER="${KAFKA_BOOTSTRAP_SERVER:-localhost:9092}"

echo "Creating LineLess Kafka topics..."

# Customer joins a queue
kafka-topics.sh \
  --bootstrap-server "$BOOTSTRAP_SERVER" \
  --create \
  --if-not-exists \
  --topic queue-joined \
  --partitions 1 \
  --replication-factor 1

# Admin calls the next customer
kafka-topics.sh \
  --bootstrap-server "$BOOTSTRAP_SERVER" \
  --create \
  --if-not-exists \
  --topic turn-called \
  --partitions 1 \
  --replication-factor 1

# Customer completes their turn
kafka-topics.sh \
  --bootstrap-server "$BOOTSTRAP_SERVER" \
  --create \
  --if-not-exists \
  --topic queue-completed \
  --partitions 1 \
  --replication-factor 1

echo "LineLess Kafka topics created successfully."

echo "Available topics:"
kafka-topics.sh \
  --bootstrap-server "$BOOTSTRAP_SERVER" \
  --list