package com.aicode.feature.git.action;

/** Opens the current Git branch on the origin web site. */
public final class OpenGitRemoteBranchAction extends OpenGitRemoteAction {
    public OpenGitRemoteBranchAction() {
        super(Target.BRANCH);
    }
}
