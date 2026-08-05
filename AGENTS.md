# Repository Guidelines

## 项目定位与技术栈

本仓库是 AICode Context Manager IntelliJ IDEA 插件，用于维护项目根目录下的 `.aicode.json`、按上下文组管理文件，并导出 Markdown。项目使用 Java 17、Gradle Kotlin DSL、IntelliJ Platform Gradle Plugin 1.17.2，开发基线为 IntelliJ IDEA Community 2023.2（build 232），声明兼容至 `300.*`。

## 目录与模块职责

- `src/main/java/com/aicode/action/`：Project View 和编辑器菜单动作；只负责参数校验与流程编排。
- `service/AICodeFileService.java`：配置读写、分组操作、路径缓存与变更通知的唯一入口。
- `model/AICodeConfig.java`：`.aicode.json` 的数据模型；修改字段时必须考虑旧版数组格式迁移。
- `listener/`：监听 VFS 创建、删除、移动、重命名和内容变化。
- `provider/`：文件图标及编辑器顶部通知。
- `ui/`：Tool Window、分组选择和文件树交互。
- `util/`：Markdown 构建、语言识别与剪贴板访问；优先保持无 UI 依赖，便于单元测试。
- `src/main/resources/META-INF/plugin.xml`：服务、扩展点、监听器、动作和快捷键注册。
- `src/main/resources/icons/`：插件 SVG 资源。

`update_aicode_plugin.py` 是一次性维护脚本，不属于插件运行链路；修改前先确认它仍有用途，不要把它当作构建或发布入口。

## 核心架构与变更原则

典型数据流为：IDE Action/UI → `AICodeFileService` → `.aicode.json` → `AICODE_TOPIC` → Tool Window/Editor Notification 刷新。新增功能应复用该链路，避免在 Action 或 Panel 中直接读写 JSON。

- 所有 VFS 写入必须通过 IntelliJ Write Action/`WriteCommandAction`，并尽量保留 Undo/Redo 语义。
- Swing 组件只能在 EDT 更新；耗时文件遍历或内容拼装不得阻塞 EDT。
- 路径统一保存为相对项目根目录的 `/` 分隔形式，禁止写入机器相关的绝对路径。
- 配置写入后必须使缓存失效并发布变更通知；同时检查多分组和活动分组是否仍一致。
- 新增 Action、Service、Provider、Listener 或 Tool Window 时，同步更新 `plugin.xml`。
- 不要静默吞掉新异常。向用户展示可操作的错误信息，并在适合的位置记录日志；不得记录文件正文、密钥或剪贴板内容。

## 构建与本地开发

统一使用仓库内 Gradle Wrapper：

```bash
./gradlew clean build       # 编译、检查、测试并打包
./gradlew runIde            # 启动安装了当前插件的沙箱 IDEA
./gradlew test              # 运行全部自动化测试
./gradlew verifyPlugin      # 检查 IntelliJ API/二进制兼容性
./gradlew buildPlugin       # 输出 build/distributions/*.zip
```

日常调试优先使用 `runIde`，覆盖添加/移除文件、分组切换、Markdown 导出，以及文件重命名、移动、删除后的同步。构建依赖下载失败时先检查 JDK 17、代理与 Gradle 缓存，不要提交本机环境配置。

## 编码规范

Java 使用 4 空格缩进；类名使用 PascalCase，方法和变量使用 camelCase，常量使用 UPPER_SNAKE_CASE。包名保持在 `com.aicode` 下，并按职责归档。遵循 IntelliJ SDK 的空值注解约定，公开边界使用 `@NotNull`/`@Nullable`。Action 应实现 `DumbAware`（确实可在索引期间运行时）并声明合适的 `ActionUpdateThread`。保持 import 显式、方法短小、注释解释设计原因而非复述代码。

仓库尚未配置 Spotless、Checkstyle 或其他自动格式化工具；提交前使用 IntelliJ 的 Reformat Code 和 Optimize Imports，并避免夹带无关格式化。

## 测试要求

当前仓库没有 `src/test` 和覆盖率门槛。新增可测试逻辑时，在 `src/test/java/com/aicode/` 下建立与生产代码一致的包结构，测试类命名为 `*Test`，测试方法描述行为与结果。纯逻辑优先覆盖 `AICodeConfig`、`MarkdownBuilder` 和 `CodeLanguageResolver`；涉及 Project、VirtualFile、Action 或 Tool Window 的行为应使用 IntelliJ Platform test fixture，而不是模拟 SDK 内部实现。

每次功能变更至少验证：正常路径、空分组、重复文件、缺失文件、旧配置迁移、非项目文件、二进制/忽略文件，以及 VFS 重命名或移动。若引入测试框架，显式添加 `testImplementation` 依赖并在 PR 中说明。

## 提交与 Pull Request

历史提交以简短主题为主，并混用 `feat:`、`docs:`、`style:`、`modify:`。新提交统一建议使用 `<type>: <动词开头的说明>`，常用类型为 `feat`、`fix`、`refactor`、`test`、`docs`、`build`，例如：`fix: 同步目录移动后的上下文路径`。一个提交只处理一个逻辑变更。

