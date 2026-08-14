package com.aicode.feature.git.action;

import com.aicode.feature.git.model.ChangelogData;
import com.aicode.feature.git.service.GitChangelogService;
import com.aicode.feature.git.ui.ChangelogPreviewDialog;
import com.aicode.feature.git.util.ChangelogBuilder;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Creates or refreshes a managed CHANGELOG.md section from local Git tags and commits. */
public final class CreateChangelogAction extends AnAction implements DumbAware {
    private static final Logger LOG = Logger.getInstance(CreateChangelogAction.class);
    private static final String FILE_NAME = "CHANGELOG.md";

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }
        GitRepository repository = chooseRepository(project, event.getData(CommonDataKeys.VIRTUAL_FILE));
        if (repository != null) {
            loadHistory(project, repository);
        }
    }

    private static void loadHistory(@NotNull Project project, @NotNull GitRepository repository) {
        new Task.Backgroundable(project, "Reading Git History", true) {
            private ChangelogData data;
            private String error;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    data = new GitChangelogService().read(project, repository);
                } catch (ProcessCanceledException exception) {
                    throw exception;
                } catch (VcsException | RuntimeException exception) {
                    LOG.warn("Failed to read Git history for changelog generation", exception);
                    error = safeMessage(exception);
                }
            }

            @Override
            public void onSuccess() {
                if (project.isDisposed()) {
                    return;
                }
                if (error != null) {
                    CreateChangelogAction.notify(
                            project,
                            "Failed to read Git history: " + error,
                            NotificationType.ERROR
                    );
                    return;
                }
                preview(project, repository, data);
            }
        }.queue();
    }

    private static void preview(
            @NotNull Project project,
            @NotNull GitRepository repository,
            @NotNull ChangelogData data
    ) {
        VirtualFile existingFile = repository.getRoot().findChild(FILE_NAME);
        Document existingDocument = existingFile == null
                ? null
                : FileDocumentManager.getInstance().getDocument(existingFile);
        if (existingFile != null && existingDocument == null) {
            notify(project, "CHANGELOG.md could not be opened as a text document.", NotificationType.ERROR);
            return;
        }
        String existingContent = existingDocument == null ? null : existingDocument.getText();
        boolean replacingUnmanaged;
        try {
            replacingUnmanaged = existingContent != null
                    && !ChangelogBuilder.hasManagedSection(existingContent);
        } catch (IllegalArgumentException exception) {
            notify(project, exception.getMessage() + ". Fix the markers and generate it again.",
                    NotificationType.ERROR);
            return;
        }
        String content;
        if (existingFile == null || replacingUnmanaged) {
            content = ChangelogBuilder.create(data);
        } else {
            content = ChangelogBuilder.update(existingContent, data);
        }

        long expectedFileStamp = existingFile == null ? -1 : existingFile.getModificationStamp();
        long expectedDocumentStamp = existingDocument == null ? -1 : existingDocument.getModificationStamp();
        ChangelogPreviewDialog dialog = new ChangelogPreviewDialog(project, content, replacingUnmanaged);
        if (dialog.showAndGet()) {
            write(
                    project,
                    repository.getRoot(),
                    existingFile,
                    existingDocument,
                    existingContent,
                    expectedFileStamp,
                    expectedDocumentStamp,
                    dialog.getContent()
            );
        }
    }

    private static void write(
            @NotNull Project project,
            @NotNull VirtualFile repositoryRoot,
            @Nullable VirtualFile existingFile,
            @Nullable Document existingDocument,
            @Nullable String expectedContent,
            long expectedFileStamp,
            long expectedDocumentStamp,
            @NotNull String content
    ) {
        AtomicReference<VirtualFile> writtenFile = new AtomicReference<>();
        AtomicReference<Document> writtenDocument = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        AtomicReference<String> conflict = new AtomicReference<>();
        WriteCommandAction.runWriteCommandAction(project, "Create or Update CHANGELOG.md", null, () -> {
            try {
                VirtualFile file;
                Document document;
                if (existingFile == null) {
                    if (repositoryRoot.findChild(FILE_NAME) != null) {
                        conflict.set("CHANGELOG.md was created while the preview was open. Generate it again.");
                        return;
                    }
                    file = repositoryRoot.createChildData(CreateChangelogAction.class, FILE_NAME);
                    document = FileDocumentManager.getInstance().getDocument(file);
                } else {
                    file = existingFile;
                    document = existingDocument;
                    if (!file.isValid() || document == null
                            || file.getModificationStamp() != expectedFileStamp
                            || document.getModificationStamp() != expectedDocumentStamp
                            || !document.getText().equals(expectedContent)) {
                        conflict.set("CHANGELOG.md changed while the preview was open. Generate it again.");
                        return;
                    }
                }
                if (document == null) {
                    throw new IllegalStateException("CHANGELOG.md could not be opened as a text document");
                }
                document.setText(content);
                writtenFile.set(file);
                writtenDocument.set(document);
            } catch (Exception exception) {
                failure.set(exception);
            }
        });
        if (conflict.get() != null) {
            notify(project, conflict.get(), NotificationType.WARNING);
            return;
        }
        if (failure.get() != null) {
            LOG.warn("Failed to write CHANGELOG.md", failure.get());
            notify(project, "Failed to write CHANGELOG.md: " + safeMessage(failure.get()), NotificationType.ERROR);
            return;
        }
        FileDocumentManager.getInstance().saveDocument(writtenDocument.get());
        FileEditorManager.getInstance(project).openFile(writtenFile.get(), true);
        notify(project, existingFile == null ? "Created CHANGELOG.md." : "Updated CHANGELOG.md.",
                NotificationType.INFORMATION);
    }

    private static @Nullable GitRepository chooseRepository(
            @NotNull Project project,
            @Nullable VirtualFile contextFile
    ) {
        GitRepositoryManager manager = GitRepositoryManager.getInstance(project);
        if (contextFile != null) {
            GitRepository contextual = manager.getRepositoryForFileQuick(contextFile);
            if (contextual != null) {
                return contextual;
            }
        }
        List<GitRepository> repositories = manager.getRepositories().stream()
                .sorted(Comparator.comparing(repository -> repository.getRoot().getPresentableUrl()))
                .toList();
        if (repositories.isEmpty()) {
            notify(project, "No Git repository was detected for this project.", NotificationType.WARNING);
            return null;
        }
        if (repositories.size() == 1) {
            return repositories.get(0);
        }
        String[] options = repositories.stream()
                .map(repository -> repository.getRoot().getPresentableUrl())
                .toArray(String[]::new);
        int selected = Messages.showChooseDialog(
                project,
                "Select the repository whose history will be used.",
                "Select Git Repository",
                Messages.getQuestionIcon(),
                options,
                options[0]
        );
        return selected < 0 ? null : repositories.get(selected);
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        boolean hasRepository = project != null
                && !GitRepositoryManager.getInstance(project).getRepositories().isEmpty();
        event.getPresentation().setVisible(project != null);
        event.getPresentation().setEnabled(hasRepository);
        event.getPresentation().setDescription(hasRepository
                ? "Create or update CHANGELOG.md from local Git tags"
                : "No Git repository was detected for this project");
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    private static @NotNull String safeMessage(@NotNull Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "Unexpected error" : message;
    }

    private static void notify(
            @NotNull Project project,
            @NotNull String message,
            @NotNull NotificationType type
    ) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!project.isDisposed()) {
                Notifications.Bus.notify(new Notification(
                        "AICode",
                        "CHANGELOG.md",
                        message,
                        type
                ), project);
            }
        });
    }
}
