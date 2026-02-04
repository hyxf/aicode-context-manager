package com.aicode.settings;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration class for ignored files and directories.
 * Defined in code as a static list.
 */
public class AICodeIgnoreSettings {

    // Configure your ignore list here
    public static final List<String> IGNORED_NAMES = Arrays.asList(
            ".git",
            ".idea",
            ".gradle",
            "build",
            "target",
            "out",
            "node_modules",
            ".DS_Store",
            "dist",
            ".mvn",
            "venv",
            "__pycache__"
    );

    /**
     * Check if the file/directory name should be ignored.
     */
    public static boolean isIgnored(String name) {
        return IGNORED_NAMES.contains(name);
    }
}