PR 必须包含变更目的、关键实现、影响范围和实际执行的验证命令；关联 Issue（如有）。涉及 Tool Window、菜单、图标或通知时附截图/录屏；涉及 `.aicode.json` 时给出前后示例并说明兼容性；涉及支持版本、依赖、快捷键或扩展点时明确标注。合并前确保 `./gradlew build` 通过，且不提交 `.idea/`、`build/`、沙箱数据、证书或发布令牌。

## 发版规范

用户提出“发版”时，默认含义是：检查并提交当前工作区的未提交改动，将提交推送到远端当前分支，再基于最新版本创建并推送新的 Git 标签。除非用户明确要求，否则不要把“发版”扩展为上传 JetBrains Marketplace、创建 GitHub Release 或修改其他发布渠道。

发版属于会修改 Git 历史和远端状态的操作。执行前必须先检查工作区、当前分支、远端跟踪分支和现有标签，向用户汇报将要提交的文件、建议的提交信息以及建议的新版本，并征询用户确认。不得擅自提交来源不明的改动，也不得在用户未确认版本级别时自行选择版本。

版本号和 Git 标签遵循语义化版本，标签格式为 `vX.Y.Z`。根据现有最新标签计算候选版本，并询问用户选择以下一种发版类型：

- `PATCH`：向后兼容的问题修复，例如 `1.5.3` → `1.5.4`。
- `MINOR`：向后兼容的新功能，例如 `1.5.3` → `1.6.0`。
- `MAJOR`：配置格式、交互或 API 存在不兼容变化，例如 `1.5.3` → `2.0.0`。

如果用户已经明确给出完整版本号，则使用该版本，但仍需检查标签是否已存在。若没有任何历史标签，应向用户确认初始版本，不得自行假设为 `v1.0.0`。如果当前改动的性质与用户选择的版本级别明显不一致，应说明原因并再次确认，不要静默更改选择。

### 标准发版流程

1. 执行只读检查：`git status --short`、`git diff --stat`、`git diff`、`git branch --show-current`、`git remote -v`、`git tag --sort=-version:refname`。必要时执行 `git fetch --tags` 获取远端最新标签。
2. 判断所有未提交改动是否属于本次发版。发现无关改动、敏感文件、构建产物或无法确认来源的文件时，暂停并请用户决定；不要擅自丢弃、暂存或提交。
3. 根据实际 diff 拟定符合本仓库规范的提交信息，例如 `fix: 修复文件移动后的上下文路径`。在执行提交前，向用户明确展示：

   - 本次准备提交的文件和变更摘要；
   - 完整提交信息；
   - 当前最新标签；
   - `MAJOR`、`MINOR`、`PATCH` 各自对应的新标签，并请用户选择，推荐项必须说明依据。

4. 获得用户确认后，运行与改动风险相匹配的测试。正式发版至少执行：

   ```bash
   ./gradlew clean build
   ```

   修改 `sinceBuild`/`untilBuild` 时额外执行 `./gradlew verifyPlugin`，并记录最低支持版本和目标高版本 IDEA 的冒烟测试结果。测试失败时停止发版，不得提交、推送或打标签。
5. 只暂存本次已确认的文件，提交后核对提交内容和提交信息：

   ```bash
   git add <confirmed-files>
   git commit -m "<type>: <动词开头的说明>"
   git show --stat --oneline HEAD
   ```

6. 获取当前分支名称，并将该提交明确推送到远端同名分支：

   ```bash
   git branch --show-current
   git push origin <current-branch>
   ```

   例如当前分支为 `main`，则执行 `git push origin main`。必须核对推送目标确实是当前分支；若当前分支没有 upstream、远端包含新提交或推送被拒绝，应停止并向用户说明，不得强制推送，也不得擅自 rebase、merge 或改推其他分支。
7. 确认当前分支已经成功推送到远端后，在刚推送的提交上创建附注标签，再将该标签推送到同一远端：

   ```bash
   git tag -a vX.Y.Z -m "vX.Y.Z"
   git push origin vX.Y.Z
   ```

8. 最后核验远端分支和标签都指向本次提交，并向用户报告提交哈希、提交信息、分支、标签、测试结果和推送结果。

### 失败处理与安全约束

- 提交失败：保留工作区状态，说明错误原因；不得绕过 Git hooks，除非用户明确授权。
- 分支推送失败：不要创建标签，先解决远端分歧或权限问题。
- 标签创建或推送失败：不要移动、覆盖或复用已存在标签；检查冲突后请用户决定新的版本号。
- 分支已经推送但标签失败：明确告知用户当前处于“代码已发布、标签未发布”的中间状态，并在问题解决后只补做标签步骤。
- 禁止使用 `git push --force`、移动既有标签或删除远端标签来完成常规发版。
- 插件签名及 Marketplace 发布使用 `CERTIFICATE_CHAIN`、`PRIVATE_KEY`、`PRIVATE_KEY_PASSWORD`、`PUBLISH_TOKEN`；只有用户明确要求上传 Marketplace 时才执行，并且必须另行确认发布版本与 channel。凭据只能通过本地环境或 CI Secret 注入，禁止写入代码、`gradle.properties`、命令行参数、日志和 PR。
