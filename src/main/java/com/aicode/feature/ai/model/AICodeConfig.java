package com.aicode.feature.ai.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data model for .aicode.json
 * Supports multiple context groups.
 * Optimized with LinkedHashMap to preserve order in JSON.
 */
public class AICodeConfig {
    public static final String DEFAULT_GROUP = "Default";

    private String activeGroup = DEFAULT_GROUP;
    private Map<String, List<String>> groups = new LinkedHashMap<>();

    public AICodeConfig() {
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
            groups = new LinkedHashMap<>();
        }
        return groups;
    }

    public void setGroups(Map<String, List<String>> groups) {
        this.groups = groups;
    }

    public List<String> getActivePaths() {
        return getGroups().computeIfAbsent(getActiveGroup(), k -> new ArrayList<>());
    }

    public void setActivePaths(List<String> paths) {
        getGroups().put(getActiveGroup(), paths != null ? paths : new ArrayList<>());
    }
}
