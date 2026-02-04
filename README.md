# AICode Context Manager

![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)
![IntelliJ Platform](https://img.shields.io/badge/platform-IntelliJ-orange.svg)

一款强大的 IntelliJ IDEA 插件，用于管理 AI 代码上下文文件，并支持一键导出为 Markdown。

---

## 🎯 插件简介

AICode Context Manager 帮助你维护一组可供 AI 助手使用的代码文件集合。
它会在项目根目录管理一个 `.aicode.json` 文件，并与 IntelliJ IDEA 界面深度集成，使用顺畅自然。

---

## ✨ 功能特性

### 📁 文件管理

* **添加到上下文**：在 Project 视图中右键任意文件 → AICode → Add to AICode
* **从上下文移除**：右键已管理文件 → AICode → Remove from AICode
* **智能菜单**：根据文件当前状态自动显示 Add 或 Remove
* **支持撤销**：所有操作均支持 IntelliJ 的 Undo / Redo

---

### 🖼️ 可视化工具窗口

* 提供 **AICode Context** 工具窗口，展示所有已管理文件
* 多模块项目中显示模块名：`[module-name] FileName.java`
* 点击即可打开文件
* 右键可移除文件
* 工具栏按钮可直接打开 `.aicode.json`
* 文件变化时自动刷新

---

### 🔄 自动同步

* **文件删除**：文件被删除时自动从上下文移除
* **文件重命名**：路径自动更新
* **文件移动**：移动目录后路径自动更新
* 所有变更都会立即同步到 `.aicode.json`

---

### 📋 Markdown 导出

* **一键导出**：右键 `.aicode.json` → AICode → Copy as Markdown
* 自动生成包含所有文件内容的完整 Markdown 文档
* 直接复制到系统剪贴板
* 支持主流文件类型并带语法高亮
* 导出完成后会提示包含的文件数量

---

## 🚀 使用方式

### 🧠 在 IntelliJ IDEA 中安装插件仓库

1. 打开 **IDEA**
2. 进入 **Settings**（或 Preferences）
3. 选择 **Plugins**
4. 右上角点击 **⚙️（齿轮图标）**
5. 选择 **Manage Plugin Repositories**
6. 点击 **+**
7. 填入地址：

```
https://hyxf.github.io/aicode-context-manager/updatePlugins.xml
```

8. 确认保存
9. 搜索插件名称或在 **Updates** 中检查更新

---

**为热爱 AI 辅助编程的开发者打造** 🚀
