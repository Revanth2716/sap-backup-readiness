package com.revzz.sap.readiness.domain;

/** One system in the landscape, with the objectives agreed for it. */
public class SystemProfile {

    private String name;
    private String sid;
    private String client;
    private String description;
    private String owner;
    private boolean critical;
    private int rpoHours;
    private int rtoHours;
    private int maxDbGb;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public String getClient() {
        return client;
    }

    public void setClient(String client) {
        this.client = client;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public boolean isCritical() {
        return critical;
    }

    public void setCritical(boolean critical) {
        this.critical = critical;
    }

    public int getRpoHours() {
        return rpoHours;
    }

    public void setRpoHours(int rpoHours) {
        this.rpoHours = rpoHours;
    }

    public int getRtoHours() {
        return rtoHours;
    }

    public void setRtoHours(int rtoHours) {
        this.rtoHours = rtoHours;
    }

    public int getMaxDbGb() {
        return maxDbGb;
    }

    public void setMaxDbGb(int maxDbGb) {
        this.maxDbGb = maxDbGb;
    }
}
