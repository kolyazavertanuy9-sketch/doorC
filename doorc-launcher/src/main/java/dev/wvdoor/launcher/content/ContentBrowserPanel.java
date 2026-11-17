package dev.wvdoor.launcher.content;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import javax.swing.JOptionPane;

public final class ContentBrowserPanel extends JPanel {
    private static final Color BG = new Color(11, 13, 23);
    private static final Color CARD = new Color(24, 29, 45);
    private static final Color FIELD = new Color(34, 41, 61);
    private static final Color FG = new Color(242, 244, 250);
    private static final Color MUTED = new Color(155, 165, 187);
    private static final Color PURPLE = new Color(154, 91, 231);
    private static final Font BODY = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font BOLD = new Font("Segoe UI", Font.BOLD, 13);

    private final Supplier<Path> gameDirectory;
    private final ContentCatalog catalog = new ContentCatalog();
    private final ContentInstaller installer = new ContentInstaller();
    private final JComboBox<ContentPlatform> platform = new JComboBox<>(ContentPlatform.values());
    private final JComboBox<ContentType> type = new JComboBox<>(ContentType.values());
    private final JComboBox<MinecraftProfile> profile = new JComboBox<>();
    private final JTextField search = new JTextField();
    private final DefaultListModel<ContentProject> results = new DefaultListModel<>();
    private final JList<ContentProject> resultList = new JList<>(results);
    private final JTextArea description = new JTextArea();
    private final JLabel status = new JLabel("Select a Minecraft profile and search for content.");
    private final JButton searchButton = new JButton("Search");
    private final JButton moreButton = new JButton("Load more");
    private final JButton installButton = new JButton("Install");
    private final JButton openProjectButton = new JButton("Project page");
    private final JProgressBar progress = new JProgressBar();
    private int nextOffset;
    private long totalHits;
    private boolean busy;
    private boolean scanningProfiles;

