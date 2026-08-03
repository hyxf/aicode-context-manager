# AICode Context Manager 完整功能规格

> 当前源码版本：v1.2.0  
> 目标：用于复刻一个用户可感知功能一致的 VS Code 插件。  
> 本文以当前源码真实行为为准，而不只依据 README。

## 1. 核心数据模型

插件在项目根目录维护 `.aicode.json`。

当前格式：

```json
{
  "activeGroup": "Default",
  "groups": {
    "Default": [
      "src/main/java/App.java",
      "README.md"
    ],
    "Backend": [
      "server/src/index.ts"
    ]
  }
}
```

规则：

- 路径均为相对项目根目录的 `/` 分隔路径。
- 默认分组名固定为 `Default`。
- `activeGroup` 表示当前激活组。
- 所有添加、移除、导出、文件树和编辑器提示，只针对当前激活组。
- 分组使用有序 Map，文件路径使用数组。
- `.aicode.json` 不存在时，任何首次读取都会自动创建默认配置。
- 支持旧格式迁移：

```json
[
  "src/App.java",
  "README.md"
]
```

旧数组会被作为 `Default` 组读取，但不会立即回写新格式；下一次修改时才会保存为新格式。

- 空文件、非法 JSON、合法但无有效分组的 JSON，会在内存中回退到默认配置，不显示错误。
- 配置保存为 UTF-8、Gson pretty-print JSON。

VS Code 对应实现建议：

- 使用 `workspace.workspaceFolders[0]` 作为项目根目录。
- 使用 `workspace.fs.readFile/writeFile` 操作配置。
- 内部维护 `AICodeConfig` 和当前组路径集合。
- 首次执行相关命令或创建 TreeView 时执行 `ensureConfig()`。

## 2. 文件添加

### 2.1 入口

“AICode → Add to AICode” 出现在：

- IDEA Project View 右键菜单。
- 编辑器标签页右键菜单。
- Windows/Linux 快捷键：`Ctrl+Shift+Z`。
- macOS 快捷键：`Cmd+Shift+Z`。

VS Code 可映射为 Explorer 的 `view/item/context`、编辑器的 `editor/title/context`、命令面板和 `keybindings`。

### 2.2 文件添加规则

- 支持单文件和多选文件。
- 添加到当前激活组。
- 路径转为相对项目根目录路径。
- 已存在的路径不会重复添加。
- `.aicode.json` 自身不能添加。
- 二进制文件不能添加。
- 忽略名单中的文件不能添加。
- 项目根目录不能添加。
- 选中项全部不可添加时，菜单隐藏。

### 2.3 目录添加规则

- 支持选中一个或多个目录并递归添加。
- 遍历全部后代文件。
- 跳过二进制文件。
- 跳过 `.aicode.json`。
- 遇到忽略目录时，不再遍历其子目录。
- 一次批量写入配置，避免逐文件刷新。
- 目录菜单会始终显示为可添加，即使目录内所有文件都已加入；执行后可能没有变化。

### 2.4 固定忽略名单

仅按文件或目录的完整 basename 判断，不支持 glob，也不读取 `.gitignore`：

```text
.git
.idea
.gradle
build
target
out
node_modules
.DS_Store
dist
.mvn
venv
__pycache__
```

匹配大小写敏感。

## 3. 文件移除

“AICode → Remove from AICode” 同样位于 Project View 和编辑器标签页菜单。

快捷键：

- Windows/Linux：`Ctrl+Shift+X`
- macOS：`Cmd+Shift+X`

行为：

- 只操作当前激活组。
- 支持多选。
- 单文件只有在当前组中时才显示 Remove。
- 选中目录时，移除所有以 `目录相对路径/` 开头的已跟踪路径。
- 项目根目录不能移除。
- 目录下只要存在至少一个已跟踪文件，Remove 菜单就会显示。
- 批量移除只保存一次配置。

## 4. 多上下文组

右侧 “AICode Context” 面板顶部有分组选择器。

### 4.1 切换分组

- 分组名按字母排序展示。
- 当前分组带选中图标。
- 切换后写入 `.aicode.json` 的 `activeGroup`。
- 文件树、菜单状态、横幅、导出内容立即切换到新组。

### 4.2 新建分组

- 输入框标题为 `New Group`。
- 自动 trim 首尾空格。
- 空名称不执行。
- 名称重复时显示错误通知。
- 创建空组并立即激活。

### 4.3 重命名当前分组

