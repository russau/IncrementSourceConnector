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
kafka-avro-console-consumer --bootstrap-server kafka:9092 --whitelist 'seq_.+' --from-beginning
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
}' http://connect:8083/connectors
```

View the configuration of tasks / hosts

```
curl http://connect:8083/connectors/kafka-connect-increment/tasks | jq .
curl http://connect:8083/connectors/kafka-connect-increment/status | jq .
```

View the offsets getting written to a kafka topic. `offset.flush.interval.ms` defaults to 60secs.

```
kafka-console-consumer --bootstrap-server kafka:9092 --topic connect-offsets --property print.key=true --from-beginning
```