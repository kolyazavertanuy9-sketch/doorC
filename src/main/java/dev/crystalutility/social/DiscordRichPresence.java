package dev.crystalutility.social;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import dev.crystalutility.config.ModConfig;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

public final class DiscordRichPresence {
    private static final String APPLICATION_ID = "1560347976197603348";
    private static final int HANDSHAKE = 0;
    private static final int FRAME = 1;
    private static volatile boolean running;
    private static volatile RpcConnection activeConnection;
    private static volatile Activity latestActivity;
    private static Thread worker;

    private DiscordRichPresence() {}

    public static synchronized void start() {
        if (running) return;
        running = true;
        worker = new Thread(DiscordRichPresence::run, "wvDoorClient-DiscordRPC");
        worker.setDaemon(true);
        worker.start();
    }

    public static void tick(Minecraft client) {
        String details = client.level == null ? "In the main menu" : "Playing Minecraft";
        String state = client.level == null ? "PvP Utility Client"
                : client.getCurrentServer() == null ? "Singleplayer" : "Multiplayer";
        latestActivity = new Activity(details, state);
    }

    public static synchronized void stop() {
        running = false;
        RpcConnection connection = activeConnection;
        if (connection != null) {
            try {
                connection.close();
            } catch (IOException exception) {
                System.err.println("[wvDoorClient] Could not close Discord IPC: " + exception.getMessage());
            }
        }
        if (worker != null) worker.interrupt();
        worker = null;
    }

    private static void run() {
        long lastWarning = 0;
        while (running) {
            if (!ModConfig.get().discordRichPresenceEnabled) {
                sleep(500);
                continue;
            }

            try (RpcConnection connection = connect()) {
                activeConnection = connection;
                handshake(connection);
                Activity sent = null;
                while (running && ModConfig.get().discordRichPresenceEnabled) {
                    Activity current = latestActivity;
                    if (current != null && !current.equals(sent)) {
                        sendActivity(connection, current);
                        Packet response = readPacket(connection);
                        if (response.opcode() == 2) {
                            throw new IOException("Discord rejected the Rich Presence update.");
                        }
                        sent = current;
                    }
                    sleep(500);
                }
            } catch (IOException | RuntimeException exception) {
                long now = System.currentTimeMillis();
                if (running && ModConfig.get().discordRichPresenceEnabled && now - lastWarning > 30_000) {
                    System.err.println("[wvDoorClient] Discord Rich Presence is waiting for Discord: "
                            + exception.getMessage());
                    lastWarning = now;
                }
            } finally {
                activeConnection = null;
            }
            sleep(2_000);
        }
    }

    private static void handshake(RpcConnection connection) throws IOException {
        JsonObject request = new JsonObject();
        request.addProperty("v", 1);
        request.addProperty("client_id", APPLICATION_ID);
        writePacket(connection, HANDSHAKE, request.toString());
        Packet response = readPacket(connection);
        JsonObject json = parse(response.payload());
        if (response.opcode() != FRAME || !json.has("evt")
                || !"READY".equals(json.get("evt").getAsString())) {
            throw new IOException("Discord IPC handshake did not return READY.");
        }
    }

