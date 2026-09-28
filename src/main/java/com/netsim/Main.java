package com.netsim;

import com.formdev.flatlaf.FlatDarkLaf;
import com.netsim.ui.MainWindow;
import javax.swing.SwingUtilities;

public class Main {

    public static void main(String[] args) {
        FlatDarkLaf.setup(); // modern dark theme
        SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
    }
}