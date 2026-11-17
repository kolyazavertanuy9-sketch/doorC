package dev.crystalutility.social;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FriendManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type FRIEND_LIST = new TypeToken<List<String>>() {}.getType();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("wvdoorclient-friends.json");
    private static final List<String> FRIENDS = new ArrayList<>();
    private static boolean loaded;

    private FriendManager() {}

    public static List<String> all() {
        load();
        return List.copyOf(FRIENDS);
    }

    public static boolean add(String name) {
        load();
        String normalized = normalize(name);
        if (!valid(normalized) || contains(normalized)) return false;
        FRIENDS.add(normalized);
        save();
        return true;
    }

    public static void remove(String name) {
        load();
        String normalized = normalize(name);
        if (FRIENDS.removeIf(friend -> friend.equalsIgnoreCase(normalized))) save();
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(FILE)) return;
        try {
            List<String> saved = GSON.fromJson(Files.readString(FILE), FRIEND_LIST);
            if (saved == null) return;
            for (String friend : saved) {
                String normalized = normalize(friend);
                if (valid(normalized) && !contains(normalized)) FRIENDS.add(normalized);
            }
        } catch (IOException | RuntimeException exception) {
            System.err.println("[wvDoorClient] Could not read friend list " + FILE + ": " + exception.getMessage());
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(FRIENDS));
        } catch (IOException exception) {
            System.err.println("[wvDoorClient] Could not save friend list " + FILE + ": " + exception.getMessage());
        }
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim();
    }

    private static boolean valid(String name) {
        return name.matches("[A-Za-z0-9_]{1,16}");
    }

    private static boolean contains(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        return FRIENDS.stream().anyMatch(friend -> friend.toLowerCase(Locale.ROOT).equals(key));
    }
}
