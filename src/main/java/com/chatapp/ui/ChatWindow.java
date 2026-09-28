package com.chatapp.ui;

import com.chatapp.client.ChatClient;
import com.chatapp.common.Protocol;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/** The chat window of one user: rooms on the left, messages in the middle, users on the right. */
public class ChatWindow extends JFrame implements ChatClient.Listener {

    private static final Color[] NAME_COLORS = {
            new Color(0x89b4fa), new Color(0xa6e3a1), new Color(0xfab387), new Color(0xf5c2e7),
            new Color(0x94e2d5), new Color(0xf9e2af), new Color(0xcba6f7), new Color(0xeba0ac)
    };
    private static final Color TIME_COLOR = new Color(0x7f849c);
    private static final Color INFO_COLOR = new Color(0x9399b2);
    private static final Color ERROR_COLOR = new Color(0xf38ba8);
    private static final Color PRIVATE_COLOR = new Color(0xf5c2e7);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final ChatClient client = new ChatClient(this);
    private final String username;
    private final String serverAddress;
    private volatile boolean loggedIn;
    private volatile boolean closing;

    private final JLabel roomTitle = new JLabel("#general");
    private final JLabel statusLabel = new JLabel("Connecting...");
    private final JTextPane chatPane = new JTextPane();
    private final DefaultListModel<String> roomsModel = new DefaultListModel<>();
    private final JList<String> roomsList = new JList<>(roomsModel);
    private final DefaultListModel<String> usersModel = new DefaultListModel<>();
    private final JList<String> usersList = new JList<>(usersModel);
    private final JLabel usersTitle = new JLabel("Online");
    private final JTextField input = new JTextField();
    private final JButton sendButton = new JButton("Send");
    private final JLabel targetLabel = new JLabel();
    private final JButton cancelPrivateButton = new JButton("Back to room");

    private String currentRoom = Protocol.DEFAULT_ROOM;
    private String privateTarget; // null = talking in the room
    private boolean updatingRooms;

    /** Connects to the server and shows the window, or shows an error. */
    public static void open(Component parent, String host, int port, String username) {
        ChatWindow window = new ChatWindow(username, host + ":" + port);
        try {
            window.client.connect(host, port, username);
            window.setVisible(true);
        } catch (IOException e) {
            window.dispose();
            JOptionPane.showMessageDialog(parent,
                    "Could not connect to " + host + ":" + port + "\n\n" + e.getMessage()
                            + "\n\nIs the server running?",
                    "Connection failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private ChatWindow(String username, String serverAddress) {
        super("Chat - " + username);
        this.username = username;
        this.serverAddress = serverAddress;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                closing = true;
                client.disconnect();
            }
        });

        setLayout(new BorderLayout());
        add(buildHeader(), BorderLayout.NORTH);
        add(buildRoomsPanel(), BorderLayout.WEST);
        add(buildChatArea(), BorderLayout.CENTER);
        add(buildUsersPanel(), BorderLayout.EAST);

