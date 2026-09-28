package com.chatapp.server;

import com.chatapp.common.Protocol;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Runs on its own thread: reads lines from one client and passes them to the server. */
class ClientHandler implements Runnable {

    private final ChatServer server;
    private final Socket socket;
    private PrintWriter out;
    private volatile String username;
    private volatile ChatServer.Room room;

    ClientHandler(ChatServer server, Socket socket) {
        this.server = server;
        this.socket = socket;
    }

    @Override
    public void run() {
        try (Socket s = socket;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
            synchronized (this) {
                out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
            }
            String line;
            while ((line = in.readLine()) != null) {
                if (!handle(line)) {
                    break; // client sent QUIT
                }
            }
        } catch (IOException e) {
            // connection lost: handled in finally
        } finally {
            server.disconnect(this);
        }
    }

    /** Handles one line. Returns false when the client wants to quit. */
    private boolean handle(String line) {
        String command = Protocol.command(line);

        // Before login, only HELLO is allowed
        if (username == null) {
            if (!Protocol.HELLO.equals(command)) {
                send(Protocol.error("Please log in first with HELLO <username>"));
                return true;
            }
            String[] parts = Protocol.parse(line, 1);
            if (parts == null || !Protocol.isValidUsername(parts[1])) {
                send(Protocol.error("Invalid username. Use 3-16 letters, digits or _"));
            } else if (!server.register(this, parts[1])) {
                send(Protocol.error("Username " + parts[1] + " is already taken"));
            }
            return true;
        }

        switch (command) {
            case Protocol.JOIN -> {
                String[] parts = Protocol.parse(line, 1);
                String roomName = parts == null ? null : parts[1].toLowerCase(Locale.ROOT);
                if (!Protocol.isValidRoom(roomName)) {
                    send(Protocol.error("Invalid room name. Use 2-20 letters, digits, _ or -"));
                } else {
                    server.joinRoom(this, roomName);
                }
            }
            case Protocol.SAY -> {
                String[] parts = Protocol.parse(line, 1);
                String text = parts == null ? "" : Protocol.cleanText(parts[1]);
                if (text.isEmpty()) {
                    send(Protocol.error("Empty message"));
                } else {
                    server.say(this, text);
                }
            }
            case Protocol.PRIVATE -> {
                String[] parts = Protocol.parse(line, 2);
                String text = parts == null ? "" : Protocol.cleanText(parts[2]);
                if (text.isEmpty()) {
                    send(Protocol.error("Usage: PM <user> <message>"));
                } else {
                    server.privateMessage(this, parts[1], text);
                }
            }
            case Protocol.QUIT -> {
                return false;
            }
            default -> send(Protocol.error("Unknown command: " + command));
        }
        return true;
    }

    /** Sends one line to this client. Synchronized so two threads never mix their lines. */
    synchronized void send(String line) {
        if (out != null) {
            out.println(line);
        }
    }

    void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // already closed
        }
    }

    String getUsername() {
        return username;
    }

    void setUsername(String username) {
        this.username = username;
    }

    ChatServer.Room getRoom() {
        return room;
    }

    void setRoom(ChatServer.Room room) {
        this.room = room;
    }
}