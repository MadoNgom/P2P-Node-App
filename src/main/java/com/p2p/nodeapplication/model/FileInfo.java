package com.p2p.nodeapplication.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class FileInfo {
    private String name;
    private long sizeBytes;
    private LocalDateTime uploadedAt;
    private String nodeId;
}
