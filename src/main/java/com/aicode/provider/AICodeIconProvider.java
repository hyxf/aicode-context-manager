package com.aicode.provider;

import com.aicode.icons.AICodeIcons;
import com.aicode.service.AICodeFileService;
import com.intellij.ide.IconProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LayeredIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AICodeIconProvider extends IconProvider {

    @Override
    public @Nullable Icon getIcon(@NotNull PsiElement element, int flags) {
        if (!(element instanceof PsiFile)) return null;

        PsiFile psiFile = (PsiFile) element;
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile == null) return null;

        // .aicode.json 保持原有逻辑
        if (".aicode.json".equals(virtualFile.getName())) {
            return AICodeIcons.LOGO;
        }

        // 直接从 PsiElement 获取 Project，避免 guessProjectForFile 的性能开销
        Project project = psiFile.getProject();
        if (project.isDisposed()) return null;

        AICodeFileService service = AICodeFileService.getInstance(project);
        if (!service.containsFile(virtualFile)) return null;

        // 直接取 FileType 图标，避免调用 psiFile.getIcon() 造成无限递归
        Icon originalIcon = virtualFile.getFileType().getIcon();
        if (originalIcon == null) return null;

        // 叠加绿色圆点到左上角
        LayeredIcon layeredIcon = new LayeredIcon(2);
        layeredIcon.setIcon(originalIcon, 0);
        layeredIcon.setIcon(AICodeIcons.DOT_GREEN, 1, 0, 0);
        return layeredIcon;
    }
}