    public ContentBrowserPanel(Supplier<Path> gameDirectory) {
        this.gameDirectory = gameDirectory;
        setOpaque(false);
        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(8, 28, 20, 24));
        add(buildHeader(), BorderLayout.NORTH);
        add(buildBrowser(), BorderLayout.CENTER);
        platform.addActionListener(event -> updateProviderStatus());
        type.addActionListener(event -> updateProviderStatus());
        search.addActionListener(event -> search(false));
        searchButton.addActionListener(event -> search(false));
        moreButton.addActionListener(event -> search(true));
        resultList.addListSelectionListener(event -> updateSelectedProject());
        installButton.addActionListener(event -> installSelected());
        openProjectButton.addActionListener(event -> openProjectPage());
        updateSelectedProject();
        refreshTargets();
    }

    public void refreshTargets() {
        if (busy || scanningProfiles) return;
        scanningProfiles = true;
        MinecraftProfile selected = (MinecraftProfile) profile.getSelectedItem();
        profile.setEnabled(false);
        searchButton.setEnabled(false);
        moreButton.setEnabled(false);
        installButton.setEnabled(false);
        status.setText("Finding installed Minecraft profiles...");
        new SwingWorker<List<MinecraftProfile>, Void>() {
            @Override
            protected List<MinecraftProfile> doInBackground() throws Exception {
                return InstalledProfileScanner.scan(gameDirectory.get());
            }

            @Override
            protected void done() {
                try {
                    List<MinecraftProfile> discovered = get();
                    profile.setModel(new DefaultComboBoxModel<>(discovered.toArray(MinecraftProfile[]::new)));
                    if (selected != null) {
                        for (int i = 0; i < discovered.size(); i++) {
                            MinecraftProfile candidate = discovered.get(i);
                            if (candidate.directory().equals(selected.directory())
                                    && candidate.gameVersion().equals(selected.gameVersion())
                                    && candidate.loader().equals(selected.loader())) {
                                profile.setSelectedIndex(i);
                                break;
                            }
                        }
                    }
                    profile.setEnabled(!discovered.isEmpty());
                    searchButton.setEnabled(!discovered.isEmpty());
                    installButton.setEnabled(false);
                    if (discovered.isEmpty()) {
                        status.setText("Install a Minecraft profile first; content is installed per profile.");
                    } else {
                        status.setText("Choose content compatible with the selected profile.");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    status.setText("Finding profiles was interrupted.");
                } catch (ExecutionException exception) {
                    status.setText("Could not read Minecraft profiles: " + message(exception.getCause()));
                } finally {
                    scanningProfiles = false;
                }
            }
        }.execute();
    }

    public void searchFor(String query) {
        search.setText(query);
        if (profile.getSelectedItem() == null) {
            status.setText("Install a Minecraft profile first; content is installed per profile.");
            return;
        }
        search(false);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 5));
        header.setOpaque(false);
        JLabel heading = new JLabel("Mods, maps & texture packs");
        heading.setFont(new Font("Segoe UI", Font.BOLD, 22));
        heading.setForeground(FG);
        JLabel note = new JLabel("Browse Modrinth and CurseForge; required mod dependencies must be added separately.");
        note.setFont(BODY);
        note.setForeground(MUTED);
        header.add(heading, BorderLayout.NORTH);
        header.add(note, BorderLayout.CENTER);
        return header;
    }

    private JPanel buildBrowser() {
        JPanel browser = new JPanel(new BorderLayout(0, 10));
        browser.setOpaque(false);
        browser.add(buildFilters(), BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBackground(CARD);
        content.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        styleList();
        JScrollPane listScroll = new JScrollPane(resultList);
        listScroll.setBorder(BorderFactory.createLineBorder(new Color(54, 60, 82)));
        listScroll.getViewport().setBackground(FIELD);
        content.add(listScroll, BorderLayout.CENTER);
        content.add(buildDetails(), BorderLayout.SOUTH);
        browser.add(content, BorderLayout.CENTER);
        return browser;
    }

    private JPanel buildFilters() {
        JPanel filters = new JPanel(new GridBagLayout());
        filters.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 0, 5, 10);
        c.fill = GridBagConstraints.HORIZONTAL;
        addField(filters, c, 0, 0, "SOURCE", platform);
        addField(filters, c, 1, 0, "CONTENT", type);
        addField(filters, c, 2, 0, "MINECRAFT PROFILE", profile);
        c.gridy = 1;
        c.gridx = 0;
        c.gridwidth = 2;
        c.weightx = 1;
        search.setFont(BODY);
        search.setBackground(FIELD);
        search.setForeground(FG);
        search.setCaretColor(FG);
        search.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(64, 69, 92)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        filters.add(search, c);
        c.gridx = 2;
        c.gridwidth = 1;
        c.weightx = 0;
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        actions.setOpaque(false);
        styleButton(searchButton, true);
        styleButton(moreButton, false);
        actions.add(searchButton);
        actions.add(moreButton);
        filters.add(actions, c);
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 3;
        c.insets = new Insets(1, 0, 0, 0);
        status.setForeground(MUTED);
        status.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        filters.add(status, c);
        return filters;
    }

    private JPanel buildDetails() {
        JPanel details = new JPanel(new BorderLayout(10, 8));
        details.setOpaque(false);
        description.setEditable(false);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setRows(3);
        description.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        description.setForeground(FG);
        description.setBackground(CARD);
        description.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        JScrollPane descriptionScroll = new JScrollPane(description);
        descriptionScroll.setBorder(null);
        descriptionScroll.setOpaque(false);
        descriptionScroll.getViewport().setOpaque(false);
        details.add(descriptionScroll, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        styleButton(openProjectButton, false);
        styleButton(installButton, true);
        actions.add(openProjectButton);
        actions.add(installButton);
        details.add(actions, BorderLayout.EAST);
        progress.setPreferredSize(new Dimension(180, 7));
        progress.setVisible(false);
        progress.setForeground(PURPLE);
        progress.setBackground(FIELD);
        progress.setBorderPainted(false);
        details.add(progress, BorderLayout.SOUTH);
        return details;
    }

    private void addField(JPanel panel, GridBagConstraints c, int column, int row,
                          String title, Component component) {
        JPanel field = new JPanel(new BorderLayout(0, 4));
        field.setOpaque(false);
        JLabel label = new JLabel(title);
        label.setForeground(MUTED);
        label.setFont(new Font("Segoe UI", Font.BOLD, 10));
        field.add(label, BorderLayout.NORTH);
        styleCombo(component);
        component.setPreferredSize(new Dimension(column == 2 ? 300 : 180, 36));
        field.add(component, BorderLayout.CENTER);
        c.gridx = column;
        c.gridy = row;
        c.weightx = column == 2 ? 1 : 0;
        c.gridwidth = 1;
        panel.add(field, c);
    }

    private void styleCombo(Component component) {
        if (!(component instanceof JComboBox<?> combo)) return;
        combo.setBackground(FIELD);
        combo.setForeground(FG);
        combo.setFont(BODY);
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                JLabel item = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
                item.setBackground(selected ? new Color(76, 52, 112) : FIELD);
                item.setForeground(FG);
                item.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                return item;
            }
        });
    }

    private void styleList() {
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setFont(BODY);
        resultList.setForeground(FG);
        resultList.setBackground(FIELD);
        resultList.setFixedCellHeight(42);
        resultList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                JLabel item = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof ContentProject project) {
                    item.setText(project.title() + "  ·  " + project.author()
                            + "  ·  " + project.downloads() + " downloads");
                }
                item.setBackground(selected ? new Color(76, 52, 112) : FIELD);
                item.setForeground(FG);
                item.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
                return item;
            }
        });
    }

    private void search(boolean append) {
        if (busy) return;
        MinecraftProfile selectedProfile = (MinecraftProfile) profile.getSelectedItem();
        ContentPlatform selectedPlatform = (ContentPlatform) platform.getSelectedItem();
        ContentType selectedType = (ContentType) type.getSelectedItem();
        if (selectedProfile == null || selectedPlatform == null || selectedType == null) {
            status.setText("Choose an installed profile first.");
            return;
        }
        int offset = append ? nextOffset : 0;
        if (!append) {
            results.clear();
            nextOffset = 0;
            totalHits = 0;
        }
        setBusy(true);
        status.setText("Searching " + selectedPlatform.displayName() + "...");
        ContentSearchQuery query = new ContentSearchQuery(selectedType, search.getText(),
                selectedProfile, offset, 20);
        new SwingWorker<ContentSearchPage, Void>() {
            @Override
            protected ContentSearchPage doInBackground() throws Exception {
                return catalog.search(selectedPlatform, query);
            }

            @Override
            protected void done() {
                try {
                    ContentSearchPage page = get();
                    page.projects().forEach(results::addElement);
                    nextOffset = page.offset() + page.projects().size();
                    totalHits = page.totalHits();
                    status.setText(page.totalHits() == 0 ? "No compatible projects found."
                            : "Showing " + results.size() + " of " + totalHits + " compatible projects.");
                    if (results.size() > 0 && resultList.getSelectedIndex() < 0) {
                        resultList.setSelectedIndex(0);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    status.setText("Content search was interrupted.");
                } catch (ExecutionException exception) {
                    status.setText("Search failed: " + message(exception.getCause()));
                } finally {
                    setBusy(false);
                    updateSelectedProject();
                }
            }
        }.execute();
    }

    private void installSelected() {
        if (busy) return;
        ContentProject project = resultList.getSelectedValue();
        MinecraftProfile selectedProfile = (MinecraftProfile) profile.getSelectedItem();
        if (project == null || selectedProfile == null) return;
        if (!project.downloadAllowed()) {
            status.setText("This project does not allow downloads through third-party launchers.");
            return;
        }
        String warning = project.type() == ContentType.MODS
                ? "\n\nMods can execute code. Install only projects you trust."
                : "\n\nOnly install content from creators you trust.";
        int choice = JOptionPane.showConfirmDialog(this,
                "Install \"" + project.title() + "\" into:\n" + selectedProfile.directory() + warning,
                "Confirm content installation", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;
        setBusy(true);
        progress.setVisible(true);
        progress.setIndeterminate(true);
        status.setText("Finding a compatible file for " + project.title() + "...");
        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                ContentFile file = catalog.latestCompatibleFile(project, selectedProfile);
                return installer.install(project, file, selectedProfile);
            }

            @Override
            protected void done() {
                try {
                    Path installed = get();
                    status.setText("Installed " + project.title() + " at " + installed);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    status.setText("Content installation was interrupted.");
                } catch (ExecutionException exception) {
                    status.setText("Install failed: " + message(exception.getCause()));
                } finally {
                    progress.setVisible(false);
                    progress.setIndeterminate(false);
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void updateSelectedProject() {
        ContentProject selected = resultList.getSelectedValue();
        if (selected == null) {
            description.setText("");
            installButton.setEnabled(false);
            openProjectButton.setEnabled(false);
            return;
        }
        String restriction = selected.downloadAllowed() ? "" : "\n\nThis project does not allow third-party downloads.";
        description.setText(selected.summary() + restriction);
        installButton.setEnabled(!busy && selected.downloadAllowed());
        openProjectButton.setEnabled(!selected.projectUrl().isBlank());
    }

    private void updateProviderStatus() {
        if (platform.getSelectedItem() == ContentPlatform.CURSEFORGE) {
            String key = System.getenv("CURSEFORGE_API_KEY");
            status.setText(key == null || key.isBlank()
                    ? "CurseForge requires CURSEFORGE_API_KEY; set it in Windows environment variables and restart."
                    : "CurseForge API key detected. Search is ready.");
        } else if (type.getSelectedItem() == ContentType.MAPS) {
            status.setText("Modrinth does not publish maps as a project type; choose CurseForge for maps.");
        }
    }

    private void openProjectPage() {
        ContentProject selected = resultList.getSelectedValue();
        if (selected == null || selected.projectUrl().isBlank()) return;
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(selected.projectUrl()));
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException exception) {
            status.setText("Could not open project page: " + message(exception));
        }
    }

    private void setBusy(boolean value) {
        busy = value;
        platform.setEnabled(!value);
        type.setEnabled(!value);
        profile.setEnabled(!value && profile.getItemCount() > 0);
        search.setEnabled(!value);
        searchButton.setEnabled(!value && profile.getSelectedItem() != null);
        moreButton.setEnabled(!value && nextOffset < totalHits);
        installButton.setEnabled(!value && resultList.getSelectedValue() != null
                && resultList.getSelectedValue().downloadAllowed());
    }

    private static void styleButton(JButton button, boolean primary) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setForeground(FG);
        button.setBackground(primary ? PURPLE : FIELD);
        button.setBorder(BorderFactory.createEmptyBorder(9, 14, 9, 14));
        button.setFocusPainted(false);
    }

    private static String message(Throwable exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "Unexpected error.";
        }
        return exception.getMessage();
    }
}
