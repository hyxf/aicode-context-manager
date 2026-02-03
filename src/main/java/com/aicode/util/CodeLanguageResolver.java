package com.aicode.util;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility to resolve code language from file extension
 */
public class CodeLanguageResolver {
    private static final Map<String, String> EXTENSION_TO_LANGUAGE = new HashMap<>();

    static {
        EXTENSION_TO_LANGUAGE.put("java", "java");
        EXTENSION_TO_LANGUAGE.put("kt", "kotlin");
        EXTENSION_TO_LANGUAGE.put("kts", "kotlin");
        EXTENSION_TO_LANGUAGE.put("xml", "xml");
        EXTENSION_TO_LANGUAGE.put("json", "json");
        EXTENSION_TO_LANGUAGE.put("yml", "yaml");
        EXTENSION_TO_LANGUAGE.put("yaml", "yaml");
        EXTENSION_TO_LANGUAGE.put("properties", "properties");
        EXTENSION_TO_LANGUAGE.put("gradle", "gradle");
        EXTENSION_TO_LANGUAGE.put("js", "javascript");
        EXTENSION_TO_LANGUAGE.put("ts", "typescript");
        EXTENSION_TO_LANGUAGE.put("py", "python");
        EXTENSION_TO_LANGUAGE.put("go", "go");
        EXTENSION_TO_LANGUAGE.put("rs", "rust");
        EXTENSION_TO_LANGUAGE.put("c", "c");
        EXTENSION_TO_LANGUAGE.put("cpp", "cpp");
        EXTENSION_TO_LANGUAGE.put("h", "c");
        EXTENSION_TO_LANGUAGE.put("hpp", "cpp");
        EXTENSION_TO_LANGUAGE.put("cs", "csharp");
        EXTENSION_TO_LANGUAGE.put("php", "php");
        EXTENSION_TO_LANGUAGE.put("rb", "ruby");
        EXTENSION_TO_LANGUAGE.put("swift", "swift");
        EXTENSION_TO_LANGUAGE.put("scala", "scala");
        EXTENSION_TO_LANGUAGE.put("groovy", "groovy");
        EXTENSION_TO_LANGUAGE.put("sh", "bash");
        EXTENSION_TO_LANGUAGE.put("sql", "sql");
        EXTENSION_TO_LANGUAGE.put("html", "html");
        EXTENSION_TO_LANGUAGE.put("css", "css");
        EXTENSION_TO_LANGUAGE.put("scss", "scss");
        EXTENSION_TO_LANGUAGE.put("md", "markdown");
        EXTENSION_TO_LANGUAGE.put("txt", "text");
    }

    @NotNull
    public static String resolveLanguage(@NotNull String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }

        String extension = fileName.substring(dotIndex + 1).toLowerCase();
        return EXTENSION_TO_LANGUAGE.getOrDefault(extension, "");
    }
}
