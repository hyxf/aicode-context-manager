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

## 安全与发布配置

插件签名和发布分别读取 `CERTIFICATE_CHAIN`、`PRIVATE_KEY`、`PRIVATE_KEY_PASSWORD`、`PUBLISH_TOKEN`。这些值只能通过本地环境或 CI Secret 注入，禁止写入代码、Gradle 属性、日志和 PR。修改 `sinceBuild`/`untilBuild` 后必须运行 `verifyPlugin`，并在至少一个最低支持版本和一个目标高版本 IDE 中完成冒烟测试。
