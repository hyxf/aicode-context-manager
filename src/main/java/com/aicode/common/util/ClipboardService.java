package com.aicode.common.util;

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;

/**
 * Utility for clipboard operations
 */
public class ClipboardService {

    /**
     * Copy text to system clipboard
     */
    public static void copyToClipboard(@NotNull String text) {
        StringSelection selection = new StringSelection(text);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(selection, selection);
    }
}
