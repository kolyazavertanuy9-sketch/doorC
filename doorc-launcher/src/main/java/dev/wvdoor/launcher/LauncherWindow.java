package dev.wvdoor.launcher;

import dev.wvdoor.launcher.auth.ElyByAuthService;
import dev.wvdoor.launcher.auth.GameAccount;
import dev.wvdoor.launcher.content.ContentBrowserPanel;
import dev.wvdoor.launcher.install.InstanceInstaller;
import dev.wvdoor.launcher.install.MinecraftLaunchService;
import dev.wvdoor.launcher.metadata.MinecraftCatalog;
import dev.wvdoor.launcher.metadata.MinecraftCatalog.GameVersion;
import dev.wvdoor.launcher.metadata.MinecraftCatalog.Loader;
import dev.wvdoor.launcher.update.GitHubUpdateService;
import dev.wvdoor.launcher.update.GitHubUpdateService.CheckResult;
import dev.wvdoor.launcher.update.GitHubUpdateService.Release;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

public final class LauncherWindow {
    private static final Color BG = new Color(11, 13, 23);
    private static final Color SIDEBAR = new Color(14, 17, 29);
    private static final Color CARD = new Color(24, 29, 45);
    private static final Color CARD_LIGHT = new Color(34, 41, 61);
    private static final Color PURPLE = new Color(154, 91, 231);
    private static final Color PURPLE_BRIGHT = new Color(185, 122, 255);
    private static final Color FG = new Color(242, 244, 250);
    private static final Color MUTED = new Color(155, 165, 187);
    private static final Font BODY = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font BODY_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    private static final Font HEADING = new Font("Segoe UI", Font.BOLD, 22);
    private static final BufferedImage LAUNCHER_LOGO = resourceImage("/launcher/logo.png");

    private final LauncherSettings settings;
    private final MinecraftCatalog catalog = new MinecraftCatalog();
    private final GitHubUpdateService updateService = new GitHubUpdateService();
    private final InstanceInstaller installer;
    private final ElyByAuthService elyByAuth = new ElyByAuthService();
    private final MinecraftLaunchService launchService = new MinecraftLaunchService();
    private final JFrame frame = new JFrame();
    private final CardLayout pageLayout = new CardLayout();
    private final JPanel pageHost = new JPanel(pageLayout);
    private final ContentBrowserPanel contentBrowser;
    private final JLabel status = new JLabel("Connecting to official version catalogs...");
    private final JLabel updateState = new JLabel("Checking for updates...");
    private final JProgressBar progress = new JProgressBar();
    private final JButton checkUpdates = new ActionButton("Check updates", false);
    private final JComboBox<String> versionType = new JComboBox<>(new String[]{"Releases", "Snapshots"});
    private final JComboBox<GameVersion> gameVersion = new JComboBox<>();
    private final JComboBox<Loader> loader = new JComboBox<>(Loader.values());
    private final JComboBox<String> loaderVersion = new JComboBox<>();
    private final JTextField instanceName = new JTextField("Minecraft");
    private final JLabel destination = new JLabel();
    private final JButton standardInstall = new ActionButton("Install version", true);
    private final JButton doorCInstall = new ActionButton("Install DoorC", true);
    private final JButton launchDoorC = new ActionButton("Play DoorC", false);
    private final JButton accountButton = new ActionButton("Choose account", false);
    private final List<JButton> installActions = new ArrayList<>();
    private final List<NavButton> navigationButtons = new ArrayList<>();
    private final JTextField globalSearch = new PlaceholderTextField("Search the content catalog...");
    private JLabel accountStatus;
    private JLabel homeAccountStatus;
    private GameAccount activeAccount;
    private List<GameVersion> versions = List.of();
    private boolean loadingLoaderVersions;
    private boolean versionCatalogLoaded;
    private boolean installing;
    private boolean launching;
    private boolean signingIn;
    private boolean checkingForUpdates;
    private PointDrag drag;

    public LauncherWindow(LauncherSettings settings) {
        this.settings = settings;
        installer = new InstanceInstaller(catalog, settings::gameDirectory);
        contentBrowser = new ContentBrowserPanel(settings::gameDirectory);
        buildWindow();
        loadGameVersions();
    }

    public void show() {
        frame.setVisible(true);
        checkForUpdates(false);
    }

    private void buildWindow() {
        frame.setTitle("DoorC Launcher");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setUndecorated(true);
        frame.setMinimumSize(new Dimension(1080, 700));
        frame.setSize(1180, 780);
        frame.setLocationRelativeTo(null);
        if (LAUNCHER_LOGO != null) frame.setIconImage(LAUNCHER_LOGO);

        JPanel shell = new JPanel(new BorderLayout());
        shell.setBackground(BG);
        shell.setBorder(BorderFactory.createLineBorder(new Color(65, 53, 91), 1));
        frame.setContentPane(shell);
        shell.add(buildTitleBar(), BorderLayout.NORTH);

        JPanel workArea = new JPanel(new BorderLayout());
        workArea.setBackground(BG);
        workArea.add(buildSidebar(), BorderLayout.WEST);
        workArea.add(buildMainArea(), BorderLayout.CENTER);
        shell.add(workArea, BorderLayout.CENTER);
        shell.add(buildStatusBar(), BorderLayout.SOUTH);
        installActions.add(standardInstall);
        installActions.add(doorCInstall);
        standardInstall.addActionListener(event -> installSelectedInstance());
        doorCInstall.addActionListener(event -> installDoorC());
        accountButton.addActionListener(event -> chooseAccount());
        launchDoorC.setToolTipText("Choose an account and install DoorC before playing.");
        updateLaunchEnabled();
        versionType.addItemListener(event -> {
            if (event.getStateChange() == ItemEvent.SELECTED) updateVersionList();
        });
        gameVersion.addItemListener(event -> {
            if (event.getStateChange() == ItemEvent.SELECTED) {
                updateDestination();
                loadLoaderVersions();
            }
        });
        loader.addItemListener(event -> {
            if (event.getStateChange() == ItemEvent.SELECTED) loadLoaderVersions();
        });
        loaderVersion.addItemListener(event -> {
            if (event.getStateChange() == ItemEvent.SELECTED) updateInstallEnabled();
        });
        instanceName.getDocument().addDocumentListener(new SimpleDocumentListener(this::updateDestination));
    }

