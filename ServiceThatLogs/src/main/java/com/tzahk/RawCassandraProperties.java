package com.tzahk;

import org.springframework.stereotype.Component;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Prefer CassandraConfig */
@Component
@ConfigurationProperties(prefix="cassandra")
public class RawCassandraProperties {
    private String hostname;
    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }

    private String port;
    public String getPort() { return port; }
    public void setPort(String port) { this.port = port; }

    private String datacenter;
    public String getDatacenter() { return datacenter; }
    public void setDatacenter(String dc) { this.datacenter = dc; }
}