- 默认输入值为当前名称。
- 空值或相同名称不执行。
- 重名显示错误通知。
- 重命名后保持当前激活状态。
- 由于实现是删除旧键再插入新键，重命名后的组会移动到 JSON 分组顺序末尾。

### 4.4 复制当前分组

- 默认新名称：`当前组名 Copy`。
- 文件路径数组复制为独立新数组。
- 创建后立即激活副本。
- 重名显示错误通知。

### 4.5 删除当前分组

- 删除前显示确认对话框。
- UI 只有分组数量大于 1 时才启用删除。
- 删除激活组后，激活剩余 Map 中的第一个组。
- 服务层仍处理“删除唯一组”的情况：重建一个空的 `Default` 组。

VS Code 中可使用 Quick Pick 切换组，并在 TreeView 标题栏提供新建、重命名、复制和删除按钮。

## 5. AICode Context 工具窗口

IDEA 中注册在右侧工具窗口，名称为 `AICode Context`，使用插件 Logo。

VS Code 对应为 Activity Bar View Container、`TreeView` 和自定义 AICode 图标。

### 5.1 文件树结构

文件数组会转换为虚拟目录树：

```text
Group: Default
└── src (*)
    └── main
        ├── App.java
        └── Service.java
```

行为：

- 根节点显示 `Group: 当前组名`。
- 文件按完整相对路径排序后构建树。
- 初始化和刷新后默认全部展开。
- 单选模式。
- 文件节点使用其文件类型图标。
- 目录使用文件夹图标。
- 不存在的文件使用 Unknown 图标、错误色名称和 `(missing)`。
- 双击有效文件节点，在编辑器中打开。
- 双击目录或缺失文件无操作。
- 空组显示 `No files in this context group.`。

### 5.2 目录部分选中标志

若树中的某个目录在磁盘上还存在未加入当前组的文本文件，目录名后显示 `(*)`。

检测规则与目录添加一致：

- 递归扫描。
- 忽略固定忽略名单。
- 忽略二进制文件。
- 忽略 `.aicode.json`。
- 找到第一个未跟踪文件即停止。
- 只有已经因某个已跟踪路径而出现在树中的目录，才会显示 `(*)`；完全未跟踪的目录不会显示。

### 5.3 树节点右键菜单

文件节点：

- `Remove File from Context`

目录节点：

- `Remove Directory from Context`
- 若带 `(*)`，额外显示 `Add Missing Files`

`Add Missing Files` 会递归添加该目录下所有尚未跟踪的合格文件，然后通知新增数量。

目录移除会递归收集当前树节点下的叶子路径。当前实现逐个路径保存，因此删除一个包含 N 个文件的目录会触发 N 次配置写入和 UI 刷新；VS Code 复刻不必复制这个性能缺陷。

## 6. 工具栏完整功能

工具栏从左到右：

1. 当前上下文组选择器。
2. Open Configuration。
3. Copy as Markdown。
4. Copy File List。
5. Expand All。
6. Collapse All。
7. Refresh。
8. Show/Hide Editor Banner。

### 6.1 Open Configuration

- 获取或创建项目根目录 `.aicode.json`。
- 在编辑器打开并聚焦。

### 6.2 Expand All / Collapse All

- 展开整棵树。
- Collapse All 保留根层级。

### 6.3 Refresh

- 重新读取配置。
- 重新扫描缺失文件状态。
- 重建并全部展开文件树。

## 7. Copy File List

工具栏提供“复制当前组文件列表”。

输出格式：

```text
@src/main/java/App.java
@src/main/java/Service.java
@README.md
```

规则：

- 每个路径前加 `@`。
- 使用换行连接。
- 末尾没有额外换行。
- 空组不复制，显示 warning。
- 成功通知包含组名和路径数量。
- 不检查文件是否真实存在，因此缺失文件同样会出现在列表中。

## 8. Copy as Markdown

入口：

- 工具栏 Copy as Markdown：导出当前激活组。
- 右键 `.aicode.json` → AICode → Copy as Markdown。
- 配置文件右键动作复用了 IDE 的 Copy 快捷键。

### 8.1 精确输出格式

````markdown
> Project: project-name  
> File Count: 2  

---

## 📄 src/App.java

```java
class App {
}
```

---

## 📄 missing.txt

⚠️ **Missing File**

---
````

生成规则：

