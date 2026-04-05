package com.docgalaxy;

import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.SwingUtilities;

public class App {
    public static void main(String[] args) {
        FlatDarkLaf.setup();
        SwingUtilities.invokeLater(() -> {
            // TODO: create and show MainFrame
            System.out.println("DocGalaxy starting...");
        });
    }
}
