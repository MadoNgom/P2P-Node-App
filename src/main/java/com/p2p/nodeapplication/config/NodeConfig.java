package com.p2p.nodeapplication.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Data
@ConfigurationProperties(prefix = "node")

public class NodeConfig {
    private String id;          // Unique Node ID
    private String storage;     // Folder local storage
    private List<String> peers; // URL  peers
}
