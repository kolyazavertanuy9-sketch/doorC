package dev.wvdoor.launcher;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FirstRunSetup {
    private FirstRunSetup() {}

    public static boolean show(Component parent, LauncherSettings settings) {
        JTextField directory = new JTextField(settings.gameDirectory().toString(), 32);
        JButton browse = new JButton("Browse...");
        browse.addActionListener(event -> chooseDirectory(parent, directory));

        JPanel pathRow = new JPanel(new BorderLayout(8, 0));
        pathRow.add(directory, BorderLayout.CENTER);
        pathRow.add(browse, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        JLabel heading = new JLabel("<html><b>Choose where DoorC stores Minecraft files</b><br>"
                + "This folder contains separate game versions, mods, libraries and caches.</html>");
        content.add(heading, BorderLayout.NORTH);
        content.add(pathRow, BorderLayout.CENTER);
        JLabel note = new JLabel("<html>The launcher itself is installed separately. Its location is chosen "
                + "in the Windows installer.</html>");
        content.add(note, BorderLayout.SOUTH);
        content.setPreferredSize(new Dimension(560, 130));

        while (true) {
            int choice = JOptionPane.showConfirmDialog(parent, content, "DoorC Launcher setup",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) return false;
            Path selected;
            try {
                selected = Path.of(directory.getText().trim()).toAbsolutePath().normalize();
                if (LauncherSettings.gameDataIsInsideApplication(selected)) {
                    throw new IllegalArgumentException(
                            "Choose a folder outside the launcher installation folder.");
                }
                Files.createDirectories(selected);
                Path probe = Files.createTempFile(selected, ".doorc-write-check-", ".tmp");
                Files.delete(probe);
                settings.setGameDirectory(selected);
                return true;
            } catch (IOException | RuntimeException exception) {
                JOptionPane.showMessageDialog(parent,
                        "Could not use that folder: " + exception.getMessage(),
                        "Folder unavailable", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public static void chooseDirectory(Component parent, JTextField target) {
        JFileChooser chooser = new JFileChooser(target.getText());
        chooser.setDialogTitle("Choose Minecraft data folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
            target.setText(chooser.getSelectedFile().toPath().toAbsolutePath().normalize().toString());
        }
    }
}
