package com.tzahk;

import java.net.InetSocketAddress;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Service
@Scope(value=ConfigurableBeanFactory.SCOPE_SINGLETON)
public class CassandraConfig {
    private final RawProperties props;

    public CassandraConfig(RawProperties props) {
        this.props = props;
    }

    public String datacenter() {
        return props.getDatacenter();
    }

    public InetSocketAddress host() {
        return InetSocketAddress.createUnresolved(
            props.getHostname(),            
            Integer.parseInt(props.getPort()));
    }

    @Component
    @ConfigurationProperties(prefix="cassandra")
    public static class RawProperties {
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
}
