package com.aicode.feature.git.action;

/** Opens the selected file's directory on the current origin branch. */
public final class OpenGitRemoteDirectoryAction extends OpenGitRemoteAction {
    public OpenGitRemoteDirectoryAction() {
        super(Target.DIRECTORY);
    }
}
