package com.aicode.provider;

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
