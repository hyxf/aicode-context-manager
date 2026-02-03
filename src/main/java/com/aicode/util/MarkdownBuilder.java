package com.aicode.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Utility to build Markdown export from file list
 */
public class MarkdownBuilder {

    @NotNull
    public static String buildMarkdown(
            @NotNull Project project,
            @NotNull List<String> filePaths,
            @NotNull VirtualFileProvider fileProvider
    ) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("# AICode Context Export\n\n");
        sb.append("> Generated from .aicode.json  \n");
        sb.append("> Project: ").append(project.getName()).append("  \n");
        sb.append("> File Count: ").append(filePaths.size()).append("  \n\n");
        sb.append("---\n\n");

        // File sections
        for (String relativePath : filePaths) {
            VirtualFile file = fileProvider.getFile(relativePath);

            sb.append("## 📄 ").append(relativePath).append("\n\n");

            if (file == null || !file.exists()) {
                sb.append("⚠️ **Missing File**\n\n");
                sb.append("---\n\n");
                continue;
            }

            String language = CodeLanguageResolver.resolveLanguage(file.getName());
            sb.append("```").append(language).append("\n");

            try {
                String content = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
                sb.append(content);
                if (!content.endsWith("\n")) {
                    sb.append("\n");
                }
            } catch (IOException e) {
                sb.append("⚠️ Error reading file: ").append(e.getMessage()).append("\n");
            }

            sb.append("```\n\n");
            sb.append("---\n\n");
        }

        return sb.toString();
    }

    /**
     * Interface for providing VirtualFile (for testability)
     */
    public interface VirtualFileProvider {
        @Nullable
        VirtualFile getFile(@NotNull String relativePath);
    }
}
