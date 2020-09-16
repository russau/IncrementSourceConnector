package com.example;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IncrementSourceTask extends SourceTask {
	private static final Logger log = LoggerFactory.getLogger(IncrementSourceTask.class);
	public static final String INCREMENT_FIELD = "increment";
	public  static final String POSITION_FIELD = "position";
	private static final Schema VALUE_SCHEMA = SchemaBuilder.struct()
	.name("sequence")
	.field("threadId", Schema.INT64_SCHEMA)
	.field("hostname", Schema.STRING_SCHEMA)
	.field("value", Schema.INT64_SCHEMA)
	.build();
	
	// a map of increment and current offset we are up to
	Hashtable<Integer,Long> offsets = new Hashtable<Integer,Long>();
	private String topicPrefix = null;
	int[] increments;

	
	@Override
	public String version() {
		return new IncrementSourceConnector().version();
	}

	@Override
	public void start(Map<String, String> props) {
			topicPrefix = props.get(IncrementSourceConnector.TOPIC_PREFIX_CONFIG);
			String incrementsString = props.get(IncrementSourceConnector.INCREMENTS_CONFIG);
			increments = Arrays.stream(incrementsString.split(",")).mapToInt(Integer::parseInt).toArray();
			// initialize offsets at zero
			for (Integer increment : increments) {
				offsets.put(increment, 0L);
			}
	}

	@Override
	public List<SourceRecord> poll() throws InterruptedException {
			log.info("!!!!!!!! POLLING-one !!!!!!!!!!!!!!");
			long threadId = Thread.currentThread().getId();
			String hostname = "";
			try {
				hostname = InetAddress.getLocalHost().getHostName();
			} catch (UnknownHostException ex) {
				hostname = "unknown";
			}

			ArrayList<SourceRecord> records = new ArrayList<>();

			for (Integer increment : increments) {
				Long offset = offsets.get(increment);

				Map<String, Object> storedOffset = context.offsetStorageReader().offset(Collections.singletonMap(INCREMENT_FIELD, increment));
				if (offset == 0 && storedOffset != null) {
					// we have a stored offset, let's use this one
					offset = (Long)storedOffset.get(POSITION_FIELD);
					log.info("We found an offset for increment {} value {}", increment, offset);
				}

				String topic = topicPrefix + increment;
				Long value = offset * increment;
	
				Struct struct = new Struct(VALUE_SCHEMA)
					.put("threadId", threadId)
					.put("hostname", hostname)
					.put("value", value);
	
				records.add(new SourceRecord(offsetKey(increment), offsetValue(offset), topic, null,
										null, null, VALUE_SCHEMA, struct, System.currentTimeMillis()));
				offsets.put(increment, offset + 1);
			}

			log.info("!!!!!!!! SLEEPING-one !!!!!!!!!!!!!!");

			synchronized (this) {
					this.wait(1000);
			}
			return records;
	}


	@Override
	public void stop() {
			log.trace("Stopping");
	}

	private Map<String, Integer> offsetKey(int increment) {
			return Collections.singletonMap(INCREMENT_FIELD, increment);
	}

	private Map<String, Long> offsetValue(Long pos) {
			return Collections.singletonMap(POSITION_FIELD, pos);
	}

}