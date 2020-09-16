package com.example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.source.SourceConnector;
import org.apache.kafka.connect.util.ConnectorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IncrementSourceConnector extends SourceConnector {

  private static Logger log = LoggerFactory.getLogger(IncrementSourceConnector.class);
  public static final String TOPIC_PREFIX_CONFIG = "topic.prefix";
  public static final String DEFAULT_TOPIC_PREFIX = "increment_";
  public static final String INCREMENTS_CONFIG = "increments";

  private static final ConfigDef CONFIG_DEF = new ConfigDef()
      .define(TOPIC_PREFIX_CONFIG, Type.STRING, DEFAULT_TOPIC_PREFIX, Importance.LOW,
          "Prefix for topics to publish data to")
      .define(INCREMENTS_CONFIG, Type.LIST, Importance.HIGH, "A list of increments for the sequences");

  private List<String> increments;
  private String topicPrefix;

  @Override
  public String version() {
    return "0.0.1";
  }

  @Override
  public void start(Map<String, String> props) {
    /***********************
     * The connector has been started, retrieve the settings passed in the props 
     * 
     **********************/
    log.info("STARTING IncrementSourceConnector");
    final AbstractConfig parsedConfig = new AbstractConfig(CONFIG_DEF, props);
    increments = parsedConfig.getList(INCREMENTS_CONFIG);

    // config validation: all the increments need to be integers
    try {
      for (String s : increments) {
        Integer.parseInt(s);
      }
    } catch (java.lang.NumberFormatException e) {
      throw new ConfigException("'increments' must be a collection of integers");
    }

    topicPrefix = parsedConfig.getString(TOPIC_PREFIX_CONFIG);
  }

  @Override
  public Class<? extends Task> taskClass() {
    return IncrementSourceTask.class;
  }

  @Override
  public List<Map<String, String>> taskConfigs(int maxTasks) {
    /***********************
     * We have been passed a maxTasks that we allocate. We determine the actual number
     * of tasks to "spread" the list of sequences across.  We can do this by returning
     * configuration for the number of tasks we want.
     * 
     * We will only grow to the min of number of increments and max tasks.  Eg. 5 increments, and 3 maxTasks
     * results in 3 tasks each with 2+2+1 increments
     **********************/
    final int numGroups = Math.min(increments.size(), maxTasks);

    
    /***********************
     * groupPartitions groups our increments and speads them evenly over our groups
     **********************/
    final List<List<String>> incrementsGrouped = ConnectorUtils.groupPartitions(increments, numGroups);
    final List<Map<String, String>> taskConfigs = new ArrayList<>();
    for (List<String> taskIncrements : incrementsGrouped) {
      final Map<String, String> taskProps = new HashMap<>();
      taskProps.put(TOPIC_PREFIX_CONFIG, topicPrefix);
      // we can pass in the increments for each group/task
      taskProps.put(INCREMENTS_CONFIG, String.join(",", taskIncrements));
      taskConfigs.add(taskProps);
      log.info("Task config: (prefix: {}, increments {})", topicPrefix, String.join(",", taskIncrements));
    }
    return taskConfigs;
  }

  @Override
  public void stop() {
    log.info("STOPPING IncrementSourceConnector");
  }

  @Override
  public ConfigDef config() {
    return CONFIG_DEF;
  }
}