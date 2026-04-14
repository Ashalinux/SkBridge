package space.ashalinux.skbridge.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import space.ashalinux.skbridge.Main;
import space.ashalinux.skbridge.events.OnlineVoteEvent;

import javax.crypto.Cipher;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class VotifierManager implements Listener {

    private final Main plugin;
    private final Map<String, Integer> voteQueue = new HashMap<>();
    private PrivateKey privateKey;
    private ServerSocket serverSocket;
    private boolean running;

    public VotifierManager(Main plugin) {
        this.plugin = plugin;
        this.running = false;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void start() {
        if (running) return;
        running = true;

        initializeKeys();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int port = plugin.getConfig().getInt("port", 8192);
            try (ServerSocket ss = new ServerSocket(port)) {
                serverSocket = ss;
                plugin.getLogger().info("Votifier listener started on port: " + port);

                while (running) {
                    try (Socket socket = serverSocket.accept()) {
                        socket.setSoTimeout(5000);

                        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                        out.write("VOTIFIER 1.9\n");
                        out.flush();

                        InputStream in = socket.getInputStream();
                        byte[] data = new byte[256];
                        int read = in.read(data);

                        if (read > 0) {
                            String decrypted = decrypt(data);
                            if (decrypted != null) {
                                String[] parts = decrypted.split("\\r?\\n");
                                int userIndex = parts[0].trim().equalsIgnoreCase("VOTE") ? 2 : 1;
                                if (parts.length > userIndex) {
                                    handleVote(parts[userIndex].trim());
                                }
                            }
                        }
                    } catch (SocketTimeoutException ignored) {
                    } catch (Exception e) {
                        if (running) plugin.getLogger().warning("Error on incoming connection: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                if (running) plugin.getLogger().severe("Could not bind Votifier port " + port + "!");
            }
        });
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}
    }

    private void handleVote(String name) {
        if (name == null || !name.matches("^[a-zA-Z0-9_]{3,16}$")) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayerExact(name);
            if (p != null && p.isOnline()) {
                Bukkit.getPluginManager().callEvent(new OnlineVoteEvent(p));
            } else {
                if (voteQueue.size() > 5000) voteQueue.clear();
                int currentVotes = voteQueue.getOrDefault(name, 0);
                if (currentVotes < 50) {
                    voteQueue.put(name, currentVotes + 1);
                }
            }
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (voteQueue.containsKey(p.getName())) {
            int count = voteQueue.get(p.getName());
            for (int i = 0; i < count; i++) {
                Bukkit.getPluginManager().callEvent(new OnlineVoteEvent(p));
            }
            voteQueue.remove(p.getName());
        }
    }

    private void initializeKeys() {
        File rsaDir = new File(plugin.getDataFolder(), "rsa");
        if (!rsaDir.exists()) rsaDir.mkdirs();
        File privFile = new File(rsaDir, "private.key");

        try {
            if (!privFile.exists()) {
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(2048);
                KeyPair pair = keyGen.generateKeyPair();

                try (FileOutputStream out = new FileOutputStream(privFile)) {
                    out.write(pair.getPrivate().getEncoded());
                }

                String pubBase64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
                try (PrintWriter pw = new PrintWriter(new File(rsaDir, "public.key"))) {
                    pw.print(pubBase64);
                }
                privateKey = pair.getPrivate();
            } else {
                byte[] privBytes = java.nio.file.Files.readAllBytes(privFile.toPath());
                privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privBytes));
            }
        } catch (Exception e) {
            plugin.getLogger().severe("RSA initialization error: " + e.getMessage());
        }
    }

    private String decrypt(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return new String(cipher.doFinal(data));
        } catch (Exception e) {
            return null;
        }
    }
}