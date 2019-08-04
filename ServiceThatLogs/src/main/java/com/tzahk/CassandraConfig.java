package com.tzahk;

import java.net.InetSocketAddress;

import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service
@Scope(value=ConfigurableBeanFactory.SCOPE_SINGLETON)
public class CassandraConfig {
    private final RawCassandraProperties props;

    public CassandraConfig(RawCassandraProperties props) {
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
}
