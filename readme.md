First turn on kafka + schema registry.

```
docker-compose up -d zookeeper kafka schema-registry
```

Build the shadow jar

```
./gradlew shadowJar
connect-standalone connect-avro-standalone.properties connector1.properties
```

View the output

```
kafka-avro-console-consumer --bootstrap-server localhost:9092 --whitelist 'seq_.+' --from-beginning
```

You can see the contents of the `/tmp/connect.offsets` file using jshell.

```
kafka_bin=$(dirname $(which kafka-console-consumer))\nshare_jars="$kafka_bin/../share/java/kafka/*"
jshell --class-path "$share_jars"
```

```
import org.apache.kafka.connect.util.SafeObjectInputStream;
import java.nio.file.Files;
import java.io.File;
import java.nio.charset.StandardCharsets;

File file = new File("/tmp/connect.offsets");
SafeObjectInputStream is = new SafeObjectInputStream(Files.newInputStream(file.toPath()));

Map<byte[], byte[]> raw = (Map<byte[], byte[]>)is.readObject();

for (Map.Entry<byte[], byte[]> mapEntry : raw.entrySet()) {
  byte[] key = mapEntry.getKey();
  byte[] value = mapEntry.getValue();
  String keys = new String(key, StandardCharsets.UTF_8);
  String values = new String(value, StandardCharsets.UTF_8);
  System.out.println(keys);
  System.out.println(values);
}
```

Bring up the two node connect cluster.  Check out the configured plugins.


```
docker-compose up -d connect-1 connect-2
until curl -fsSL localhost:8083 ; do sleep 1; done
curl localhost:8083/connector-plugins | jq
```

```
curl -X POST \
  -H "Content-Type: application/json" \
  --data '{
    "name": "kafka-connect-increment",
    "config": {
      "connector.class": "com.example.IncrementSourceConnector",
      "increments": "1,100",
      "tasks.max": "3",
      "topic.prefix": "seq_",
      "key.converter": "org.apache.kafka.connect.storage.StringConverter",
      "value.converter": "io.confluent.connect.avro.AvroConverter",
      "value.converter.schema.registry.url": "http://schema-registry:8081"
    }
}' http://localhost:8083/connectors
```

View the configuration of tasks / hosts

```
curl http://localhost:8083/connectors/kafka-connect-increment/tasks | jq .
curl http://localhost:8083/connectors/kafka-connect-increment/status | jq . ; curl http://localhost:8084/connectors/kafka-connect-increment/status | jq .
```

View the offsets getting written to a kafka topic. `offset.flush.interval.ms` defaults to 60secs.

```
kafka-console-consumer --bootstrap-server localhost:9092 --topic connect-offsets --property print.key=true --from-beginning
```

---

From [Kafka Connect Administration](https://kafka.apache.org/documentation/#connect_administration)

> "If a Connect worker leaves the group, intentionally or due to a failure, Connect waits for scheduled.rebalance.max.delay.ms before triggering a rebalance. This delay defaults to five minutes (300000ms) to tolerate failures or upgrades of workers without immediately redistributing the load of a departing worker. If this worker returns within the configured delay, it gets its previously assigned tasks in full. However, this means that the tasks will remain unassigned until the time specified by scheduled.rebalance.max.delay.ms elapses. If a worker does not return within that time limit, Connect will reassign those tasks among the remaining workers in the Connect cluster."

From [KIP-415 Configuration Properties](https://cwiki.apache.org/confluence/display/KAFKA/KIP-415%3A+Incremental+Cooperative+Rebalancing+in+Kafka+Connect#KIP415:IncrementalCooperativeRebalancinginKafkaConnect-ConfigurationProperties)

> "The actual delay used by the leader to hold off redistribution of connectors and tasks and maintain imbalance may be less or equal to this value."

---

Sometimes seeing kafka fail to come up.  Sounds like a node already exists in ZK, but this should be a fresh install of ZK?

```
kafka              | [2020-09-17 22:07:46,222] ERROR Error while creating ephemeral at /brokers/ids/101, node already exists and owner '72057799782825985' does not match current session '72057641461940225' (kafka.zk.KafkaZkClient$CheckedEphemeral)
```