    private static void sendActivity(RpcConnection connection, Activity activity) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("type", 0);
        body.addProperty("details", activity.details());
        body.addProperty("state", activity.state());
        body.addProperty("instance", true);

        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessHandle.current().pid());
        args.add("activity", body);

        JsonObject request = new JsonObject();
        request.addProperty("cmd", "SET_ACTIVITY");
        request.add("args", args);
        request.addProperty("nonce", UUID.randomUUID().toString());
        writePacket(connection, FRAME, request.toString());
    }

    private static RpcConnection connect() throws IOException {
        if (isWindows()) return connectWindowsPipe();
        IOException failure = new IOException("No Discord IPC socket was found.");
        for (Path path : socketCandidates()) {
            if (!Files.exists(path)) continue;
            SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
            try {
                channel.connect(UnixDomainSocketAddress.of(path));
                return new SocketRpcConnection(channel);
            } catch (IOException exception) {
                channel.close();
                failure.addSuppressed(exception);
            }
        }
        throw failure;
    }

    private static RpcConnection connectWindowsPipe() throws IOException {
        for (int index = 0; index < 10; index++) {
            String pipe = "\\\\.\\pipe\\discord-ipc-" + index;
            WinNT.HANDLE handle = Kernel32.INSTANCE.CreateFile(pipe,
                    WinNT.GENERIC_READ | WinNT.GENERIC_WRITE,
                    0, null, 3, 0, null);
            if (handle != null && Pointer.nativeValue(handle.getPointer()) != -1) {
                return new WindowsPipeRpcConnection(handle);
            }
        }
        throw new IOException("Discord is not exposing a local IPC pipe.");
    }

    private static List<Path> socketCandidates() {
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        addSocketCandidate(candidates, System.getenv("XDG_RUNTIME_DIR"));
        addSocketCandidate(candidates, System.getenv("TMPDIR"));
        addSocketCandidate(candidates, System.getProperty("java.io.tmpdir"));
        addSocketCandidate(candidates, "/tmp");
        String home = System.getProperty("user.home");
        if (home != null) {
            candidates.add(Path.of(home, ".config", "discord", "discord-ipc-0"));
            candidates.add(Path.of(home, ".config", "discordcanary", "discord-ipc-0"));
            candidates.add(Path.of(home, ".config", "discordptb", "discord-ipc-0"));
        }
        return new ArrayList<>(candidates);
    }

    private static void addSocketCandidate(LinkedHashSet<Path> candidates, String directory) {
        if (directory != null && !directory.isBlank()) {
            candidates.add(Path.of(directory).resolve("discord-ipc-0"));
        }
    }

    private static void writePacket(RpcConnection connection, int opcode, String payload) throws IOException {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(opcode).putInt(bytes.length);
        connection.output().write(header.array());
        connection.output().write(bytes);
        connection.output().flush();
    }

    private static Packet readPacket(RpcConnection connection) throws IOException {
        byte[] header = readExactly(connection.input(), 8);
        ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int opcode = buffer.getInt();
        int length = buffer.getInt();
        if (length < 0 || length > 1_048_576) throw new IOException("Invalid Discord IPC packet size.");
        return new Packet(opcode, new String(readExactly(connection.input(), length), StandardCharsets.UTF_8));
    }

    private static byte[] readExactly(InputStream input, int length) throws IOException {
        byte[] bytes = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(bytes, offset, length - offset);
            if (read < 0) throw new IOException("Discord IPC closed the connection.");
            offset += read;
        }
        return bytes;
    }

    private static JsonObject parse(String payload) throws IOException {
        try {
            return JsonParser.parseString(payload).getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IOException("Discord IPC returned invalid JSON.", exception);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private record Activity(String details, String state) {}

    private record Packet(int opcode, String payload) {}

    private interface RpcConnection extends AutoCloseable {
        InputStream input();

        OutputStream output();

        @Override
        void close() throws IOException;
    }

    private record SocketRpcConnection(SocketChannel channel) implements RpcConnection {
        @Override
        public InputStream input() {
            return Channels.newInputStream(channel);
        }

        @Override
        public OutputStream output() {
            return Channels.newOutputStream(channel);
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }
    }

    private static final class WindowsPipeRpcConnection implements RpcConnection {
        private final WinNT.HANDLE handle;
        private final InputStream input = new InputStream() {
            @Override
            public int read() throws IOException {
                byte[] one = new byte[1];
                return read(one, 0, 1) < 0 ? -1 : Byte.toUnsignedInt(one[0]);
            }

            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException {
                if (length == 0) return 0;
                byte[] buffer = new byte[length];
                IntByReference count = new IntByReference();
                if (!Kernel32.INSTANCE.ReadFile(handle, buffer, length, count, null)) {
                    throw pipeFailure("read");
                }
                int read = count.getValue();
                if (read == 0) return -1;
                System.arraycopy(buffer, 0, bytes, offset, read);
                return read;
            }
        };
        private final OutputStream output = new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                write(new byte[]{(byte) value});
            }

            @Override
            public void write(byte[] bytes, int offset, int length) throws IOException {
                while (length > 0) {
                    byte[] buffer = java.util.Arrays.copyOfRange(bytes, offset, offset + length);
                    IntByReference count = new IntByReference();
                    if (!Kernel32.INSTANCE.WriteFile(handle, buffer, length, count, null)) {
                        throw pipeFailure("write");
                    }
                    int written = count.getValue();
                    if (written <= 0) throw new IOException("Discord IPC pipe accepted no data.");
                    offset += written;
                    length -= written;
                }
            }
        };

        private WindowsPipeRpcConnection(WinNT.HANDLE handle) {
            this.handle = handle;
        }

        @Override
        public InputStream input() {
            return input;
        }

        @Override
        public OutputStream output() {
            return output;
        }

        @Override
        public void close() {
            Kernel32.INSTANCE.CloseHandle(handle);
        }

        private static IOException pipeFailure(String operation) {
            return new IOException("Could not " + operation + " Discord IPC pipe (Win32 error "
                    + Native.getLastError() + ").");
        }
    }
}
