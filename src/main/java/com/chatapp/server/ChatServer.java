package com.chatapp.server;

import com.chatapp.common.Protocol;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Multi-client chat server. One thread accepts connections, and every client
 * gets its own thread (a ClientHandler) from a thread pool.
 */
public class ChatServer {

    /** Lets a window show what the server is doing. */
    public interface Listener {
        void onLog(String message);

        void onUsersChanged(List<String> users);
    }

    private static final Listener NO_OP = new Listener() {
        @Override
        public void onLog(String message) {
        }

        @Override
        public void onUsersChanged(List<String> users) {
        }
    };

    private final int requestedPort;
    private final Listener listener;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final Set<ClientHandler> connections = ConcurrentHashMap.newKeySet();
    private final Map<String, ClientHandler> users = new ConcurrentHashMap<>(); // lowercase name -> client
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private ServerSocket serverSocket;
    private volatile boolean running;

    /** Use port 0 to let the system pick a free port (useful for tests). */
    public ChatServer(int port, Listener listener) {
        this.requestedPort = port;
        this.listener = listener == null ? NO_OP : listener;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(requestedPort);
        running = true;
        for (String name : Protocol.DEFAULT_ROOMS) {
            rooms.put(name, new Room(name));
        }
        Thread acceptThread = new Thread(this::acceptLoop, "chat-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        log("Server started on port " + getPort() + ".");
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    public boolean isRunning() {
        return running;
    }

    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // already closed
        }
        for (ClientHandler client : connections) {
            client.close();
        }
        pool.shutdownNow();
        log("Server stopped.");
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(this, socket);
                connections.add(handler);
                pool.execute(handler);
                log("New connection from " + socket.getInetAddress().getHostAddress() + ".");
            } catch (IOException | RejectedExecutionException e) {
                if (running) {
                    log("Error accepting a connection: " + e.getMessage());
                }
            }
        }
    }

    // ---------- Called by ClientHandler (synchronized = one change at a time) ----------

    synchronized boolean register(ClientHandler handler, String username) {
        String key = username.toLowerCase(Locale.ROOT);
        if (users.containsKey(key)) {
            return false;
        }
        users.put(key, handler);
        handler.setUsername(username);
        handler.send(Protocol.welcome(username));
        handler.send(Protocol.rooms(roomNames()));
        log(username + " logged in.");
        joinRoom(handler, Protocol.DEFAULT_ROOM);
        broadcastUsers();
        return true;
    }

    synchronized void joinRoom(ClientHandler handler, String roomName) {
        Room current = handler.getRoom();
        if (current != null && current.name.equals(roomName)) {
            handler.send(Protocol.joined(roomName));
            return;
        }
        if (current != null) {
            current.members.remove(handler);
            broadcastToRoom(current, Protocol.info(handler.getUsername() + " left #" + current.name));
        }

        boolean isNew = !rooms.containsKey(roomName);
        Room room = rooms.computeIfAbsent(roomName, Room::new);
        room.members.add(handler);
        handler.setRoom(room);

        handler.send(Protocol.joined(roomName));
        for (String line : room.historySnapshot()) {
            handler.send(line); // show recent messages to the newcomer
        }
        broadcastToRoom(room, Protocol.info(handler.getUsername() + " joined #" + roomName));

        if (isNew) {
            log(handler.getUsername() + " created room #" + roomName + ".");
            broadcastAll(Protocol.rooms(roomNames()));
        }
    }

    synchronized void say(ClientHandler handler, String text) {
        Room room = handler.getRoom();
        if (room == null) {
            return;
        }
        String line = Protocol.message(System.currentTimeMillis(), room.name, handler.getUsername(), text);
        room.addToHistory(line);
        broadcastToRoom(room, line);
        log("[#" + room.name + "] " + handler.getUsername() + ": " + text);
    }

    synchronized void privateMessage(ClientHandler from, String toName, String text) {
        ClientHandler to = users.get(toName.toLowerCase(Locale.ROOT));
        if (to == null) {
            from.send(Protocol.error("User " + toName + " is not online"));
            return;
        }
        if (to == from) {
            from.send(Protocol.error("You cannot send a private message to yourself"));
            return;
        }
        String line = Protocol.privateDelivery(
                System.currentTimeMillis(), from.getUsername(), to.getUsername(), text);
        to.send(line);
        from.send(line); // so the sender sees it in their window too
        log("[private] " + from.getUsername() + " -> " + to.getUsername());
    }

    synchronized void disconnect(ClientHandler handler) {
        connections.remove(handler);
        String name = handler.getUsername();
        if (name == null) {
            return; // never logged in
        }
        users.remove(name.toLowerCase(Locale.ROOT), handler);
        Room room = handler.getRoom();
        if (room != null) {
            room.members.remove(handler);
            broadcastToRoom(room, Protocol.info(name + " left the chat"));
        }
        log(name + " disconnected.");
        broadcastUsers();
    }

    // ---------- Helpers ----------

    private List<String> onlineUsers() {
        return users.values().stream()
                .map(ClientHandler::getUsername)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private List<String> roomNames() {
        return rooms.keySet().stream().sorted().toList();
    }

    private void broadcastUsers() {
        List<String> online = onlineUsers();
        broadcastAll(Protocol.users(online));
        listener.onUsersChanged(online);
    }

    private void broadcastToRoom(Room room, String line) {
        for (ClientHandler member : room.members) {
            member.send(line);
        }
    }

    private void broadcastAll(String line) {
        for (ClientHandler client : users.values()) {
            client.send(line);
        }
    }

    private void log(String message) {
        listener.onLog(message);
    }

    /** A chat room: its members and its last messages. */
    static final class Room {
        final String name;
        final Set<ClientHandler> members = ConcurrentHashMap.newKeySet();
        private final Deque<String> history = new ArrayDeque<>();

        Room(String name) {
            this.name = name;
        }

        synchronized void addToHistory(String line) {
            history.addLast(line);
            if (history.size() > Protocol.HISTORY_SIZE) {
                history.removeFirst();
            }
        }

        synchronized List<String> historySnapshot() {
            return new ArrayList<>(history);
        }
    }
}