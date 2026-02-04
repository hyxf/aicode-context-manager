package com.aicode.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data model for .aicode.json
 * Supports multiple context groups.
 */
public class AICodeConfig {
    public static final String DEFAULT_GROUP = "Default";

    private String activeGroup = DEFAULT_GROUP;
    private Map<String, List<String>> groups = new HashMap<>();

    public AICodeConfig() {
        // Ensure default group always exists
        groups.put(DEFAULT_GROUP, new ArrayList<>());
    }

    public String getActiveGroup() {
        if (activeGroup == null || activeGroup.isEmpty()) {
            activeGroup = DEFAULT_GROUP;
        }
        return activeGroup;
    }

    public void setActiveGroup(String activeGroup) {
        this.activeGroup = activeGroup;
    }

    public Map<String, List<String>> getGroups() {
        if (groups == null) {
            groups = new HashMap<>();
        }
        return groups;
    }

    public void setGroups(Map<String, List<String>> groups) {
        this.groups = groups;
    }

    /**
     * Helper to get paths for current active group
     */
    public List<String> getActivePaths() {
        return getGroups().computeIfAbsent(getActiveGroup(), k -> new ArrayList<>());
    }

    /**
     * Helper to set paths for current active group
     */
    public void setActivePaths(List<String> paths) {
        getGroups().put(getActiveGroup(), paths != null ? paths : new ArrayList<>());
    }
}
