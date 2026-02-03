import os

# 定义工程根目录（假设脚本放在工程根目录）
PROJECT_ROOT = os.path.dirname(os.path.abspath(__file__))

def write_file(relative_path, content):
    full_path = os.path.join(PROJECT_ROOT, relative_path)
    # 确保目录存在
    os.makedirs(os.path.dirname(full_path), exist_ok=True)

    with open(full_path, 'w', encoding='utf-8') as f:
        f.write(content.strip())
    print(f"✅ Replaced: {relative_path}")

# ==========================================
# 1. AddToAICodeAction.java
# 修复：实现 DumbAware，添加 getActionUpdateThread (BGT)
# ==========================================
add_action_content = """
package com.aicode.action;

import com.aicode.service.AICodeFileService;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Action to add file to AICode context
 */
public class AddToAICodeAction extends AnAction implements DumbAware {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);

        if (project == null || file == null || file.isDirectory()) {
            return;
        }

        AICodeFileService service = AICodeFileService.getInstance(project);
        service.addFile(file);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);

        boolean visible = false;
        if (project != null && file != null && !file.isDirectory()) {
            // Prevent adding the configuration file itself
            if (!".aicode.json".equals(file.getName())) {
                AICodeFileService service = AICodeFileService.getInstance(project);
                // Only show "Add" if file is not already in the list
                visible = !service.containsFile(file);
            }
        }

        e.getPresentation().setEnabledAndVisible(visible);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
"""

# ==========================================
# 2. RemoveFromAICodeAction.java
# 修复：实现 DumbAware，添加 getActionUpdateThread (BGT)
# ==========================================
remove_action_content = """
package com.aicode.action;

import com.aicode.service.AICodeFileService;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Action to remove file from AICode context
 */
public class RemoveFromAICodeAction extends AnAction implements DumbAware {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);

        if (project == null || file == null) {
            return;
        }

        AICodeFileService service = AICodeFileService.getInstance(project);
        service.removeFile(file);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);

        boolean visible = false;
        if (project != null && file != null) {
            AICodeFileService service = AICodeFileService.getInstance(project);
            // Only show "Remove" if file is in the list
            visible = service.containsFile(file);
        }

        e.getPresentation().setEnabledAndVisible(visible);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
"""

# ==========================================
# 3. CopyMarkdownAction.java
# 修复：实现 DumbAware，添加 getActionUpdateThread (BGT)
# ==========================================
copy_action_content = """
package com.aicode.action;

import com.aicode.service.AICodeFileService;
import com.aicode.util.ClipboardService;
import com.aicode.util.MarkdownBuilder;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Action to copy AICode context as Markdown to clipboard
 */
public class CopyMarkdownAction extends AnAction implements DumbAware {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        AICodeFileService service = AICodeFileService.getInstance(project);
        List<String> filePaths = service.readFilePaths();

        if (filePaths.isEmpty()) {
            showNotification(project, "No files in AICode context", NotificationType.WARNING);
            return;
        }

        try {
            String markdown = MarkdownBuilder.buildMarkdown(
                    project,
                    filePaths,
                    service::getFileFromPath
            );

            ClipboardService.copyToClipboard(markdown);

            String message = String.format("AICode Markdown copied to clipboard (%d files)", filePaths.size());
            showNotification(project, message, NotificationType.INFORMATION);

        } catch (Exception ex) {
            showNotification(project, "Failed to export Markdown: " + ex.getMessage(), NotificationType.ERROR);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);

        boolean visible = false;
        if (project != null && file != null && !file.isDirectory()) {
            // Only show for .aicode.json file
            visible = ".aicode.json".equals(file.getName());
        }

        e.getPresentation().setEnabledAndVisible(visible);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    private void showNotification(@NotNull Project project, @NotNull String content, @NotNull NotificationType type) {
        Notification notification = new Notification(
                "AICode",
                "AICode Context Manager",
                content,
                type
        );
        Notifications.Bus.notify(notification, project);
    }
}
"""

# ==========================================
# 4. plugin.xml
# 修复：将错误的 VirtualFileListener 修正为 BulkFileListener
# ==========================================
plugin_xml_content = """
<idea-plugin>
    <id>com.aicode.context-manager</id>
    <name>AICode Context Manager</name>
    <vendor email="support@aicode.com" url="https://aicode.com">AICode</vendor>

    <description><![CDATA[
    <h2>AICode Context Manager</h2>
    <p>Manage code context files for AI assistance with one-click Markdown export.</p>
    <br/>
    <h3>Features:</h3>
    <ul>
      <li>Add/Remove files to AI context via right-click menu</li>
      <li>Visualize context files in Tool Window</li>
      <li>Support multi-module projects</li>
      <li>Auto-sync file changes (rename, move, delete)</li>
      <li>Export all context files as Markdown code package</li>
      <li>Undo support for all operations</li>
    </ul>
    ]]></description>

    <depends>com.intellij.modules.platform</depends>
    <depends>com.intellij.modules.java</depends>

    <extensions defaultExtensionNs="com.intellij">
        <!-- Tool Window -->
        <toolWindow
                id="AICode Context"
                anchor="right"
                icon="/icons/aicode.svg"
                factoryClass="com.aicode.ui.AICodeToolWindowFactory"/>

        <!-- Project Service -->
        <projectService
                serviceImplementation="com.aicode.service.AICodeFileService"/>
    </extensions>

    <actions>
        <!-- Project View Context Menu Group -->
        <group id="AICodeGroup" text="AICode" popup="true">
            <add-to-group group-id="ProjectViewPopupMenu" anchor="last"/>

            <action id="com.aicode.action.AddToAICodeAction"
                    class="com.aicode.action.AddToAICodeAction"
                    text="Add to AICode"
                    description="Add file to AICode context">
            </action>

            <action id="com.aicode.action.RemoveFromAICodeAction"
                    class="com.aicode.action.RemoveFromAICodeAction"
                    text="Remove from AICode"
                    description="Remove file from AICode context">
            </action>

            <action id="com.aicode.action.CopyMarkdownAction"
                    class="com.aicode.action.CopyMarkdownAction"
                    text="Copy as Markdown"
                    description="Export AICode context as Markdown">
            </action>
        </group>
    </actions>

    <projectListeners>
        <!-- Updated to BulkFileListener to match Java implementation -->
        <listener class="com.aicode.listener.AICodeFileListener"
                  topic="com.intellij.openapi.vfs.newvfs.BulkFileListener"/>
    </projectListeners>
</idea-plugin>
"""

def main():
    print("🚀 Starting direct file replacement...")

    # 路径映射
    files_to_update = {
        "src/main/java/com/aicode/action/AddToAICodeAction.java": add_action_content,
        "src/main/java/com/aicode/action/RemoveFromAICodeAction.java": remove_action_content,
        "src/main/java/com/aicode/action/CopyMarkdownAction.java": copy_action_content,
        "src/main/resources/META-INF/plugin.xml": plugin_xml_content
    }

    for path, content in files_to_update.items():
        write_file(path, content)

    print("\n🎉 All files updated successfully!")
    print("Please run './gradlew buildPlugin' to rebuild.")

if __name__ == "__main__":
    main()