package com.chatapp.ui;

import com.chatapp.common.NetworkInfo;
import com.chatapp.common.Protocol;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Random;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

/** Start screen: host a server and/or open chat windows. */
public class LauncherWindow extends JFrame {

    private final JTextField serverPortField = new JTextField(String.valueOf(Protocol.DEFAULT_PORT), 8);
    private final JTextField hostField = new JTextField("localhost", 14);
    private final JTextField joinPortField = new JTextField(String.valueOf(Protocol.DEFAULT_PORT), 8);
    private final JTextField usernameField = new JTextField(randomUsername(), 14);

    public LauncherWindow() {
        super("Java Chat");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JLabel title = new JLabel("Java Chat");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 26f));
        JLabel subtitle = new JLabel("Start a server, then open as many chat windows as you want "
                + "(each one is a different user).");

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(title);
        top.add(subtitle);

        JPanel cards = new JPanel(new GridLayout(1, 2, 16, 0));
        cards.add(buildServerCard());
        cards.add(buildJoinCard());

        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setBorder(new EmptyBorder(20, 22, 22, 22));
        content.add(top, BorderLayout.NORTH);
        content.add(cards, BorderLayout.CENTER);

        setContentPane(content);
        pack();
        setLocationRelativeTo(null);
    }

    private JPanel buildServerCard() {
        JPanel card = card("1. Host a chat server");
        addRow(card, 0, "Port", serverPortField);

        List<String> ips = NetworkInfo.localIpAddresses();
        JLabel ipLabel = new JLabel(ips.isEmpty()
                ? "<html>No network found.<br>You can still chat using localhost.</html>"
                : "<html>This computer's IP: <b>" + String.join(", ", ips) + "</b><br>"
                        + "Friends on the same Wi-Fi use this address to join.</html>");
        addFull(card, 1, ipLabel);

        JButton startButton = new JButton("Start Server");
        startButton.addActionListener(e -> startServer());
        addFull(card, 2, startButton);
        return card;
    }

    private JPanel buildJoinCard() {
        JPanel card = card("2. Join a chat");
        addRow(card, 0, "Server address", hostField);
        addRow(card, 1, "Port", joinPortField);
        addRow(card, 2, "Username", usernameField);
        usernameField.addActionListener(e -> joinChat()); // Enter key

        JButton joinButton = new JButton("Join Chat");
        joinButton.addActionListener(e -> joinChat());
        addFull(card, 3, joinButton);
        return card;
    }

    private void startServer() {
        int port = parsePort(serverPortField.getText());
        if (port < 0) {
            error("The port must be a number between 1 and 65535.");
            return;
        }
        joinPortField.setText(String.valueOf(port));
        ServerWindow.open(this, port);
    }

    private void joinChat() {
        String host = hostField.getText().strip();
        int port = parsePort(joinPortField.getText());
        String username = usernameField.getText().strip();

        if (host.isEmpty()) {
            error("Please enter the server address (for example: localhost).");
        } else if (port < 0) {
            error("The port must be a number between 1 and 65535.");
        } else if (!Protocol.isValidUsername(username)) {
            error("The username must be 3-16 characters: letters, digits or _");
        } else {
            ChatWindow.open(this, host, port, username);
            usernameField.setText(randomUsername()); // ready for the next window
        }
    }

    // ---------- Helpers ----------

    private static int parsePort(String text) {
        try {
            int port = Integer.parseInt(text.strip());
            return port >= 1 && port <= 65535 ? port : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String randomUsername() {
        return "user" + (100 + new Random().nextInt(900));
    }

    private void error(String message) {
        JOptionPane.showMessageDialog(this, message, "Java Chat", JOptionPane.WARNING_MESSAGE);
    }

    private static JPanel card(String title) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title), new EmptyBorder(8, 10, 10, 10)));
        return panel;
    }

    private static void addRow(JPanel panel, int row, String label, Component field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(6, 0, 6, 10);
        panel.add(new JLabel(label), c);

        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = row;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(6, 0, 6, 0);
        panel.add(field, c);
    }

    private static void addFull(JPanel panel, int row, Component component) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(10, 0, 0, 0);
        panel.add(component, c);
    }
}