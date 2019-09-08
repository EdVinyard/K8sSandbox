package com.tzahk.message;

import java.util.Map;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service
@Scope(value=ConfigurableBeanFactory.SCOPE_SINGLETON)
public class Config {
    public Properties consumerProperties() {
        final var props = new Properties();
        props.putAll(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            bootstrapServers(),

            ConsumerConfig.GROUP_ID_CONFIG,
            groupId(),

            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            LongDeserializer.class.getName(),

            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class.getName()));
        return props;
    }
    
    // TODO: Derive these values from application properties.
    // TODO: Wrap these in a "Raw" class like the one in Cassandra config class.
    public String bootstrapServers() {
        return "bootstrap.kafka.svc.cluster.local:9092";
    }

    public String topic() {
        return "service-that-logs";
    }

    public String groupId() {
        return "group_id";
    }
}