- 不包含顶级 `# AICode Context Export` 标题。
- 第一行是项目名。
- 第二行是配置路径数量，包括缺失文件。
- 每个文件以 `## 📄 相对路径` 开始。
- 文件内容按 UTF-8 解码。
- 内容末尾没有换行时自动补一个。
- 每个文件块后添加 `---`。
- 缺失文件输出 `⚠️ **Missing File**`。
- 读取失败时，在代码围栏内部输出错误信息。
- 不转义路径中的 Markdown 字符。
- 不处理文件内容自身包含三反引号的情况。
- 不限制总大小，全部构建到内存后一次写入系统剪贴板。
- 空组不复制，并显示警告通知。
- 成功通知显示路径数组数量，不是实际成功读取数量。

### 8.2 Markdown 语言映射

| 扩展名 | Markdown language |
|---|---|
| java | java |
| kt, kts | kotlin |
| xml | xml |
| json | json |
| yml, yaml | yaml |
| properties | properties |
| gradle | gradle |
| js | javascript |
| ts | typescript |
| py | python |
| go | go |
| rs | rust |
| c | c |
| cpp | cpp |
| h | c |
| hpp | cpp |
| cs | csharp |
| php | php |
| rb | ruby |
| swift | swift |
| scala | scala |
| groovy | groovy |
| sh | bash |
| sql | sql |
| html | html |
| css | css |
| scss | scss |
| md | markdown |
| txt | text |
| 其他/无扩展名 | 空字符串 |

扩展名匹配不区分大小写。

## 9. 编辑器横幅

工具栏眼睛按钮控制横幅：

- 默认值实际是 `false`，即启动后默认关闭。
- 状态仅保存在内存中。
- 重启 IDE 或重新打开项目后恢复关闭。
- 关闭状态使用闭眼图标，按钮文字为 `Show Editor Banner`。
- 开启状态使用睁眼图标，按钮文字为 `Hide Editor Banner`。

开启后，当编辑器打开的文件属于当前激活组时，在编辑器顶部显示：

```text
This file is in the active AICode context.
[Remove from Context]
```

规则：

- 不对目录显示。
- 不对 `.aicode.json` 显示。
- 仅针对当前激活组。
- 点击 Remove 后立即从当前组移除。
- 切换分组、修改配置或开关横幅时刷新全部编辑器通知。

VS Code 没有完全等价的 Editor Notification Panel。可使用状态栏、第一行上方 decoration 或打开文件时的非模态信息提示近似实现。

## 10. 图标

- `.aicode.json` 的文件图标被覆盖为插件 Logo。
- 普通已加入上下文的文件不会覆盖 Project View 图标。
- 插件和工具窗口使用 `aicode.svg`。
- 目录部分选中实际使用文本 `(*)`；`dot_green.svg` 当前没有被 UI 使用。
- 横幅开启使用 `eye_open.svg`。
- 横幅关闭使用 `eye_close.svg`。

## 11. 文件系统自动同步

插件监听整个 IDEA VFS 的事件。

### 11.1 创建文件/目录

- 不自动加入上下文。
- 清缓存并刷新工具树，用于重新计算 `(*)`。

### 11.2 删除文件

- 如果删除的相对路径恰好属于当前组，自动移除。
- 否则只刷新 UI。

### 11.3 移动文件

- 计算旧路径和新路径。
- 当前组中恰好存在旧路径时，将该项替换为新路径。
- 保持数组中的原位置。

### 11.4 重命名文件

- 当前组中恰好存在旧路径时，替换为新路径。
- 保持数组位置。

### 11.5 修改配置

修改 `.aicode.json` 时：

- 清除缓存。
- 刷新文件树。
- 刷新编辑器横幅。
- 刷新 Project View。

事件目标 basename 命中固定忽略名单时，整个事件跳过。

VS Code 映射：

```ts
workspace.createFileSystemWatcher('**/*');
workspace.onDidRenameFiles(...);
workspace.onDidDeleteFiles(...);

new vscode.RelativePattern(workspaceRoot, '.aicode.json');
```

## 12. 内部刷新机制

任何配置保存或显式通知都会：

1. 清空当前路径缓存。
2. 向内部消息总线发布 `onContextChanged`。
3. 重建 AICode Tree。
4. 刷新全部编辑器横幅。
5. 异步刷新 Project View。

当前路径缓存为 `Set<String>`，用于菜单和横幅高频判断。

VS Code 可使用集中式服务：

```ts
class AICodeService {
  private config?: AICodeConfig;
  private activePathSet?: Set<string>;
  private readonly changeEmitter = new vscode.EventEmitter<void>();

  readonly onDidChange = this.changeEmitter.event;
}
```

