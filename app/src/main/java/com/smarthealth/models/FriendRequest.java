package com.smarthealth.models;

public class FriendRequest {
    private String id;
    private String fromUid;
    private String fromName;
    private String fromEmail;
    private String toUid;
    private String status; // "pending" | "accepted" | "declined"
    private long timestamp;

    public FriendRequest() {}

    public FriendRequest(String fromUid, String fromName, String fromEmail, String toUid) {
        this.fromUid   = fromUid;
        this.fromName  = fromName;
        this.fromEmail = fromEmail;
        this.toUid     = toUid;
        this.status    = "pending";
        this.timestamp = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFromUid() { return fromUid; }
    public void setFromUid(String f) { this.fromUid = f; }
    public String getFromName() { return fromName; }
    public void setFromName(String f) { this.fromName = f; }
    public String getFromEmail() { return fromEmail; }
    public void setFromEmail(String f) { this.fromEmail = f; }
    public String getToUid() { return toUid; }
    public void setToUid(String t) { this.toUid = t; }
    public String getStatus() { return status; }
    public void setStatus(String s) { this.status = s; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long t) { this.timestamp = t; }
}
