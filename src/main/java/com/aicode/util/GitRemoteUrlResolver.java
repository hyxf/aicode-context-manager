package com.aicode.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitLocalBranch;
import git4idea.remote.hosting.GitHostingUrlUtil;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URLEncoder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Resolves a Git repository's origin into the equivalent web URL using the bundled Git4Idea API.
 */
public final class GitRemoteUrlResolver {
    private GitRemoteUrlResolver() {
    }

    public static @Nullable GitRepository findRepository(
            @NotNull Project project,
            @Nullable VirtualFile contextFile
    ) {
        GitRepositoryManager manager = GitRepositoryManager.getInstance(project);
        if (contextFile != null) {
            GitRepository repository = manager.getRepositoryForFileQuick(contextFile);
            if (repository != null) {
                return repository;
            }
        }
        VirtualFile projectDirectory = ProjectUtil.guessProjectDir(project);
        if (projectDirectory != null) {
            GitRepository repository = manager.getRepositoryForFileQuick(projectDirectory);
            if (repository != null) {
                return repository;
            }
        }
        return manager.getRepositories().stream().findFirst().orElse(null);
    }

    public static @Nullable String getOriginUrl(@NotNull GitRepository repository) {
        return repository.getRemotes().stream()
                .filter(remote -> GitRemote.ORIGIN.equals(remote.getName()))
                .map(GitRemote::getFirstUrl)
                .filter(url -> url != null && !url.isBlank())
                .findFirst()
                .orElse(null);
    }

    public static @Nullable String getCurrentBranch(@NotNull GitRepository repository) {
        GitLocalBranch branch = repository.getCurrentBranch();
        return branch == null ? null : branch.getName();
    }

    public static @Nullable String toWebUrl(
            @NotNull String rawUrl,
            @Nullable String branch,
            @Nullable String relativePath
    ) {
        return toWebUrl(rawUrl, branch, relativePath, detectHostingPlatform(rawUrl));
    }

    public static @Nullable String toWebUrl(
            @NotNull String rawUrl,
            @Nullable String branch,
            @Nullable String relativePath,
            @NotNull HostingPlatform platform
    ) {
        URI uri = GitHostingUrlUtil.getUriFromRemoteUrl(rawUrl.trim());
        if (uri == null) {
            return null;
        }
        String host = uri.getHost();
        String path = trimSlashes(uri.getPath() == null ? "" : uri.getPath());
        if (host == null || host.isBlank() || path.isBlank()) {
            return null;
        }

        String rawUrlLowerCase = rawUrl.trim().toLowerCase(Locale.ROOT);
        String webScheme = rawUrlLowerCase.startsWith("http://") ? "http" : "https";
        boolean retainPort = rawUrlLowerCase.startsWith("http://")
                || rawUrlLowerCase.startsWith("https://");
        String authority = retainPort && uri.getPort() >= 0 ? host + ":" + uri.getPort() : host;
        String result = webScheme + "://" + authority + "/" + path;
        if (branch != null && !branch.isBlank()) {
            if (platform == HostingPlatform.UNKNOWN) {
                return null;
            }
            result += platform.getTreeRoute() + encodePath(branch);
            String cleanPath = relativePath == null ? "" : trimSlashes(relativePath.replace('\\', '/'));
            if (!cleanPath.isBlank()) {
                result += "/" + encodePath(cleanPath);
            }
        }
        return result;
    }

    public static @NotNull HostingPlatform detectHostingPlatform(@NotNull String rawUrl) {
        URI uri = GitHostingUrlUtil.getUriFromRemoteUrl(rawUrl.trim());
        String host = uri == null ? null : uri.getHost();
        if (host == null) {
            return HostingPlatform.UNKNOWN;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.contains("gitlab")) {
            return HostingPlatform.GITLAB;
        }
        if (normalizedHost.contains("bitbucket")) {
            return HostingPlatform.BITBUCKET;
        }
        if (normalizedHost.contains("github")) {
            return HostingPlatform.GITHUB;
        }
        if (normalizedHost.contains("gitee")) {
            return HostingPlatform.GITEE;
        }
        if (normalizedHost.contains("codeup")) {
            return HostingPlatform.CODEUP;
        }
        return HostingPlatform.UNKNOWN;
    }

    public static @Nullable String getHost(@NotNull String rawUrl) {
        URI uri = GitHostingUrlUtil.getUriFromRemoteUrl(rawUrl.trim());
        return uri == null ? null : uri.getHost();
    }

    private static @NotNull String encodePath(@NotNull String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%2F", "/")
                .replace("%2f", "/");
    }

    private static @NotNull String trimSlashes(@NotNull String value) {
        return value.replaceAll("^/+|/+$", "");
    }

    public enum HostingPlatform {
        GITHUB("/tree/"),
        GITLAB("/-/tree/"),
        GITEE("/tree/"),
        CODEUP("/tree/"),
        BITBUCKET("/src/"),
        UNKNOWN("");

        private final String treeRoute;

        HostingPlatform(@NotNull String treeRoute) {
            this.treeRoute = treeRoute;
        }

        public @NotNull String getTreeRoute() {
            return treeRoute;
        }
    }
}