TreeDataProvider、命令可见性和编辑器状态订阅同一个事件源。

## 13. IDEA 注册入口与 VS Code API 对照

| IDEA 功能 | VS Code 对应 API |
|---|---|
| Project Service | activation-scoped service/singleton |
| Tool Window | `viewsContainers` + `views` + `TreeDataProvider` |
| Project View context action | `menus.view/item/context` |
| Editor tab popup | `menus.editor/title/context` |
| Action shortcut | `contributes.keybindings` |
| Action update visibility | `when` clause + `commands.executeCommand('setContext', ...)` |
| Balloon Notification | `showInformationMessage/showWarningMessage/showErrorMessage` |
| FileEditorManager.openFile | `workspace.openTextDocument` + `showTextDocument` |
| ClipboardService | `env.clipboard.writeText` |
| VFS listener | `FileSystemWatcher` + workspace file events |
| Editor Notification Panel | status bar/decoration/message approximation |
| IconProvider | 文件图标主题关联或 TreeItem icon |
| MessageBus Topic | `EventEmitter<void>` |
| WriteCommandAction | `WorkspaceEdit` 或 `workspace.fs.writeFile` |

## 14. 复刻时必须注意的真实边界和源码缺陷

如果目标是“用户可感知功能完全一致”，建议保留结果语义，但不要照搬以下缺陷：

- README 所称多模块名称展示并不存在。当前树只展示路径目录，没有 `[module-name] FileName.java` 格式。
- 目录删除不会清理全部后代路径。文件事件处理只匹配被删除目录本身的相对路径，不按前缀移除其子文件。
- 目录移动或重命名不会更新后代路径，只尝试替换目录本身的精确路径。
- 文件删除、移动、重命名只维护当前激活组，其他非激活组里的路径不会更新。
- 路径根判断是字符串 `startsWith`，例如根目录 `/work/foo` 可能错误匹配 `/work/foobar`。
- 多项目归属选择第一个字符串前缀匹配的打开项目。
- 路径缓存会丢失顺序。首次读取返回 JSON 数组顺序，缓存命中后由 `HashSet` 转回列表，后续导出或添加的顺序可能不稳定。
- 批量添加内部去重不完整。`existingSet` 在遍历过程中没有加入新发现路径；当多选目录互相包含时，可能在同一次操作中添加重复路径。
- 配置写入异常全部静默吞掉。
- 非法 JSON 不通知用户。
- 代码注释说横幅“重启后默认开启”，实际初始化为关闭。
- README 宣称操作支持 Undo/Redo，但 VS Code 使用直接文件写入时不会自然进入编辑器 Undo 栈。
- Copy as Markdown 动作复用普通 Copy 快捷键；VS Code 中不建议全局覆盖 `Ctrl/Cmd+C`。
- 每次树刷新都会递归扫描磁盘，每个目录节点还可能重复扫描后代，大项目中可能出现明显性能问题。

建议 VS Code 版本采用“结果兼容模式”：修复内部缺陷，但保持 UI、配置格式、命令语义和导出结果一致，而不是进行 bug-for-bug 复刻。

## 15. 主要源码索引

- 插件入口和扩展点：`src/main/resources/META-INF/plugin.xml`
- 数据模型：`src/main/java/com/aicode/model/AICodeConfig.java`
- 配置与文件服务：`src/main/java/com/aicode/service/AICodeFileService.java`
- 添加命令：`src/main/java/com/aicode/action/AddToAICodeAction.java`
- 移除命令：`src/main/java/com/aicode/action/RemoveFromAICodeAction.java`
- Markdown 命令：`src/main/java/com/aicode/action/CopyMarkdownAction.java`
- 工具窗口：`src/main/java/com/aicode/ui/AICodePanel.java`
- 文件事件监听：`src/main/java/com/aicode/listener/AICodeFileListener.java`
- 编辑器横幅：`src/main/java/com/aicode/provider/AICodeEditorNotificationProvider.java`
- 配置文件图标：`src/main/java/com/aicode/provider/AICodeIconProvider.java`
- Markdown 生成器：`src/main/java/com/aicode/util/MarkdownBuilder.java`
- 语言映射：`src/main/java/com/aicode/util/CodeLanguageResolver.java`
- 忽略名单：`src/main/java/com/aicode/settings/AICodeIgnoreSettings.java`
