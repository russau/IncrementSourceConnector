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

The connector these result to build task configuration that assigns the partition groups to tasks.


### Can I record my position in the stream of source data?  

We call this position in the source stream an offset.  Every [SourceRecord](https://docs.confluent.io/current/connect/javadocs/org/apache/kafka/connect/source/SourceRecord.html) returned from a task can set a `sourcePartition` and `sourceOffset`.  Kafka Connect periodically commits the offset values to remember where we are in the source stream.  If the worker hosting a task fails,  the task is started on another worker. At this point the offset can be retrieved and the task can continue copying data from the source stream.

### What is the structure of my data?

The output of your connector can be Java standard types, or a data structure from the [org.apache.kafka.connect.data](https://docs.confluent.io/current/connect/javadocs/org/apache/kafka/connect/data/package-summary.html) package. If your data is can be returned as a structured records you can populate a [Struct][https://docs.confluent.io/current/connect/javadocs/org/apache/kafka/connect/data/Struct.html] and supply a schema.

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

## Module 2: Writing the Source Connector

