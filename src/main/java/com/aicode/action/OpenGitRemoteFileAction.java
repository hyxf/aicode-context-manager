package com.aicode.action;

/** Opens the selected file on the current origin branch. */
public final class OpenGitRemoteFileAction extends OpenGitRemoteAction {
    public OpenGitRemoteFileAction() {
        super(Target.FILE);
    }
}
