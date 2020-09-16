```
export CLASSPATH=extensions/connect-mediawiki-0.0.1.jar
connect-standalone connect-avro-standalone.properties connector1.properties

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

```
kafkacat -C -b kafka:9092 -t banana -s value=avro -r http://schema-registry:8081 -f 'Topic %t, payload: %s\n' -q
```

https://docs.confluent.io/current/connect/managing/monitoring.html#connect-managing-rest-examples

```
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

```
curl http://connect:8083/connectors/kafka-connect-increment/tasks
curl http://connect:8083/connectors/kafka-connect-increment/status
```
