import os
import sys

# 修改 plugin.xml
# 变更策略：
# 将 <add-to-group group-id="EditorTabPopupMenu" ... />
# 修改为 anchor="first"
# 强制放在菜单的第一位，这样肯定在 Close 的上面。

PLUGIN_XML_CONTENT = r"""<idea-plugin>
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
        <group id="AICodeGroup" text="AICode" popup="true">
            <!-- 1. Project View: Keep in CutCopyPaste group at the bottom -->
            <add-to-group group-id="CutCopyPasteGroup" anchor="last"/>

            <!-- 2. Editor Tab: Force to FIRST position (Top of the menu) -->
            <!-- This guarantees it appears above 'Close' which is usually the first standard item -->
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

def main():
    target_path = "src/main/resources/META-INF/plugin.xml"

    # 路径跨平台处理
    full_path = os.path.join(*target_path.split("/"))

    if not os.path.exists(os.path.dirname(full_path)):
        print(f"错误: 找不到目录 {os.path.dirname(full_path)}")
        return

    try:
        with open(full_path, "w", encoding="utf-8") as f:
            f.write(PLUGIN_XML_CONTENT)
        print(f"成功更新: {full_path}")
        print("\n已修改为 anchor=\"first\"。")
        print("请重新构建插件 (./gradlew buildPlugin) 并运行，AICode 应该会出现在 Tab 右键菜单的最顶部。")
    except IOError as e:
        print(f"写入失败 {full_path}: {e}")

if __name__ == "__main__":
    main()