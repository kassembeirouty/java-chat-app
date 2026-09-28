package com.chatapp;

import com.chatapp.ui.LauncherWindow;
import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.SwingUtilities;

public class Main {

    public static void main(String[] args) {
        FlatDarkLaf.setup(); // modern dark theme
        SwingUtilities.invokeLater(() -> new LauncherWindow().setVisible(true));
    }
}