## Module 1: Designing a Source Connectors

* Source connectors vs sink connectors: To copy data between Kafka and another system, users instantiate Kafka Connectors for the systems they want to pull data from or push data to. Connectors come in two flavors: SourceConnectors, which import data from another system, and SinkConnectors, which export data to another system.
* Examples of source connectors: 
 * FileStreamSource https://docs.confluent.io/current/connect/filestream_connector.html#filesource-connector
 * JdbcSourceConnector https://docs.confluent.io/current/connect/kafka-connect-jdbc/source-connector/index.html
 * ActiveMQSourceConnector https://docs.confluent.io/current/connect/kafka-connect-activemq/index.html

To write a source connector we implement classes for two interfaces [SourceConnector](https://docs.confluent.io/current/connect/javadocs/org/apache/kafka/connect/source/SourceConnector.html) and [SourceTask](https://docs.confluent.io/current/connect/javadocs/org/apache/kafka/connect/source/SourceTask.html).

* SourceConnector - configuration for accessing the source data. Breaking up the work into tasks that can be run in parallel on the connect workers.  For example, a JdbcSourceConnector may be copying data from multiple tables. The connector is written to assign the tables to multiple tasks.  Conversely the [FileStreamSourceConnector](https://github.com/apache/kafka/blob/2.6/connect/file/src/main/java/org/apache/kafka/connect/file/FileStreamSourceConnector.java#L80) always creates one task as this consumption of a single file will only ever be consumed by a single task.
* SourceTask - the implementation that copies the subset of data from our source. Using JDBC as an example a task will be assigned one or more tables to copy data from.

### Can I *partition* the source data so that multiple tasks can copy a subset of data in parallel?

Using the example we used above: the JdbcSourceConnector can break up the work when copying streams of data from multiple tables.  Because this is such a common pattern in connectors we can use the [ConnectorUtils#groupPartitions](https://docs.confluent.io/current/connect/javadocs/org/apache/kafka/connect/util/ConnectorUtils.html#groupPartitions-java.util.List-int-) method to assign groups of partitions to tasks.

For example: we want to group 6 partitions (database tables in this case) across 3 tasks in the cluster.

``` java
List<String> tableNames = Arrays.asList("departments", "employees", "dept_emp", "dept_manager", "titles", "salaries");
List<List<String>> groups = ConnectorUtils.groupPartitions(tableNames, 3);
System.out.println(groups);
```

Returns:

```
[[departments, employees], [dept_emp, dept_manager], [titles, salaries]]
```

The connector uses these result to build task configuration that assigns the partition groups to tasks.


### Can I record my position in the stream of source data?  

We call this position in the source stream an offset.  Every [SourceRecord](https://docs.confluent.io/current/connect/javadocs/org/apache/kafka/connect/source/SourceRecord.html) returned from a task can set a `sourcePartition` and `sourceOffset`.  Kafka Connect periodically commits the offset values to remember where we are in the source stream.  If the worker hosting a task fails,  the task is started on another worker. At this point the offset can be retrieved and the task can continue copying data from the source stream.

### What is the structure of my data?

The output of your connector can be Java standard types, or a data structure from the [org.apache.kafka.connect.data](https://docs.confluent.io/current/connect/javadocs/org/apache/kafka/connect/data/package-summary.html) package. If your data is can be returned as a structured records you can populate a [Struct](https://docs.confluent.io/current/connect/javadocs/org/apache/kafka/connect/data/Struct.html) and supply a schema.

``` java
Schema schema = SchemaBuilder.struct().name("com.example.Person")
		.field("name", Schema.STRING_SCHEMA).field("age", Schema.INT32_SCHEMA).build()
Struct struct = new Struct(schema).put("name", "Bobby McGee").put("age", 21)
``` 

The serialization of your output is handled by the configured converter.  In the following exercise you will configure the `AvroConverter`.  The `AvroConverter` registers your schemaa with Confluent Schema Registry and is used to write the Avro serialized bytes to your Kafka topic.

## Module 2: The IncrementSourceConnector

Let's look at the contrived source connector we are building in this exercise.  The connector has been kept very simple to help with the education. While you are going through this exercise you can think about the sources of data you are currently working with, and how these concepts would apply.

* The contrived IncrementSourceConnector.  What does it do, what is the configuration?
 * Creates incrementing number sequences we write to Kafka topics
 * Supports partitioning - multiple sequences across multiple tasks
 * Supports offset management - connect remembers were we are in our sequence, new tasks can start from the saved "offset" position in the sequence.


```
name=kafka-connect-increment
connector.class=com.example.IncrementSourceConnector
increments=1,100
tasks.max=1
topic.prefix=seq_
```

## Module 3: Writing the Source Connector

* SourceConnector
 * `String version()`
 * `void start(Map<String, String> props)`
 * `void start(Map<String, String> props)`
 * `Class<? extends Task> taskClass()`
 * `List<Map<String, String>> taskConfigs(int maxTasks)`
 * `void stop()`
 * `ConfigDef config()`

* SourceTask
 * `void start(Map<String, String> props)`
 * `List<SourceRecord> poll()`
 * `void stop()`

Run the connector standalone `connect-standalone connect-avro-standalone.properties connector1.properties`.  Observe: `tasks.max=1` - we assign both sequences to the same task: `INFO Task config: (prefix: seq_, increments 1,100)`.  Observe: the thread id is the same for both sequences.

Stop  `connect-standalone` with Ctrl-C and increase the `tasks.max` to 2.  Observe: sequence numbers continued on from where we stopped.  Observe: two config objects are returned `INFO Task config: (prefix: seq_, increments 1)` and `INFO Task config: (prefix: seq_, increments 100)`.  Observe: two different thread ids.

## Module 4: Connect Cluster and Rebalance

* Turning on two workers in a Connect cluster.

* Observe one worker is the leader.

```
$ docker-compose logs | grep "leaderUrl="
...leaderUrl='http://connect-2:8084/'...
```

* Using the REST API to create a connector

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

* Observe: connect tasks running on different hosts.

```
$ kafka-avro-console-consumer --bootstrap-server localhost:9092 --whitelist 'seq_.+' --from-beginning
{"threadId":119,"hostname":"1a828236487f","value":7400}
{"threadId":111,"hostname":"d2970215cdb0","value":74}
```

* Observe: Offsets are getting commited. `offset.flush.interval.ms` defaults to 60secs.

```
$ kafka-console-consumer --bootstrap-server localhost:9092 --topic connect-offsets --property print.key=true --from-beginning
["kafka-connect-increment",{"increment":1}]	{"position":3471}
["kafka-connect-increment",{"increment":100}]	{"position":3472}
```

* Get the tasks status from both connect workers. Observe: where the connectors and tasks live.

```
$ curl http://localhost:8083/connectors/kafka-connect-increment/status | jq . ; curl http://localhost:8084/connectors/kafka-connect-increment/status | jq .

{
  "name": "kafka-connect-increment",
  "connector": {
    "state": "RUNNING",
    "worker_id": "connect-2:8084"
  },
  "tasks": [
    {
      "id": 0,
      "state": "RUNNING",
      "worker_id": "connect-2:8084"
    },
    {
      "id": 1,
      "state": "RUNNING",
      "worker_id": "connect-1:8083"
    }
  ],
  "type": "source"
}
```

* Stop one of the connect workers.

```
docker-compose stop connect-1
```

* Keep checking the status of the connector on both nodes using the curl statement you used previously.  It may take up to 5 minutes for the `UNASSIGNED` tasks/connector to rebalance onto the remaining node.


From [Kafka Connect Administration](https://kafka.apache.org/documentation/#connect_administration)

> "If a Connect worker leaves the group, intentionally or due to a failure, Connect waits for scheduled.rebalance.max.delay.ms before triggering a rebalance. This delay defaults to five minutes (300000ms) to tolerate failures or upgrades of workers without immediately redistributing the load of a departing worker. If this worker returns within the configured delay, it gets its previously assigned tasks in full. However, this means that the tasks will remain unassigned until the time specified by scheduled.rebalance.max.delay.ms elapses. If a worker does not return within that time limit, Connect will reassign those tasks among the remaining workers in the Connect cluster."