        setSize(980, 640);
        setLocationByPlatform(true);
        setInputEnabled(false);
        updateTarget();
    }

    // ---------- Layout ----------

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(10, 14, 10, 14));
        roomTitle.setFont(roomTitle.getFont().deriveFont(Font.BOLD, 18f));
        statusLabel.setForeground(INFO_COLOR);
        header.add(roomTitle, BorderLayout.WEST);
        header.add(statusLabel, BorderLayout.EAST);
        return header;
    }

    private JPanel buildRoomsPanel() {
        JLabel title = new JLabel("Rooms");
        title.setFont(title.getFont().deriveFont(Font.BOLD));

        roomsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roomsList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, "# " + value, index, isSelected, cellHasFocus);
                setBorder(new EmptyBorder(4, 8, 4, 8));
                return this;
            }
        });
        roomsList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || updatingRooms) {
                return;
            }
            String room = roomsList.getSelectedValue();
            if (room != null && !room.equals(currentRoom)) {
                client.join(room);
            }
        });

        JButton newRoomButton = new JButton("+ New Room");
        newRoomButton.addActionListener(e -> createRoom());

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(new EmptyBorder(0, 10, 10, 0));
        panel.setPreferredSize(new Dimension(180, 0));
        panel.add(title, BorderLayout.NORTH);
        panel.add(new JScrollPane(roomsList), BorderLayout.CENTER);
        panel.add(newRoomButton, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildChatArea() {
        chatPane.setEditable(false);
        chatPane.setBorder(new EmptyBorder(8, 10, 8, 10));
        chatPane.setFont(chatPane.getFont().deriveFont(14f));

        cancelPrivateButton.addActionListener(e -> {
            privateTarget = null;
            updateTarget();
        });
        JPanel targetPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        targetPanel.add(targetLabel);
        targetPanel.add(cancelPrivateButton);

        input.addActionListener(e -> send()); // Enter key
        sendButton.addActionListener(e -> send());

        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.setBorder(new EmptyBorder(8, 0, 0, 0));
        inputRow.add(targetPanel, BorderLayout.WEST);
        inputRow.add(input, BorderLayout.CENTER);
        inputRow.add(sendButton, BorderLayout.EAST);

        JPanel center = new JPanel(new BorderLayout());
        center.setBorder(new EmptyBorder(0, 10, 10, 10));
        center.add(new JScrollPane(chatPane), BorderLayout.CENTER);
        center.add(inputRow, BorderLayout.SOUTH);
        return center;
    }

    private JPanel buildUsersPanel() {
        usersTitle.setFont(usersTitle.getFont().deriveFont(Font.BOLD));

        usersList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                String name = String.valueOf(value);
                String text = name.equalsIgnoreCase(username) ? name + " (you)" : name;
                super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
                setBorder(new EmptyBorder(4, 8, 4, 8));
                if (!isSelected) {
                    setForeground(colorFor(name));
                }
                return this;
            }
        });
        usersList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String user = usersList.getSelectedValue();
                    if (user != null && !user.equalsIgnoreCase(username)) {
                        privateTarget = user;
                        updateTarget();
                        input.requestFocusInWindow();
                    }
                }
            }
        });

        JLabel hint = new JLabel("<html>Double-click a user to send a private message</html>");
        hint.setForeground(INFO_COLOR);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(new EmptyBorder(0, 0, 10, 10));
        panel.setPreferredSize(new Dimension(190, 0));
        panel.add(usersTitle, BorderLayout.NORTH);
        panel.add(new JScrollPane(usersList), BorderLayout.CENTER);
        panel.add(hint, BorderLayout.SOUTH);
        return panel;
    }

    // ---------- Actions ----------

    private void send() {
        String text = input.getText().strip();
        if (text.isEmpty() || !client.isConnected()) {
            return;
        }
        if (privateTarget != null) {
            client.privateMessage(privateTarget, text);
        } else {
            client.say(text);
        }
        input.setText("");
    }

    private void createRoom() {
        String name = JOptionPane.showInputDialog(this,
                "Room name (2-20 lowercase letters, digits, _ or -):", "New Room",
                JOptionPane.PLAIN_MESSAGE);
        if (name == null) {
            return;
        }
        name = name.strip().toLowerCase(Locale.ROOT);
        if (!Protocol.isValidRoom(name)) {
            JOptionPane.showMessageDialog(this, "Invalid room name.", "New Room",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        client.join(name);
    }

    private void updateTarget() {
        if (privateTarget == null) {
            targetLabel.setText("To #" + currentRoom);
            targetLabel.setForeground(UIManager.getColor("Label.foreground"));
            cancelPrivateButton.setVisible(false);
        } else {
            targetLabel.setText("Private to " + privateTarget);
            targetLabel.setForeground(PRIVATE_COLOR);
            cancelPrivateButton.setVisible(true);
        }
    }

    private void setInputEnabled(boolean enabled) {
        input.setEnabled(enabled);
        sendButton.setEnabled(enabled);
        roomsList.setEnabled(enabled);
    }

    // ---------- Writing to the chat ----------

    private void append(String text, Color color, boolean bold, boolean italic) {
        StyledDocument doc = chatPane.getStyledDocument();
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setForeground(attributes, color);
        StyleConstants.setBold(attributes, bold);
        StyleConstants.setItalic(attributes, italic);
        try {
            doc.insertString(doc.getLength(), text, attributes);
        } catch (BadLocationException ignored) {
            // cannot happen when inserting at the end
        }
        chatPane.setCaretPosition(doc.getLength());
    }

    private Color textColor() {
        Color color = UIManager.getColor("TextPane.foreground");
        return color != null ? color : Color.WHITE;
    }

    private static Color colorFor(String name) {
        int index = Math.floorMod(name.toLowerCase(Locale.ROOT).hashCode(), NAME_COLORS.length);
        return NAME_COLORS[index];
    }

    private static String time(long millis) {
        return TIME.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }

    // ---------- Server events (they arrive on the network thread) ----------

    @Override
    public void onWelcome(String name) {
        SwingUtilities.invokeLater(() -> {
            loggedIn = true;
            statusLabel.setText("Connected as " + name + " to " + serverAddress);
            setInputEnabled(true);
            input.requestFocusInWindow();
        });
    }

    @Override
    public void onJoined(String room) {
        SwingUtilities.invokeLater(() -> {
            currentRoom = room;
            roomTitle.setText("#" + room);
            chatPane.setText("");
            append("You joined #" + room + "\n", INFO_COLOR, false, true);
            updatingRooms = true;
            roomsList.setSelectedValue(room, true);
            updatingRooms = false;
            updateTarget();
        });
    }

    @Override
    public void onMessage(long millis, String room, String from, String text) {
        SwingUtilities.invokeLater(() -> {
            if (!room.equals(currentRoom)) {
                return;
            }
            append("[" + time(millis) + "] ", TIME_COLOR, false, false);
            append(from + ": ", colorFor(from), true, false);
            append(text + "\n", textColor(), false, false);
        });
    }

    @Override
    public void onPrivateMessage(long millis, String from, String to, String text) {
        SwingUtilities.invokeLater(() -> {
            boolean outgoing = from.equalsIgnoreCase(username);
            String label = outgoing ? "(private to " + to + ") " : "(private from " + from + ") ";
            append("[" + time(millis) + "] ", TIME_COLOR, false, false);
            append(label, PRIVATE_COLOR, true, false);
            append(text + "\n", PRIVATE_COLOR, false, false);
        });
    }

    @Override
    public void onInfo(String text) {
        SwingUtilities.invokeLater(() -> append("• " + text + "\n", INFO_COLOR, false, true));
    }

    @Override
    public void onError(String text) {
        SwingUtilities.invokeLater(() -> {
            if (!loggedIn) {
                // Login refused (username taken or invalid): close this window
                JOptionPane.showMessageDialog(this, text, "Could not join", JOptionPane.ERROR_MESSAGE);
                closing = true;
                dispose();
            } else {
                append("Error: " + text + "\n", ERROR_COLOR, true, false);
            }
        });
    }

    @Override
    public void onUsers(List<String> users) {
        SwingUtilities.invokeLater(() -> {
            usersModel.clear();
            usersModel.addAll(users);
            usersTitle.setText("Online (" + users.size() + ")");
            if (privateTarget != null && !users.contains(privateTarget)) {
                append("• " + privateTarget + " went offline\n", INFO_COLOR, false, true);
                privateTarget = null;
                updateTarget();
            }
        });
    }

    @Override
    public void onRooms(List<String> rooms) {
        SwingUtilities.invokeLater(() -> {
            updatingRooms = true;
            roomsModel.clear();
            roomsModel.addAll(rooms);
            roomsList.setSelectedValue(currentRoom, false);
            updatingRooms = false;
        });
    }

    @Override
    public void onDisconnected() {
        SwingUtilities.invokeLater(() -> {
            if (closing) {
                return;
            }
            setInputEnabled(false);
            statusLabel.setText("Disconnected");
            append("Disconnected from the server.\n", ERROR_COLOR, true, false);
        });
    }
}