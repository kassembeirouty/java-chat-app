package com.chatapp.client;

import com.chatapp.common.Protocol;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Connects to a ChatServer. A background thread reads lines from the server
 * and turns them into calls on the Listener.
 */
public class ChatClient {

    public interface Listener {
        void onWelcome(String username);

        void onJoined(String room);

        void onMessage(long time, String room, String from, String text);

        void onPrivateMessage(long time, String from, String to, String text);

        void onInfo(String text);

        void onError(String text);

        void onUsers(List<String> users);

        void onRooms(List<String> rooms);

        void onDisconnected();
    }

    private final Listener listener;
    private Socket socket;
    private PrintWriter out;
    private volatile boolean connected;

    public ChatClient(Listener listener) {
        this.listener = listener;
    }

    public void connect(String host, int port, String username) throws IOException {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), 5000); // 5 second timeout
        socket = s;
        out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
        BufferedReader in = new BufferedReader(
                new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
        connected = true;

        Thread reader = new Thread(() -> readLoop(in), "chat-client-reader");
        reader.setDaemon(true);
        reader.start();

        send(Protocol.hello(username));
    }

    public void join(String room) {
        send(Protocol.join(room));
    }

    public void say(String text) {
        send(Protocol.say(Protocol.cleanText(text)));
    }

    public void privateMessage(String to, String text) {
        send(Protocol.privateMessage(to, Protocol.cleanText(text)));
    }

    public void disconnect() {
        if (!connected) {
            return;
        }
        send(Protocol.quit());
        try {
            socket.close();
        } catch (IOException ignored) {
            // already closed
        }
    }

    public boolean isConnected() {
        return connected;
    }

    private synchronized void send(String line) {
        if (out != null) {
            out.println(line);
        }
    }

    private void readLoop(BufferedReader in) {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                dispatch(line);
            }
        } catch (IOException e) {
            // connection closed
        } finally {
            connected = false;
            listener.onDisconnected();
        }
    }

    private void dispatch(String line) {
        try {
            switch (Protocol.command(line)) {
                case Protocol.WELCOME -> listener.onWelcome(Protocol.rest(line));
                case Protocol.JOINED -> listener.onJoined(Protocol.rest(line));
                case Protocol.MESSAGE -> {
                    String[] p = Protocol.parse(line, 4);
                    if (p != null) {
                        listener.onMessage(Long.parseLong(p[1]), p[2], p[3], p[4]);
                    }
                }
                case Protocol.PRIVATE_DELIVERY -> {
                    String[] p = Protocol.parse(line, 4);
                    if (p != null) {
                        listener.onPrivateMessage(Long.parseLong(p[1]), p[2], p[3], p[4]);
                    }
                }
                case Protocol.INFO -> listener.onInfo(Protocol.rest(line));
                case Protocol.ERROR -> listener.onError(Protocol.rest(line));
                case Protocol.USERS -> listener.onUsers(Protocol.splitList(Protocol.rest(line)));
                case Protocol.ROOMS -> listener.onRooms(Protocol.splitList(Protocol.rest(line)));
                default -> {
                    // unknown line: ignore
                }
            }
        } catch (NumberFormatException e) {
            // bad timestamp from server: ignore the line
        }
    }
}