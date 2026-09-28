package com.chatapp.ui;

import com.chatapp.common.NetworkInfo;
import com.chatapp.server.ChatServer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/** Shows the running server: live log and the list of connected users. */
public class ServerWindow extends JFrame implements ChatServer.Listener {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ChatServer server;
    private final JTextArea log = new JTextArea();
    private final DefaultListModel<String> usersModel = new DefaultListModel<>();
    private final JLabel status = new JLabel();
    private final JButton stopButton = new JButton("Stop Server");

    /** Starts a server on the port and shows its window, or shows an error. */
    public static void open(Component parent, int port) {
        ServerWindow window = new ServerWindow(port);
        try {
            window.server.start();
            window.updateStatus(0);
            window.setVisible(true);
        } catch (IOException e) {
            window.dispose();
            JOptionPane.showMessageDialog(parent,
                    "Could not start the server on port " + port + ".\n"
                            + "Is another server already running on this port?\n\n" + e.getMessage(),
                    "Server error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private ServerWindow(int port) {
        super("Chat Server - port " + port);
        server = new ChatServer(port, this);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                server.stop();
            }
        });

        // Header: status + how others can connect
        JPanel header = new JPanel(new GridLayout(2, 1, 0, 4));
        header.setBorder(new EmptyBorder(12, 14, 8, 14));
        status.setFont(status.getFont().deriveFont(Font.BOLD, 15f));
        List<String> ips = NetworkInfo.localIpAddresses();
        JLabel connectInfo = new JLabel(ips.isEmpty()
                ? "Connect from this computer with address: localhost"
                : "Connect with: localhost (this computer) or " + String.join(" / ", ips)
                        + " (other computers on the same Wi-Fi), port " + port);
        header.add(status);
        header.add(connectInfo);

        // Center: log on the left, users on the right
        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);
        JScrollPane logScroll = new JScrollPane(log);
        logScroll.setBorder(BorderFactory.createTitledBorder("Server log"));

        JScrollPane usersScroll = new JScrollPane(new JList<>(usersModel));
        usersScroll.setBorder(BorderFactory.createTitledBorder("Online users"));
        usersScroll.setPreferredSize(new Dimension(200, 0));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, logScroll, usersScroll);
        split.setResizeWeight(1.0);
        split.setBorder(new EmptyBorder(0, 10, 0, 10));

        // Bottom: stop button
        stopButton.addActionListener(e -> {
            server.stop();
            stopButton.setEnabled(false);
            updateStatus(0);
        });
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(new EmptyBorder(8, 10, 10, 10));
        bottom.add(stopButton, BorderLayout.EAST);

        setLayout(new BorderLayout());
        add(header, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        setSize(820, 500);
        setLocationByPlatform(true);
    }

    private void updateStatus(int usersOnline) {
        status.setText(server.isRunning()
                ? "Running on port " + server.getPort() + "   ·   " + usersOnline + " user(s) online"
                : "Server stopped");
    }

    // Called from server threads, so we switch to the Swing thread with invokeLater

    @Override
    public void onLog(String message) {
        SwingUtilities.invokeLater(() -> {
            log.append("[" + LocalTime.now().format(TIME) + "] " + message + "\n");
            log.setCaretPosition(log.getDocument().getLength());
        });
    }

    @Override
    public void onUsersChanged(List<String> users) {
        SwingUtilities.invokeLater(() -> {
            usersModel.clear();
            usersModel.addAll(users);
            updateStatus(users.size());
        });
    }
}