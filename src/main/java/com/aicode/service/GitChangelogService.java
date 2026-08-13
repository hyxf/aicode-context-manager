package com.aicode.service;

import com.aicode.model.ChangelogData;
import com.aicode.model.ChangelogData.Commit;
import com.aicode.model.ChangelogData.Release;
import com.aicode.model.SemVer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Reads local tags and commit subjects through Git4Idea without contacting a remote. */
public final class GitChangelogService {
    private final Git git;

    public GitChangelogService() {
        git = Git.getInstance();
    }

    public @NotNull ChangelogData read(@NotNull Project project, @NotNull GitRepository repository)
            throws VcsException {
        List<Tag> tags = readTags(project, repository);
        List<Release> releases = new ArrayList<>();

        String latestTag = tags.isEmpty() ? null : tags.get(tags.size() - 1).name();
        List<Commit> unreleased = readCommits(project, repository,
                latestTag == null ? "HEAD" : latestTag + "..HEAD");
        releases.add(new Release("Unreleased", "", unreleased, true));

        for (int index = tags.size() - 1; index >= 0; index--) {
            Tag tag = tags.get(index);
            String range = index == 0 ? tag.name() : tags.get(index - 1).name() + ".." + tag.name();
            releases.add(new Release(
                    tag.name().substring(1),
                    tag.date(),
                    readCommits(project, repository, range),
                    false
            ));
        }
        return new ChangelogData(List.copyOf(releases));
    }

    private @NotNull List<Tag> readTags(@NotNull Project project, @NotNull GitRepository repository)
            throws VcsException {
        GitLineHandler handler = new GitLineHandler(project, repository.getRoot(), GitCommand.TAG);
        handler.addParameters("--merged", "HEAD", "--format=%(refname:short)%09%(creatordate:short)");
        GitCommandResult result = git.runCommand(handler);
        result.throwOnError();

        return result.getOutput().stream()
                .map(GitChangelogService::parseTag)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(Tag::version))
                .toList();
    }

    private @NotNull List<Commit> readCommits(
            @NotNull Project project,
            @NotNull GitRepository repository,
            @NotNull String revision
    ) throws VcsException {
        GitLineHandler handler = new GitLineHandler(project, repository.getRoot(), GitCommand.LOG);
        handler.addParameters("--no-merges", "--pretty=format:%H%x09%s", revision);
        GitCommandResult result = git.runCommand(handler);
        result.throwOnError();

        return result.getOutput().stream()
                .map(GitChangelogService::parseCommit)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    static Tag parseTag(@NotNull String line) {
        int separator = line.indexOf('\t');
        String name = separator < 0 ? line : line.substring(0, separator);
        SemVer version = SemVer.parseTag(name);
        if (version == null) {
            return null;
        }
        String date = separator < 0 ? "" : line.substring(separator + 1).trim();
        return new Tag(name, date, version);
    }

    static Commit parseCommit(@NotNull String line) {
        int separator = line.indexOf('\t');
        if (separator <= 0 || separator == line.length() - 1) {
            return null;
        }
        return new Commit(line.substring(0, separator), line.substring(separator + 1).trim());
    }

    record Tag(@NotNull String name, @NotNull String date, @NotNull SemVer version) {
    }
}