    private JPanel buildTitleBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(new Color(12, 14, 24));
        bar.setPreferredSize(new Dimension(100, 46));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(42, 42, 61)));
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 6));
        brand.setOpaque(false);
        if (LAUNCHER_LOGO != null) brand.add(new JLabel(logoIcon(30)));
        JLabel caption = label("DOORC LAUNCHER", 0, BODY_BOLD, MUTED);
        brand.add(caption);
        bar.add(brand, BorderLayout.WEST);

        JPanel windowActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        windowActions.setOpaque(false);
        JButton minimize = titleButton("—");
        minimize.addActionListener(event -> frame.setState(JFrame.ICONIFIED));
        JButton close = titleButton("×");
        close.addActionListener(event -> frame.dispose());
        windowActions.add(minimize);
        windowActions.add(close);
        bar.add(windowActions, BorderLayout.EAST);

        MouseAdapter moveWindow = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                drag = new PointDrag(event.getXOnScreen() - frame.getX(),
                        event.getYOnScreen() - frame.getY());
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (drag != null) frame.setLocation(event.getXOnScreen() - drag.x(),
                        event.getYOnScreen() - drag.y());
            }
        };
        bar.addMouseListener(moveWindow);
        bar.addMouseMotionListener(moveWindow);
        brand.addMouseListener(moveWindow);
        brand.addMouseMotionListener(moveWindow);
        caption.addMouseListener(moveWindow);
        caption.addMouseMotionListener(moveWindow);
        return bar;
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setBackground(SIDEBAR);
        sidebar.setPreferredSize(new Dimension(214, 100));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(39, 43, 61)));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(39, 43, 61)),
                BorderFactory.createEmptyBorder(18, 14, 16, 14)));

        JPanel brand = new JPanel(new BorderLayout(11, 0));
        brand.setOpaque(false);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel mark = LAUNCHER_LOGO == null
                ? label("D", 0, new Font("Segoe UI", Font.BOLD, 23), FG)
                : new JLabel(logoIcon(48));
        mark.setPreferredSize(new Dimension(48, 48));
        mark.setMinimumSize(new Dimension(48, 48));
        brand.add(mark, BorderLayout.WEST);
        JPanel brandText = new JPanel();
        brandText.setOpaque(false);
        brandText.setLayout(new BoxLayout(brandText, BoxLayout.Y_AXIS));
        brandText.add(label("DoorC", 0, new Font("Segoe UI", Font.BOLD, 19), FG));
        brandText.add(Box.createVerticalStrut(2));
        brandText.add(label("GAME LAUNCHER", 0, new Font("Segoe UI", Font.BOLD, 9), MUTED));
        brand.add(brandText, BorderLayout.CENTER);
        sidebar.add(brand);
        sidebar.add(Box.createVerticalStrut(24));
        sidebar.add(navButton("Home", "home"));
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(navButton("Play", "versions"));
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(navButton("Mods & More", "content"));
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(navButton("My installs", "library"));
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(navButton("Settings", "settings"));
        sidebar.add(Box.createVerticalGlue());

        RoundedPanel accountCard = new RoundedPanel(CARD, 14);
        accountCard.setLayout(new BorderLayout(8, 8));
        accountCard.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JLabel accountTitle = label("ACCOUNT", 0, BODY_BOLD, MUTED);
        accountStatus = label("<html>No account<br>selected</html>", 0, BODY, FG);
        accountCard.add(accountTitle, BorderLayout.NORTH);
        accountCard.add(accountStatus, BorderLayout.CENTER);
        accountCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        accountCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(accountCard);
        return sidebar;
    }

    private JPanel buildMainArea() {
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(BG);
        main.add(buildTopBar(), BorderLayout.NORTH);
        pageHost.setOpaque(false);
        pageHost.add(buildHomePage(), "home");
        pageHost.add(buildVersionsPage(), "versions");
        pageHost.add(buildLibraryPage(), "library");
        pageHost.add(contentBrowser, "content");
        pageHost.add(buildSettingsPage(), "settings");
        main.add(pageHost, BorderLayout.CENTER);
        return main;
    }

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(12, 24, 10, 24));
        JLabel crumb = label("DOORC LAUNCHER  ·  v" + updateService.currentVersion(),
                0, BODY_BOLD, MUTED);
        JPanel search = new JPanel(new BorderLayout(8, 0));
        search.setBackground(CARD);
        search.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(54, 59, 80)),
                BorderFactory.createEmptyBorder(2, 12, 2, 12)));
        JLabel searchIcon = label("⌕", 0, new Font("Segoe UI Symbol", Font.PLAIN, 24), MUTED);
        search.add(searchIcon, BorderLayout.WEST);
        globalSearch.setFont(BODY);
        globalSearch.setBackground(CARD);
        globalSearch.setForeground(FG);
        globalSearch.setCaretColor(FG);
        globalSearch.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        globalSearch.addActionListener(event -> {
            String query = globalSearch.getText().trim();
            if (query.isEmpty()) return;
            showPage("content");
            contentBrowser.searchFor(query);
        });
        search.add(globalSearch, BorderLayout.CENTER);
        search.setPreferredSize(new Dimension(430, 42));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        updateState.setToolTipText("Stable launcher update channel");
        bar.add(crumb, BorderLayout.WEST);
        updateState.setForeground(MUTED);
        updateState.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        actions.add(updateState);
        checkUpdates.addActionListener(event -> checkForUpdates(true));
        actions.add(checkUpdates);
        actions.add(accountButton);
        bar.add(search, BorderLayout.CENTER);
        bar.add(actions, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildHomePage() {
        JPanel page = pagePanel();
        page.setLayout(new BorderLayout(0, 18));
        page.setBorder(BorderFactory.createEmptyBorder(6, 28, 24, 28));
        JPanel intro = new JPanel(new BorderLayout());
        intro.setOpaque(false);
        JLabel title = label("Welcome back", 0, HEADING, FG);
        JLabel subtitle = label("Choose your game. Keep every install separate.", 0, BODY, MUTED);
        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        copy.add(title);
        copy.add(Box.createVerticalStrut(4));
        copy.add(subtitle);
        intro.add(copy, BorderLayout.WEST);
        page.add(intro, BorderLayout.NORTH);

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(0, 0, 14, 12);
        c.gridx = 0;
        c.weightx = 0.7;
        content.add(buildHeroCard(), c);
        c.gridx = 1;
        c.weightx = 0.3;
        c.insets = new Insets(0, 0, 14, 0);
        content.add(buildHomeHighlights(), c);
        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 1;
        c.weighty = 0;
        c.gridwidth = 2;
        c.insets = new Insets(0, 0, 0, 0);
        content.add(buildQuickCards(), c);
        page.add(content, BorderLayout.CENTER);
        return page;
    }

    private JPanel buildHomeHighlights() {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        RoundedPanel account = new RoundedPanel(CARD, 14);
        account.setLayout(new BorderLayout(0, 10));
        account.setBorder(BorderFactory.createEmptyBorder(15, 16, 15, 16));
        account.add(label("YOUR ACCOUNT", 0, BODY_BOLD, PURPLE_BRIGHT), BorderLayout.NORTH);
        homeAccountStatus = label("No account selected", 0, BODY_BOLD, FG);
        account.add(homeAccountStatus, BorderLayout.CENTER);
        JButton choose = new ActionButton("Choose account  →", false);
        choose.addActionListener(event -> chooseAccount());
        account.add(choose, BorderLayout.SOUTH);
        account.setMaximumSize(new Dimension(Integer.MAX_VALUE, 135));
        account.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(account);
        column.add(Box.createVerticalStrut(12));

        RoundedPanel explore = new RoundedPanel(CARD, 14);
        explore.setLayout(new BorderLayout(0, 10));
        explore.setBorder(BorderFactory.createEmptyBorder(15, 16, 15, 16));
        explore.add(label("DISCOVER CONTENT", 0, BODY_BOLD, PURPLE_BRIGHT), BorderLayout.NORTH);
        explore.add(label("<html>Browse mods, maps and<br>texture packs for your<br>installed profiles.</html>",
                0, BODY, FG), BorderLayout.CENTER);
        JButton openContent = new ActionButton("Explore  →", true);
        openContent.addActionListener(event -> showPage("content"));
        explore.add(openContent, BorderLayout.SOUTH);
        explore.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
        explore.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(explore);
        column.setPreferredSize(new Dimension(240, 300));
        return column;
    }

    private JPanel buildHeroCard() {
        BufferedImage background = resourceImage("/launcher/hero.png");
        HeroPanel hero = new HeroPanel(background);
        hero.setLayout(new BorderLayout());
        hero.setBorder(BorderFactory.createEmptyBorder(26, 28, 22, 28));
        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        JLabel badge = label("FEATURED CLIENT", 0, BODY_BOLD, new Color(216, 189, 255));
        JLabel title = label("DoorC", 0, new Font("Segoe UI", Font.BOLD, 38), FG);
        JLabel subtitle = label("Minecraft 1.21.11   ·   Fabric   ·   WVlib included", 0,
                BODY, new Color(222, 225, 238));
        copy.add(badge);
        copy.add(Box.createVerticalStrut(8));
        copy.add(title);
        copy.add(Box.createVerticalStrut(3));
        copy.add(subtitle);
        copy.add(Box.createVerticalStrut(18));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        actions.add(doorCInstall);
        launchDoorC.addActionListener(event -> launchDoorCGame());
        actions.add(launchDoorC);
        copy.add(actions);
        hero.add(copy, BorderLayout.CENTER);

        RoundedPanel note = new RoundedPanel(new Color(9, 10, 18, 190), 12);
        note.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        note.add(label("ISOLATED PROFILE", 0, BODY_BOLD, FG));
        hero.add(note, BorderLayout.SOUTH);
        hero.setPreferredSize(new Dimension(560, 300));
        return hero;
    }

    private JPanel buildQuickCards() {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(0, 0, 0, 8);
        RoundedPanel versionsCard = new RoundedPanel(CARD, 14);
        versionsCard.setBorder(BorderFactory.createEmptyBorder(16, 17, 15, 17));
        versionsCard.setLayout(new BorderLayout(0, 7));
        versionsCard.add(label("ALL VERSIONS", 0, BODY_BOLD, PURPLE_BRIGHT), BorderLayout.NORTH);
        versionsCard.add(label("<html>Browse releases, snapshots and<br>available mod loaders.</html>",
                0, BODY, FG), BorderLayout.CENTER);
        JButton browse = new ActionButton("Browse versions  →", false);
        browse.addActionListener(event -> showPage("versions"));
        versionsCard.add(browse, BorderLayout.SOUTH);
        row.add(versionsCard, c);

        c.gridx = 1;
        c.insets = new Insets(0, 8, 0, 8);
        RoundedPanel folderCard = new RoundedPanel(CARD, 14);
        folderCard.setBorder(BorderFactory.createEmptyBorder(16, 17, 15, 17));
        folderCard.setLayout(new BorderLayout(0, 7));
        folderCard.add(label("YOUR LIBRARY", 0, BODY_BOLD, PURPLE_BRIGHT), BorderLayout.NORTH);
        folderCard.add(label("<html>Game files stay in a location<br>you choose during setup.</html>",
                0, BODY, FG), BorderLayout.CENTER);
        JButton library = new ActionButton("Open my installs  →", false);
        library.addActionListener(event -> showPage("library"));
        folderCard.add(library, BorderLayout.SOUTH);
        row.add(folderCard, c);
        c.gridx = 2;
        c.insets = new Insets(0, 8, 0, 0);
        RoundedPanel contentCard = new RoundedPanel(CARD, 14);
        contentCard.setBorder(BorderFactory.createEmptyBorder(16, 17, 15, 17));
        contentCard.setLayout(new BorderLayout(0, 7));
        contentCard.add(label("MODS & MORE", 0, BODY_BOLD, PURPLE_BRIGHT), BorderLayout.NORTH);
        contentCard.add(label("<html>Find content for your<br>installed profiles.</html>",
                0, BODY, FG), BorderLayout.CENTER);
        JButton browseContent = new ActionButton("Browse content  →", false);
        browseContent.addActionListener(event -> showPage("content"));
        contentCard.add(browseContent, BorderLayout.SOUTH);
        row.add(contentCard, c);
        row.setPreferredSize(new Dimension(500, 140));
        return row;
    }

    private JPanel buildVersionsPage() {
        JPanel page = pagePanel();
        page.setLayout(new BorderLayout(0, 16));
        page.setBorder(BorderFactory.createEmptyBorder(8, 28, 24, 28));
        page.add(pageHeading("Version library", "Install a clean profile with the version and loader you want."),
                BorderLayout.NORTH);

        RoundedPanel form = new RoundedPanel(CARD, 15);
        form.setLayout(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 0, 8, 16);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        addField(form, c, 0, 0, "VERSION CHANNEL");
        addField(form, c, 0, 1, "MINECRAFT VERSION");
        addField(form, c, 0, 2, "MOD LOADER");
        addField(form, c, 0, 3, "LOADER BUILD");
        addField(form, c, 0, 4, "INSTANCE NAME");
        addControl(form, c, versionType, 1, 0);
        addControl(form, c, gameVersion, 1, 1);
        addControl(form, c, loader, 1, 2);
        addControl(form, c, loaderVersion, 1, 3);
        addControl(form, c, instanceName, 1, 4);
        c.gridx = 0;
        c.gridy = 5;
        c.gridwidth = 2;
        c.insets = new Insets(12, 0, 8, 0);
        destination.setForeground(MUTED);
        destination.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        form.add(destination, c);
        c.gridy = 6;
        c.insets = new Insets(10, 0, 0, 0);
        standardInstall.setPreferredSize(new Dimension(190, 42));
        form.add(standardInstall, c);
        page.add(form, BorderLayout.CENTER);

        RoundedPanel details = new RoundedPanel(CARD_LIGHT, 14);
        details.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        details.setLayout(new BorderLayout(8, 6));
        details.add(label("SUPPORTED LOADERS", 0, BODY_BOLD, PURPLE_BRIGHT), BorderLayout.NORTH);
        details.add(label("Vanilla  ·  Fabric  ·  Forge  ·  NeoForge  ·  Quilt", 0, BODY, FG),
                BorderLayout.CENTER);
        page.add(details, BorderLayout.SOUTH);
        return page;
    }

    private JPanel buildLibraryPage() {
        JPanel page = pagePanel();
        page.setLayout(new BorderLayout(0, 16));
        page.setBorder(BorderFactory.createEmptyBorder(8, 28, 24, 28));
        page.add(pageHeading("My installations", "Each version has its own folder, settings and mods."),
                BorderLayout.NORTH);
        RoundedPanel content = new RoundedPanel(CARD, 15);
        content.setLayout(new BorderLayout(12, 12));
        content.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        Path instances = settings.gameDirectory().resolve("instances");
        if (Files.isDirectory(instances)) {
            try (var directories = Files.list(instances)) {
                List<Path> found = directories.filter(Files::isDirectory).toList();
                for (Path directory : found) list.add(instanceRow(directory));
                if (found.isEmpty()) list.add(emptyLibraryMessage());
            } catch (IOException exception) {
                list.add(label("Could not read the selected game folder: " + exception.getMessage(),
                        0, BODY, new Color(255, 142, 142)));
            }
        } else {
            list.add(emptyLibraryMessage());
        }
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        content.add(scroll, BorderLayout.CENTER);
        JButton openFolder = new ActionButton("Open game folder", false);
        openFolder.addActionListener(event -> openFolder(settings.gameDirectory()));
        content.add(openFolder, BorderLayout.SOUTH);
        page.add(content, BorderLayout.CENTER);
        return page;
    }

    private JPanel buildSettingsPage() {
        JPanel page = pagePanel();
        page.setLayout(new BorderLayout(0, 16));
        page.setBorder(BorderFactory.createEmptyBorder(8, 28, 24, 28));
        page.add(pageHeading("Settings", "Choose where Minecraft versions and DoorC files are stored."),
                BorderLayout.NORTH);
        RoundedPanel options = new RoundedPanel(CARD, 15);
        options.setLayout(new GridBagLayout());
        options.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(9, 0, 9, 12);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        addField(options, c, 0, 0, "GAME DATA FOLDER");
        JTextField gamePath = styledField(settings.gameDirectory().toString());
        JButton browse = new ActionButton("Browse", false);
        browse.addActionListener(event -> FirstRunSetup.chooseDirectory(frame, gamePath));
        JPanel chooser = new JPanel(new BorderLayout(8, 0));
        chooser.setOpaque(false);
        chooser.add(gamePath, BorderLayout.CENTER);
        chooser.add(browse, BorderLayout.EAST);
        addControl(options, c, chooser, 1, 0);
        JButton savePath = new ActionButton("Save folder", true);
        savePath.addActionListener(event -> saveGameDirectory(gamePath));
        c.gridx = 1;
        c.gridy = 1;
        options.add(savePath, c);
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 2;
        JLabel auth = label("<html><b>Account types</b><br>"
                + "Offline profiles and Ely.by are available. Microsoft sign-in requires an approved application ID.</html>",
                0, BODY, MUTED);
        options.add(auth, c);
        c.gridy = 3;
        JLabel shortcuts = label("<html><b>Windows shortcuts</b><br>"
                + "The installer adds DoorC to the Start Menu and can create a desktop shortcut.</html>",
                0, BODY, MUTED);
        options.add(shortcuts, c);
        c.gridy = 4;
        JLabel updateInfo = label("<html><b>Launcher updates</b><br>"
                + "Stable releases are checked on startup. You choose whether to download and install each update.</html>",
                0, BODY, MUTED);
        options.add(updateInfo, c);
        page.add(options, BorderLayout.NORTH);
        return page;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(new Color(12, 14, 24));
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(42, 42, 61)));
        status.setForeground(MUTED);
        status.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        status.setBorder(BorderFactory.createEmptyBorder(9, 16, 9, 8));
        bar.add(status, BorderLayout.CENTER);
        progress.setPreferredSize(new Dimension(150, 8));
        progress.setVisible(false);
        progress.setForeground(PURPLE);
        progress.setBackground(CARD_LIGHT);
        progress.setBorderPainted(false);
        bar.add(progress, BorderLayout.EAST);
        return bar;
    }

    private JPanel pageHeading(String title, String subtitle) {
        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.add(label(title, 0, HEADING, FG));
        heading.add(Box.createVerticalStrut(5));
        heading.add(label(subtitle, 0, BODY, MUTED));
        return heading;
    }

    private JPanel pagePanel() {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(false);
        return page;
    }

    private NavButton navButton(String text, String page) {
        NavButton button = new NavButton(text, page);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        navigationButtons.add(button);
        return button;
    }

    private JButton titleButton(String text) {
        JButton button = new JButton(text);
        button.setForeground(MUTED);
        button.setBackground(new Color(12, 14, 24));
        button.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        button.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
        button.setFocusPainted(false);
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                button.setForeground(FG);
            }

            @Override
            public void mouseExited(MouseEvent event) {
                button.setForeground(MUTED);
            }
        });
        return button;
    }

    private void addField(JPanel panel, GridBagConstraints c, int column, int row, String text) {
        c.gridx = column;
        c.gridy = row;
        c.weightx = 0;
        c.gridwidth = 1;
        JLabel field = label(text, 0, BODY_BOLD, MUTED);
        panel.add(field, c);
    }

    private void addControl(JPanel panel, GridBagConstraints c, Component control, int column, int row) {
        c.gridx = column;
        c.gridy = row;
        c.weightx = 1;
        c.gridwidth = 1;
        control.setPreferredSize(new Dimension(400, 38));
        if (control instanceof JComboBox<?> combo) styleCombo(combo);
        if (control instanceof JTextField field) styleField(field);
        panel.add(control, c);
        c.weightx = 0;
    }

    private JTextField styledField(String value) {
        JTextField field = new JTextField(value);
        styleField(field);
        return field;
    }

    private void styleField(JTextField field) {
        field.setBackground(CARD_LIGHT);
        field.setForeground(FG);
        field.setCaretColor(FG);
        field.setFont(BODY);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(64, 69, 92)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
    }

    private void styleCombo(JComboBox<?> combo) {
        combo.setBackground(CARD_LIGHT);
        combo.setForeground(FG);
        combo.setFont(BODY);
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel item = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                item.setBackground(isSelected ? new Color(76, 52, 112) : CARD_LIGHT);
                item.setForeground(FG);
                item.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
                return item;
            }
        });
    }

    private JLabel label(String text, int alignment, Font font, Color color) {
        JLabel label = new JLabel(text, alignment);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    private JPanel instanceRow(Path directory) {
        RoundedPanel row = new RoundedPanel(CARD_LIGHT, 12);
        row.setLayout(new BorderLayout(12, 0));
        row.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 12));
        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        copy.add(label(directory.getFileName().toString(), 0, BODY_BOLD, FG));
        copy.add(Box.createVerticalStrut(3));
        copy.add(label(directory.toString(), 0, new Font("Segoe UI", Font.PLAIN, 11), MUTED));
        row.add(copy, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton open = new ActionButton("Folder", false);
        open.addActionListener(event -> openFolder(directory));
        JButton launch = new ActionButton("Launch", false);
        launch.setEnabled(false);
        launch.setToolTipText("Launching standard profiles is not available yet.");
        actions.add(open);
        actions.add(launch);
        row.add(actions, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 74));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private JPanel emptyLibraryMessage() {
        JPanel empty = new JPanel(new BorderLayout(0, 8));
        empty.setOpaque(false);
        empty.add(label("No game versions installed yet.", 0, BODY_BOLD, FG), BorderLayout.NORTH);
        empty.add(label("Browse the version library to add your first profile.", 0, BODY, MUTED),
                BorderLayout.CENTER);
        return empty;
    }

    private void loadGameVersions() {
        status.setText("Loading the official Minecraft version list...");
        new SwingWorker<List<GameVersion>, Void>() {
            @Override
            protected List<GameVersion> doInBackground() throws Exception {
                return catalog.getGameVersions();
            }

            @Override
            protected void done() {
                try {
                    versions = get();
                    versionCatalogLoaded = true;
                    updateVersionList();
                    status.setText("Ready  ·  " + versions.size() + " official game versions available");
                    updateInstallEnabled();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError("Loading versions was interrupted.", exception);
                } catch (ExecutionException exception) {
                    showError("Could not load Minecraft versions.", exception.getCause());
                }
            }
        }.execute();
    }

    private void checkForUpdates(boolean announceWhenCurrent) {
        if (checkingForUpdates) return;
        checkingForUpdates = true;
        checkUpdates.setEnabled(false);
        updateState.setText("Checking updates...");
        status.setText("Checking the stable DoorC Launcher release channel...");
        new SwingWorker<CheckResult, Void>() {
            @Override
            protected CheckResult doInBackground() throws Exception {
                return updateService.checkForUpdate();
            }

            @Override
            protected void done() {
                checkingForUpdates = false;
                checkUpdates.setEnabled(true);
                try {
                    CheckResult result = get();
                    if (!result.hasPublishedRelease()) {
                        updateState.setText("No release published");
                        status.setText("The stable update channel has no published release yet.");
                        if (announceWhenCurrent) {
                            javax.swing.JOptionPane.showMessageDialog(frame,
                                    "There is no stable launcher release published on GitHub yet.",
                                    "DoorC Launcher updates", javax.swing.JOptionPane.INFORMATION_MESSAGE);
                        }
                    } else if (result.update().isPresent()) {
                        Release release = result.update().orElseThrow();
                        updateState.setText("Update available");
                        status.setText("DoorC Launcher " + release.version() + " is available.");
                        showUpdateAvailable(release);
                    } else {
                        updateState.setText("Up to date");
                        status.setText("DoorC Launcher is up to date (v" + updateService.currentVersion() + ").");
                        if (announceWhenCurrent) {
                            javax.swing.JOptionPane.showMessageDialog(frame,
                                    "You are using the latest stable version ("
                                            + updateService.currentVersion() + ").",
                                    "DoorC Launcher updates", javax.swing.JOptionPane.INFORMATION_MESSAGE);
                        }
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    updateState.setText("Update check interrupted");
                    if (announceWhenCurrent) showError("Checking for updates was interrupted.", exception);
                } catch (ExecutionException exception) {
                    updateState.setText("Update check failed");
                    status.setText("Could not check for launcher updates.");
                    if (announceWhenCurrent) showError("Could not check for launcher updates.", exception.getCause());
                }
            }
        }.execute();
    }

    private void showUpdateAvailable(Release release) {
        String notes = release.notes() == null || release.notes().isBlank()
                ? "No release notes were provided."
                : release.notes().strip();
        if (notes.length() > 1600) notes = notes.substring(0, 1597) + "...";
        String message = "DoorC Launcher " + release.version() + " is ready.\n\n"
                + notes + "\n\nThe installer will verify its SHA-256 checksum and update this installation.";
        Object[] options = {"Download update", "Later"};
        int choice = javax.swing.JOptionPane.showOptionDialog(frame, message,
                "Launcher update available", javax.swing.JOptionPane.DEFAULT_OPTION,
                javax.swing.JOptionPane.INFORMATION_MESSAGE, null, options, options[1]);
        if (choice == 0) downloadUpdate(release);
    }

    private void downloadUpdate(Release release) {
        checkUpdates.setEnabled(false);
        frame.setEnabled(false);
        progress.setVisible(true);
        progress.setIndeterminate(true);
        status.setText("Downloading and verifying DoorC Launcher " + release.version() + "...");
        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                return updateService.downloadAndVerify(release);
            }

            @Override
            protected void done() {
                progress.setVisible(false);
                checkUpdates.setEnabled(true);
                try {
                    Path installer = get();
                    int choice = javax.swing.JOptionPane.showConfirmDialog(frame,
                            "Update " + release.version() + " is downloaded and verified.\n"
                                    + "The launcher will close while the installer updates this installation.",
                            "Install launcher update", javax.swing.JOptionPane.OK_CANCEL_OPTION,
                            javax.swing.JOptionPane.INFORMATION_MESSAGE);
                    if (choice == javax.swing.JOptionPane.OK_OPTION) launchUpdateInstaller(installer);
                    else status.setText("Update downloaded. Choose Check updates when you are ready to install it.");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError("Downloading the update was interrupted.", exception);
                } catch (ExecutionException exception) {
                    updateState.setText("Update download failed");
                    showError("Could not download or verify the launcher update.", exception.getCause());
                }
            }
        }.execute();
    }

    private void launchUpdateInstaller(Path installer) {
        var applicationDirectory = LauncherSettings.applicationInstallDirectory();
        Path gameDirectory = settings.gameDirectory().toAbsolutePath().normalize();
        if (applicationDirectory.isPresent()
                && GameDataMigration.isInsideInstallation(gameDirectory, applicationDirectory.orElseThrow())) {
            preserveGameDataBeforeUpdate(installer, gameDirectory, applicationDirectory.orElseThrow());
            return;
        }
        startUpdateInstaller(installer);
    }

    private void preserveGameDataBeforeUpdate(Path installer, Path source, Path applicationDirectory) {
        Path destination;
        try {
            destination = GameDataMigration.createDestination(applicationDirectory);
        } catch (IOException exception) {
            showError("Could not choose a safe game-data folder. The update was not started.", exception);
            return;
        }
        int choice = javax.swing.JOptionPane.showConfirmDialog(frame,
                "Your game data is stored inside the launcher installation folder and may be removed by "
                        + "the update.\n\nCopy it to this safe folder before updating?\n" + destination,
                "Protect Minecraft data", javax.swing.JOptionPane.YES_NO_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE);
        if (choice != javax.swing.JOptionPane.YES_OPTION) {
            status.setText("Update cancelled. Game data was left in place.");
            return;
        }

        checkUpdates.setEnabled(false);
        progress.setVisible(true);
        progress.setIndeterminate(true);
        status.setText("Copying game data to a safe folder before updating...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                GameDataMigration.copy(source, destination, applicationDirectory);
                settings.setGameDirectory(destination);
                return null;
            }

            @Override
            protected void done() {
                progress.setVisible(false);
                progress.setIndeterminate(false);
                checkUpdates.setEnabled(true);
                frame.setEnabled(true);
                try {
                    get();
                    status.setText("Game data was copied. Starting the launcher update...");
                    startUpdateInstaller(installer);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError("Data migration was interrupted. The update was not started.", exception);
                } catch (ExecutionException exception) {
                    showError("Could not preserve game data. The update was not started.",
                            exception.getCause());
                }
            }
        }.execute();
    }

    private void startUpdateInstaller(Path installer) {
        try {
            new ProcessBuilder(installer.toAbsolutePath().toString()).start();
            status.setText("Closing the launcher for update installation...");
            frame.dispose();
            System.exit(0);
        } catch (IOException exception) {
            showError("Could not start the launcher update installer.", exception);
        }
    }

    private void updateVersionList() {
        String selection = gameVersion.getSelectedItem() instanceof GameVersion current ? current.id() : null;
        boolean snapshots = "Snapshots".equals(versionType.getSelectedItem());
        DefaultComboBoxModel<GameVersion> model = new DefaultComboBoxModel<>();
        for (GameVersion version : versions) {
            if (snapshots == version.isSnapshot()) model.addElement(version);
        }
        gameVersion.setModel(model);
        if (selection != null) {
            for (int i = 0; i < model.getSize(); i++) {
                if (model.getElementAt(i).id().equals(selection)) {
                    gameVersion.setSelectedIndex(i);
                    break;
                }
            }
        }
        if (model.getSize() > 0) gameVersion.setSelectedIndex(0);
        updateDestination();
    }

    private void loadLoaderVersions() {
        if (loadingLoaderVersions || !(gameVersion.getSelectedItem() instanceof GameVersion selected)
                || !(loader.getSelectedItem() instanceof Loader selectedLoader)) return;
        if (selectedLoader == Loader.VANILLA) {
            loaderVersion.setModel(new DefaultComboBoxModel<>(new String[]{"Vanilla"}));
            updateInstallEnabled();
            return;
        }

        loadingLoaderVersions = true;
        updateInstallEnabled();
        loaderVersion.setModel(new DefaultComboBoxModel<>(new String[]{"Loading..."}));
        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                return catalog.getLoaderVersions(selected.id(), selectedLoader);
            }

            @Override
            protected void done() {
                try {
                    List<String> result = get();
                    if (gameVersion.getSelectedItem() == selected
                            && loader.getSelectedItem() == selectedLoader) {
                        loaderVersion.setModel(new DefaultComboBoxModel<>(result.toArray(String[]::new)));
                        status.setText(result.isEmpty()
                                ? selectedLoader.displayName() + " is unavailable for " + selected.id()
                                : "Ready  ·  " + selectedLoader.displayName() + " builds available");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError("Loading loader versions was interrupted.", exception);
                } catch (ExecutionException exception) {
                    showError("Could not load " + selectedLoader.displayName() + " versions.",
                            exception.getCause());
                } finally {
                    loadingLoaderVersions = false;
                    updateInstallEnabled();
                    if (gameVersion.getSelectedItem() != selected
                            || loader.getSelectedItem() != selectedLoader) loadLoaderVersions();
                }
            }
        }.execute();
    }

    private void updateInstallEnabled() {
        if (!versionCatalogLoaded || loadingLoaderVersions) {
            standardInstall.setEnabled(false);
            return;
        }
        if (loader.getSelectedItem() == Loader.VANILLA) {
            standardInstall.setEnabled(gameVersion.getSelectedItem() != null);
            return;
        }
        Object selectedVersion = loaderVersion.getSelectedItem();
        standardInstall.setEnabled(selectedVersion != null && !"Loading...".equals(selectedVersion));
    }

    private void installSelectedInstance() {
        if (!(gameVersion.getSelectedItem() instanceof GameVersion selected)
                || !(loader.getSelectedItem() instanceof Loader selectedLoader)) return;
        String selectedLoaderVersion = (String) loaderVersion.getSelectedItem();
        if (selectedLoader != Loader.VANILLA
                && (selectedLoaderVersion == null || selectedLoaderVersion.equals("Loading..."))) {
            status.setText("Choose a compatible loader build first.");
            return;
        }
        String name = safeInstanceName(instanceName.getText());
        Path target = settings.gameDirectory().resolve("instances").resolve("standard").resolve(name);
        runInstall("Preparing " + selected.id(), target, progress -> installer.install(
                selected, selectedLoader, selectedLoaderVersion, target, progress));
    }

    private void installDoorC() {
        Path target = settings.gameDirectory().resolve("instances").resolve("DoorC-1.21.11");
        runInstall("Preparing DoorC 1.21.11", target, progress -> installer.installDoorC(target, progress));
    }

    private void runInstall(String initialStatus, Path target, InstallAction action) {
        if (installing) return;
        installing = true;
        setActionsEnabled(false);
        progress.setVisible(true);
        progress.setIndeterminate(true);
        status.setText(initialStatus);
        new SwingWorker<Void, InstallProgress>() {
            @Override
            protected Void doInBackground() throws Exception {
                action.run((message, completed, total) ->
                        publish(new InstallProgress(message, completed, total)));
                return null;
            }

            @Override
            protected void process(List<InstallProgress> updates) {
                InstallProgress update = updates.get(updates.size() - 1);
                status.setText(update.message());
                if (update.total() > 0) {
                    progress.setIndeterminate(false);
                    progress.setMaximum(update.total());
                    progress.setValue(update.completed());
                }
            }

            @Override
            protected void done() {
                try {
                    get();
                    status.setText("Installed separately at " + target);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError("Installation was interrupted.", exception);
                } catch (ExecutionException exception) {
                    showError("Installation failed.", exception.getCause());
                } finally {
                    installing = false;
                    progress.setVisible(false);
                    updateInstallEnabled();
                    doorCInstall.setEnabled(true);
                    updateLaunchEnabled();
                }
            }
        }.execute();
    }

    private void setActionsEnabled(boolean enabled) {
        for (JButton button : installActions) button.setEnabled(enabled);
        updateLaunchEnabled();
    }

    private void updateDestination() {
        if (destination == null) return;
        String version = gameVersion.getSelectedItem() instanceof GameVersion selected
                ? selected.id() : "version";
        String name = safeInstanceName(instanceName.getText().isBlank() ? version : instanceName.getText());
        destination.setText("INSTALL LOCATION   " + settings.gameDirectory()
                .resolve("instances").resolve("standard").resolve(name));
    }

    private void showPage(String key) {
        for (NavButton button : navigationButtons) {
            button.selected = key.equals(button.page);
            button.repaint();
        }
        pageLayout.show(pageHost, key);
        if ("library".equals(key)) {
            pageHost.remove(2);
            pageHost.add(buildLibraryPage(), "library", 2);
            pageLayout.show(pageHost, "library");
        } else if ("content".equals(key)) {
            contentBrowser.refreshTargets();
        }
    }

    private void saveGameDirectory(JTextField field) {
        try {
            Path selected = Path.of(field.getText().trim()).toAbsolutePath().normalize();
            if (LauncherSettings.gameDataIsInsideApplication(selected)) {
                throw new IllegalArgumentException(
                        "Choose a game-data folder outside the launcher installation folder.");
            }
            Files.createDirectories(selected);
            Path probe = Files.createTempFile(selected, ".doorc-write-check-", ".tmp");
            Files.delete(probe);
            settings.setGameDirectory(selected);
            status.setText("Game files will be stored in " + selected);
            updateDestination();
        } catch (IOException | RuntimeException exception) {
            showError("Could not use that folder.", exception);
        }
    }

    private void openFolder(Path folder) {
        try {
            Files.createDirectories(folder);
            java.awt.Desktop.getDesktop().open(folder.toFile());
        } catch (IOException | UnsupportedOperationException exception) {
            showError("Could not open folder.", exception);
        }
    }

    private void chooseAccount() {
        if (signingIn) return;
        String[] providers = {"Offline profile", "Ely.by account", "Microsoft account"};
        String selected = (String) javax.swing.JOptionPane.showInputDialog(frame,
                "Choose an account provider for DoorC.", "Game account",
                javax.swing.JOptionPane.PLAIN_MESSAGE, null, providers, providers[0]);
        if (selected == null) return;
        switch (selected) {
            case "Offline profile" -> chooseOfflineAccount();
            case "Ely.by account" -> chooseElyByAccount();
            case "Microsoft account" -> showMicrosoftRequirement();
            default -> throw new IllegalStateException("Unknown account provider.");
        }
    }

    private void chooseOfflineAccount() {
        String rememberedName = activeAccount != null
                && activeAccount.provider() == GameAccount.Provider.OFFLINE
                ? activeAccount.playerName() : settings.offlinePlayerName();
        JTextField name = styledField(rememberedName);
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.add(name, BorderLayout.NORTH);
        content.add(label("<html>Works in single-player and on servers that allow offline profiles.<br>"
                + "It does not sign in to Microsoft or Ely.by online servers.</html>",
                0, new Font("Segoe UI", Font.PLAIN, 11), MUTED), BorderLayout.CENTER);
        int choice = javax.swing.JOptionPane.showConfirmDialog(frame, content,
                "Offline profile", javax.swing.JOptionPane.OK_CANCEL_OPTION,
                javax.swing.JOptionPane.PLAIN_MESSAGE);
        if (choice != javax.swing.JOptionPane.OK_OPTION) return;
        try {
            GameAccount account = GameAccount.offline(name.getText().trim());
            settings.setOfflinePlayerName(account.playerName());
            setAccount(account);
        } catch (IllegalArgumentException exception) {
            showError("Invalid offline nickname.", exception);
        } catch (IOException exception) {
            showError("Could not save the offline profile name.", exception);
        }
    }

    private void chooseElyByAccount() {
        JTextField username = styledField("");
        JPasswordField password = new JPasswordField(22);
        JPanel fields = new JPanel(new GridBagLayout());
        fields.setPreferredSize(new Dimension(380, 116));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 4, 6, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0;
        c.gridx = 0;
        c.gridy = 0;
        fields.add(label("Username or email", 0, BODY, FG), c);
        c.gridy = 1;
        fields.add(label("Password", 0, BODY, FG), c);
        c.gridx = 1;
        c.gridy = 0;
        c.weightx = 1;
        fields.add(username, c);
        c.gridy = 1;
        fields.add(password, c);
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 2;
        fields.add(label("Credentials are sent to Ely.by over HTTPS and are not saved.",
                0, new Font("Segoe UI", Font.PLAIN, 11), MUTED), c);
        int choice = javax.swing.JOptionPane.showConfirmDialog(frame, fields,
                "Sign in to Ely.by", javax.swing.JOptionPane.OK_CANCEL_OPTION,
                javax.swing.JOptionPane.PLAIN_MESSAGE);
        if (choice != javax.swing.JOptionPane.OK_OPTION) {
            password.setText("");
            return;
        }
        char[] secret = password.getPassword();
        password.setText("");
        authenticateElyBy(username.getText().trim(), secret, null);
    }

    private void authenticateElyBy(String username, char[] password, String twoFactorCode) {
        signingIn = true;
        accountButton.setEnabled(false);
        status.setText("Signing in to Ely.by...");
        new SwingWorker<ElyByAuthService.LoginResult, Void>() {
            @Override
            protected ElyByAuthService.LoginResult doInBackground() throws Exception {
                return elyByAuth.authenticate(username, password, twoFactorCode);
            }

            @Override
            protected void done() {
                try {
                    ElyByAuthService.LoginResult result = get();
                    Arrays.fill(password, '\0');
                    signingIn = false;
                    accountButton.setEnabled(true);
                    chooseElyProfile(result);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    Arrays.fill(password, '\0');
                    signingIn = false;
                    accountButton.setEnabled(true);
                    showError("Ely.by sign-in was interrupted.", exception);
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    if (cause instanceof ElyByAuthService.TwoFactorRequiredException
                            && twoFactorCode == null) {
                        String code = javax.swing.JOptionPane.showInputDialog(frame,
                                "Enter the two-factor authentication code from Ely.by.",
                                "Ely.by two-factor authentication",
                                javax.swing.JOptionPane.PLAIN_MESSAGE);
                        if (code != null && !code.isBlank()) {
                            authenticateElyBy(username, password, code.trim());
                            return;
                        }
                        Arrays.fill(password, '\0');
                        signingIn = false;
                        accountButton.setEnabled(true);
                        status.setText("Ely.by sign-in cancelled.");
                        return;
                    }
                    Arrays.fill(password, '\0');
                    signingIn = false;
                    accountButton.setEnabled(true);
                    showError("Ely.by sign-in failed.", cause);
                }
            }
        }.execute();
    }

    private void chooseElyProfile(ElyByAuthService.LoginResult result) {
        ElyByAuthService.Profile selected = result.selectedProfile();
        if (selected == null && result.profiles().size() == 1) {
            selected = result.profiles().get(0);
        } else if (selected == null) {
            JComboBox<ElyByAuthService.Profile> profiles =
                    new JComboBox<>(result.profiles().toArray(ElyByAuthService.Profile[]::new));
            int choice = javax.swing.JOptionPane.showConfirmDialog(frame, profiles,
                    "Choose Ely.by profile", javax.swing.JOptionPane.OK_CANCEL_OPTION,
                    javax.swing.JOptionPane.PLAIN_MESSAGE);
            if (choice != javax.swing.JOptionPane.OK_OPTION) {
                status.setText("Ely.by profile selection cancelled.");
                return;
            }
            selected = (ElyByAuthService.Profile) profiles.getSelectedItem();
        }
        if (selected != null) setAccount(result.account(selected));
    }

    private void showMicrosoftRequirement() {
        javax.swing.JOptionPane.showMessageDialog(frame,
                "Microsoft sign-in is an available account type, but cannot be activated until "
                        + "this launcher has an approved Microsoft application ID. Offline and Ely.by "
                        + "profiles are available without one.",
                "Microsoft sign-in setup required", javax.swing.JOptionPane.INFORMATION_MESSAGE);
    }

    private void setAccount(GameAccount account) {
        activeAccount = account;
        if (homeAccountStatus != null) {
            homeAccountStatus.setText("<html>" + escapeHtml(account.provider().label() + "  ·  "
                    + account.playerName()) + "</html>");
        }
        if (accountStatus != null) {
            accountStatus.setText("<html>" + escapeHtml(account.provider().label()) + "<br>"
                    + escapeHtml(account.playerName()) + "</html>");
        }
        accountButton.setText(account.provider().label() + "  ·  " + account.playerName());
        status.setText(account.provider().label() + " account selected.");
        updateLaunchEnabled();
    }

    private void updateLaunchEnabled() {
        Path instance = settings.gameDirectory().resolve("instances").resolve("DoorC-1.21.11");
        launchDoorC.setEnabled(!installing && !launching && activeAccount != null
                && Files.isDirectory(instance.resolve("versions")));
    }

    private void launchDoorCGame() {
        if (activeAccount == null) {
            chooseAccount();
            return;
        }
        if (installing || launching) return;
        GameAccount account = activeAccount;
        launching = true;
        updateLaunchEnabled();
        status.setText("Preparing DoorC for launch...");
        new SwingWorker<Process, Void>() {
            @Override
            protected Process doInBackground() throws Exception {
                return launchService.launchDoorC(settings.gameDirectory(), account);
            }

            @Override
            protected void done() {
                try {
                    get();
                    status.setText("DoorC started with " + account.provider().label()
                            + " account " + account.playerName() + ".");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError("DoorC launch was interrupted.", exception);
                } catch (ExecutionException exception) {
                    showError("Could not launch DoorC.", exception.getCause());
                } finally {
                    launching = false;
                    updateLaunchEnabled();
                }
            }
        }.execute();
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void showError(String message, Throwable error) {
        String details = error == null || error.getMessage() == null
                ? message : message + " " + error.getMessage();
        status.setText(details);
        javax.swing.JOptionPane.showMessageDialog(frame, details,
                "DoorC Launcher", javax.swing.JOptionPane.ERROR_MESSAGE);
    }

    private static String safeInstanceName(String value) {
        String safe = value == null ? "" : value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.isBlank() ? "Minecraft" : safe;
    }

    private static BufferedImage resourceImage(String path) {
        try (var input = LauncherWindow.class.getResourceAsStream(path)) {
            return input == null ? null : javax.imageio.ImageIO.read(input);
        } catch (IOException exception) {
            return null;
        }
    }

    private static ImageIcon logoIcon(int size) {
        if (LAUNCHER_LOGO == null) return null;
        Image scaled = LAUNCHER_LOGO.getScaledInstance(size, size, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    private static void setRound(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    @FunctionalInterface
    private interface InstallAction {
        void run(InstanceInstaller.ProgressListener progress) throws Exception;
    }

    private record InstallProgress(String message, int completed, int total) {}
    private record PointDrag(int x, int y) {}

    private static String navGlyph(String page) {
        return switch (page) {
            case "home" -> "⌂";
            case "versions" -> "▶";
            case "content" -> "✦";
            case "library" -> "▤";
            case "settings" -> "⚙";
            default -> "·";
        };
    }

    private static final class PlaceholderTextField extends JTextField {
        private final String placeholder;

        private PlaceholderTextField(String placeholder) {
            this.placeholder = placeholder;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (!getText().isEmpty()) return;
            Graphics2D g = (Graphics2D) graphics.create();
            g.setColor(MUTED);
            g.setFont(getFont());
            java.awt.FontMetrics metrics = g.getFontMetrics();
            int baseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            g.drawString(placeholder, getInsets().left, baseline);
            g.dispose();
        }
    }

    private final class NavButton extends JButton {
        private final String page;
        private boolean selected;
        private boolean hovered;

        private NavButton(String text, String page) {
            super(navGlyph(page) + "   " + text);
            this.page = page;
            this.selected = "home".equals(page);
            setForeground(MUTED);
            setFont(BODY_BOLD);
            setHorizontalAlignment(SwingConstants.LEFT);
            setBorder(BorderFactory.createEmptyBorder(11, 13, 11, 10));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addActionListener(event -> {
                for (Component component : getParent().getComponents()) {
                    if (component instanceof NavButton button) {
                        button.selected = button == this;
                        button.repaint();
                    }
                }
                LauncherWindow.this.showPage(this.page);
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hovered = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            setRound(g);
            if (selected || hovered) {
                g.setColor(selected ? new Color(83, 54, 118, 175) : new Color(39, 43, 62));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            }
            if (selected) {
                g.setColor(PURPLE_BRIGHT);
                g.fillRoundRect(0, 8, 3, getHeight() - 16, 3, 3);
            }
            g.dispose();
            setForeground(selected ? FG : hovered ? FG : MUTED);
            super.paintComponent(graphics);
        }
    }

    private final class RoundedPanel extends JPanel {
        private final Color color;
        private final int radius;

        private RoundedPanel(Color color, int radius) {
            this.color = color;
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            setRound(g);
            g.setColor(color);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g.setColor(new Color(82, 77, 108, 90));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private final class HeroPanel extends JPanel {
        private final BufferedImage image;

        private HeroPanel(BufferedImage image) {
            this.image = image;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            setRound(g);
            int width = getWidth();
            int height = getHeight();
            g.setColor(CARD);
            g.fillRoundRect(0, 0, width, height, 20, 20);
            java.awt.Shape previousClip = g.getClip();
            g.clip(new RoundRectangle2D.Float(0, 0, width, height, 20, 20));
            if (image != null) {
                double scale = Math.max((double) width / image.getWidth(),
                        (double) height / image.getHeight());
                int imageWidth = (int) Math.ceil(image.getWidth() * scale);
                int imageHeight = (int) Math.ceil(image.getHeight() * scale);
                int x = (width - imageWidth) / 2;
                int y = (height - imageHeight) / 2;
                g.drawImage(image, x, y, imageWidth, imageHeight, null);
            }
            g.setPaint(new GradientPaint(0, 0, new Color(14, 13, 27, 105),
                    width, height, new Color(24, 13, 45, 230)));
            g.fillRoundRect(0, 0, width, height, 20, 20);
            g.setClip(previousClip);
            g.setColor(new Color(127, 97, 174, 160));
            g.drawRoundRect(0, 0, width - 1, height - 1, 20, 20);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private final class ActionButton extends JButton {
        private final boolean primary;
        private boolean hovered;

        private ActionButton(String text, boolean primary) {
            super(text);
            this.primary = primary;
            setFont(BODY_BOLD);
            setForeground(FG);
            setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hovered = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            setRound(g);
            Color fill = primary ? (hovered ? PURPLE_BRIGHT : PURPLE)
                    : hovered ? new Color(57, 48, 77) : CARD_LIGHT;
            if (!isEnabled()) fill = new Color(70, 65, 82);
            g.setColor(fill);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 11, 11);
            g.setColor(primary ? new Color(224, 204, 255, 90) : new Color(107, 101, 130, 90));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 11, 11);
            g.dispose();
            setForeground(isEnabled() ? FG : MUTED);
            super.paintComponent(graphics);
        }
    }

    private static final class SimpleDocumentListener implements DocumentListener {
        private final Runnable action;

        private SimpleDocumentListener(Runnable action) {
            this.action = action;
        }

        @Override
        public void insertUpdate(DocumentEvent event) {
            action.run();
        }

        @Override
        public void removeUpdate(DocumentEvent event) {
            action.run();
        }

        @Override
        public void changedUpdate(DocumentEvent event) {
            action.run();
        }
    }
}
