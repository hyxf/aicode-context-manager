import os

# 定义文件路径
FILE_PATHS = {
    "icons_class": os.path.join("src", "main", "java", "com", "aicode", "icons", "AICodeIcons.java"),
    "provider_class": os.path.join("src", "main", "java", "com", "aicode", "provider", "AICodeIconProvider.java"),
    "plugin_xml": os.path.join("src", "main", "resources", "META-INF", "plugin.xml")
}

# 1. 新增 AICodeIcons.java
# ------------------------------------------------------------------
icons_class_content = """package com.aicode.icons;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class AICodeIcons {
    public static final Icon LOGO = IconLoader.getIcon("/icons/aicode.svg", AICodeIcons.class);
}
"""

# 2. 新增 AICodeIconProvider.java
# ------------------------------------------------------------------
provider_class_content = """package com.aicode.provider;

import com.aicode.icons.AICodeIcons;
import com.intellij.ide.IconProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AICodeIconProvider extends IconProvider {
    @Override
    public @Nullable Icon getIcon(@NotNull PsiElement element, int flags) {
        if (element instanceof PsiFile) {
            PsiFile psiFile = (PsiFile) element;
            if (".aicode.json".equals(psiFile.getName())) {
                return AICodeIcons.LOGO;
            }
        }
        return null;
    }
}
"""

# 3. 更新 plugin.xml
# ------------------------------------------------------------------
plugin_xml_content = """<idea-plugin>
    <id>com.github.hyxf.aicode-context-manager</id>
    <name>AICode Context Manager</name>
    <vendor email="xchao887@gmail.com" url="https://github.com/hyxf/aicode-context-manager">AICode</vendor>

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
      <li>Multiple Context Groups support</li>
    </ul>
    ]]></description>

    <depends>com.intellij.modules.platform</depends>

    <extensions defaultExtensionNs="com.intellij">
        <notificationGroup
                id="AICode"
                displayType="BALLOON"
                isLogByDefault="true"/>

        <!-- Tool Window -->
        <toolWindow
                id="AICode Context"
                anchor="right"
                icon="/icons/aicode.svg"
                factoryClass="com.aicode.ui.AICodeToolWindowFactory"/>

        <!-- Project Service -->
        <projectService
                serviceImplementation="com.aicode.service.AICodeFileService"/>

        <!-- Icon Provider -->
        <iconProvider implementation="com.aicode.provider.AICodeIconProvider"/>
    </extensions>

    <actions>
        <group id="AICodeGroup" text="AICode" popup="true">
            <!-- 1. Project View: Keep in CutCopyPaste group at the bottom -->
            <add-to-group group-id="CutCopyPasteGroup" anchor="last"/>

            <!-- 2. Editor Tab: Force to FIRST position (Top of the menu) -->
            <add-to-group group-id="EditorTabPopupMenu" anchor="first"/>

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
        <listener class="com.aicode.listener.AICodeFileListener"
                  topic="com.intellij.openapi.vfs.newvfs.BulkFileListener"/>
    </projectListeners>
</idea-plugin>
"""

def write_file(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Updated: {path}")

# 执行文件写入
write_file(FILE_PATHS["icons_class"], icons_class_content)
write_file(FILE_PATHS["provider_class"], provider_class_content)
write_file(FILE_PATHS["plugin_xml"], plugin_xml_content)

print("AICode Custom Icon Provider implementation completed.")