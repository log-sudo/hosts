package com.txl.hosts;

public class HostConfig {
    private String name;
    private String content;
    private boolean isActive;

    public HostConfig(String name, String content, boolean isActive) {
        this.name = name;
        this.content = content;
        this.isActive = isActive;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }
}