# AICode Context Manager

![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)
![IntelliJ Platform](https://img.shields.io/badge/platform-IntelliJ-orange.svg)

A powerful IntelliJ IDEA plugin for managing AI code context files with one-click Markdown export.

## 🎯 Overview

AICode Context Manager helps you maintain a curated collection of code files to provide as context to AI assistants. It manages a `.aicode.json` file in your project root and offers seamless integration with IntelliJ IDEA's interface.

## ✨ Features

### 📁 File Management
- **Add to Context**: Right-click any file in Project View → AICode → Add to AICode
- **Remove from Context**: Right-click managed files → AICode → Remove from AICode
- **Smart Menu**: Only shows relevant actions (Add vs Remove) based on file status
- **Undo Support**: All operations support IntelliJ's undo/redo

### 🖼️ Visual Tool Window
- **AICode Context** tool window displays all managed files
- Shows module name for multi-module projects: `[module-name] FileName.java`
- Click to open files
- Right-click to remove files
- Toolbar button to open `.aicode.json` directly
- Auto-refreshes when files change

### 🔄 Auto-Sync
- **File Deletion**: Automatically removes deleted files from context
- **File Rename**: Updates paths when files are renamed
- **File Move**: Updates paths when files are moved to different directories
- All changes sync immediately to `.aicode.json`

### 📋 Markdown Export
- **One-Click Export**: Right-click `.aicode.json` → AICode → Copy as Markdown
- Generates complete Markdown document with all file contents
- Copies directly to system clipboard
- Supports all major file types with proper syntax highlighting
- Shows notification with file count

## 🚀 Getting Started

### Installation

1. Download the plugin from JetBrains Marketplace (or build from source)
2. Install via `Settings → Plugins → Install Plugin from Disk`
3. Restart IntelliJ IDEA

### Quick Start

1. **Add files to context**:
   - Right-click any code file in Project View
   - Select `AICode → Add to AICode`

2. **View your context**:
   - Open the `AICode Context` tool window (right sidebar)
   - See all managed files with their module names

3. **Export as Markdown**:
   - Right-click `.aicode.json` in Project View
   - Select `AICode → Copy as Markdown`
   - Paste into your AI assistant

## 📄 `.aicode.json` Format

The plugin manages a simple JSON array in your project root:

```json
[
  "finance-module-settle-api/src/main/java/com/tongtong/settle/api/SettleBillRuleApi.java",
  "finance-module-settle-api/src/main/java/com/tongtong/settle/model/request/SettleBillRuleV2Request.java"
]
```

**Rules:**
- File paths are relative to project root
- Plain JSON string array (no objects, no comments)
- Auto-created with `[]` if missing
- Managed automatically by the plugin

## 📋 Markdown Export Format

```markdown
# AICode Context Export

> Generated from .aicode.json  
> Project: MyProject  
> File Count: 2  

---

## 📄 src/main/java/Example.java

```java
public class Example {
    // Full file contents
}
```

---

## 📄 src/main/kotlin/Demo.kt

```kotlin
class Demo {
    // Full file contents
}
```

---
```

## 🛠️ Supported File Types

The plugin recognizes these file extensions for syntax highlighting:

| Extension | Language |
|-----------|----------|
| `.java` | Java |
| `.kt`, `.kts` | Kotlin |
| `.xml` | XML |
| `.json` | JSON |
| `.yml`, `.yaml` | YAML |
| `.gradle` | Gradle |
| `.js` | JavaScript |
| `.ts` | TypeScript |
| `.py` | Python |
| `.go` | Go |
| `.rs` | Rust |
| `.c`, `.h` | C |
| `.cpp`, `.hpp` | C++ |
| `.cs` | C# |
| `.rb` | Ruby |
| `.swift` | Swift |
| `.scala` | Scala |
| And many more... |

## 🏗️ Architecture

```
AICodeFileService         → JSON file read/write operations
AddToAICodeAction         → Add file to context
RemoveFromAICodeAction    → Remove file from context
AICodeToolWindowFactory   → Creates the tool window
AICodePanel               → Tool window UI
AICodeFileListener        → File system event monitoring
CopyMarkdownAction        → Export to Markdown
MarkdownBuilder           → Markdown generation
CodeLanguageResolver      → File extension → language mapping
ClipboardService          → Clipboard operations
```

## 🔧 Building from Source

### Prerequisites
- JDK 17+
- Gradle 8.0+

### Build Steps

```bash
# Clone the repository
git clone https://github.com/yourusername/aicode-context-manager.git
cd aicode-context-manager

# First time setup - Generate Gradle Wrapper (if needed)
# If you don't have gradle-wrapper.jar, run:
gradle wrapper --gradle-version 8.5
# Or just open the project in IntelliJ IDEA and it will auto-download

# Build the plugin
./gradlew buildPlugin

# Run in IntelliJ sandbox for testing
./gradlew runIde

# The plugin ZIP will be in build/distributions/
```

**Note**: If `gradle-wrapper.jar` is missing, IntelliJ IDEA will automatically download it when you open the project, or you can generate it with `gradle wrapper`.

## 🎨 Multi-Module Projects

For projects with multiple modules, the tool window displays module names to avoid confusion:

```
[api-module] UserService.java
[core-module] UserService.java
[web-module] UserController.java
```

All paths in `.aicode.json` remain relative to the project root.

## ⚠️ Error Handling

| Situation | Behavior |
|-----------|----------|
| File doesn't exist | Shows "⚠️ Missing File" in Markdown |
| JSON parse error | Notification with error message |
| Empty file | Still exports (empty code block) |
| Large file | No truncation (exports full content) |

## 🤝 Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## 📝 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🐛 Issues & Support

- Report bugs via GitHub Issues
- For feature requests, use the discussion board
- Check existing issues before creating new ones

## 🎯 Use Cases

- **AI Pair Programming**: Quickly share context with ChatGPT, Claude, or GitHub Copilot
- **Code Reviews**: Export specific files for detailed review
- **Documentation**: Generate code examples for documentation
- **Knowledge Sharing**: Share code snippets with team members
- **Training**: Create training materials with real code examples

## 🔮 Roadmap

- [ ] Support for custom Markdown templates
- [ ] Export to different formats (HTML, PDF)
- [ ] Context size calculator
- [ ] Smart context suggestions based on file relationships
- [ ] Integration with AI assistant APIs

## 👏 Acknowledgments

Built with ❤️ using IntelliJ Platform SDK

---

**Made for developers who love AI-assisted coding** 🚀
