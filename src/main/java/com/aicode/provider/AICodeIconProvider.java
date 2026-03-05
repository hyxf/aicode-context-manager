package com.aicode.provider;

import com.aicode.icons.AICodeIcons;
import com.intellij.ide.IconProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AICodeIconProvider extends IconProvider {
    
    @Override
    public @Nullable Icon getIcon(@NotNull PsiElement element, int flags) {
        if (!(element instanceof PsiFile psiFile)) return null;

        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile == null) return null;

        // .aicode.json 使用 LOGO 图标
        if (".aicode.json".equals(virtualFile.getName())) {
            return AICodeIcons.LOGO;
        }
        return null;
    }
}