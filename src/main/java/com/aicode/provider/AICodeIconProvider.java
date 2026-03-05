package com.aicode.provider;

import com.aicode.icons.AICodeIcons;
import com.aicode.service.AICodeFileService;
import com.intellij.ide.IconProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.PsiIconUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AICodeIconProvider extends IconProvider {

    private static final ThreadLocal<Boolean> COMPUTING = ThreadLocal.withInitial(() -> false);

    @Override
    public @Nullable Icon getIcon(@NotNull PsiElement element, int flags) {
        if (!(element instanceof PsiFile psiFile)) return null;

        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile == null) return null;

        // .aicode.json 使用 LOGO 图标
        if (".aicode.json".equals(virtualFile.getName())) {
            return AICodeIcons.LOGO;
        }

        // 防止递归
        if (COMPUTING.get()) return null;

        Project project = psiFile.getProject();
        if (project.isDisposed()) return null;

        AICodeFileService service = AICodeFileService.getInstance(project);
        // if (!service.isBannerEnabled()) return null;
        if (!service.containsFile(virtualFile)) return null;

        try {
            COMPUTING.set(true);

            // FileType 图标作为兜底
            Icon baseIcon = virtualFile.getFileType().getIcon();
            if (baseIcon == null) return null;

            // 获取其他 Provider 的真实图标（Java/Kotlin 等插件图标）
            Icon providersIcon = PsiIconUtil.getProvidersIcon(psiFile, flags);
            Icon originalIcon = (providersIcon != null) ? providersIcon : baseIcon;

            // 叠加绿色圆点到左上角
            LayeredIcon layeredIcon = new LayeredIcon(2);
            layeredIcon.setIcon(originalIcon, 0);
            layeredIcon.setIcon(AICodeIcons.DOT_GREEN, 1, 0, 0);
            return layeredIcon;

        } finally {
            COMPUTING.set(false);
        }
    }
}