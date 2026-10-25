package com.modl.selfhost.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "uploads")
public class UploadRecord {
    @Id
    private String id;

    private String filename;
    private String originalName;
    private long size;
    private String url;
    private String ownerId;

    public UploadRecord() {}

    public UploadRecord(String filename, String originalName, long size, String url, String ownerId) {
        this.filename = filename;
        this.originalName = originalName;
        this.size = size;
        this.url = url;
        this.ownerId = ownerId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
}
