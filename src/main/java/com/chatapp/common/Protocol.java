package com.chatapp.common;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The text protocol between client and server. Every message is ONE line:
 *
 * Client -> Server
 *   HELLO <username>          log in
 *   JOIN <room>               switch to a room (created if it doesn't exist)
 *   SAY <text>                send a message to the current room
 *   PM <user> <text>          private message
 *   QUIT                      leave
 *
 * Server -> Client
 *   WELCOME <username>                         login accepted
 *   JOINED <room>                              you are now in this room
 *   MSG <time> <room> <from> <text>            room message
 *   PRIVMSG <time> <from> <to> <text>          private message
 *   INFO <text>                                system notice
 *   ERROR <text>                               something went wrong
 *   USERS <user1,user2,...>                    everyone online
 *   ROOMS <room1,room2,...>                    all rooms
 */
public final class Protocol {

    public static final int DEFAULT_PORT = 5050;
    public static final String DEFAULT_ROOM = "general";
    public static final List<String> DEFAULT_ROOMS = List.of("general", "random", "help");
    public static final int MAX_MESSAGE_LENGTH = 500;
    public static final int HISTORY_SIZE = 50;

    // Client -> Server
    public static final String HELLO = "HELLO";
    public static final String JOIN = "JOIN";
    public static final String SAY = "SAY";
    public static final String PRIVATE = "PM";
    public static final String QUIT = "QUIT";

    // Server -> Client
    public static final String WELCOME = "WELCOME";
    public static final String JOINED = "JOINED";
    public static final String MESSAGE = "MSG";
    public static final String PRIVATE_DELIVERY = "PRIVMSG";
    public static final String INFO = "INFO";
    public static final String ERROR = "ERROR";
    public static final String USERS = "USERS";
    public static final String ROOMS = "ROOMS";

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern ROOM = Pattern.compile("[a-z0-9_-]{2,20}");

    private Protocol() {
    }

    // ---------- Validation ----------

    /** 3 to 16 characters: letters, digits, underscore. */
    public static boolean isValidUsername(String name) {
        return name != null && USERNAME.matcher(name).matches();
    }

    /** 2 to 20 characters: lowercase letters, digits, underscore, dash. */
    public static boolean isValidRoom(String room) {
        return room != null && ROOM.matcher(room).matches();
    }

    /** Removes line breaks (they would break the one-line protocol), trims, and limits length. */
    public static String cleanText(String text) {
        if (text == null) {
            return "";
        }
        String clean = text.replace('\r', ' ').replace('\n', ' ').strip();
        return clean.length() > MAX_MESSAGE_LENGTH ? clean.substring(0, MAX_MESSAGE_LENGTH) : clean;
    }

    // ---------- Parsing ----------

    /** The first word of a line, e.g. "SAY" for "SAY hello". */
    public static String command(String line) {
        int space = line.indexOf(' ');
        return space < 0 ? line : line.substring(0, space);
    }

    /** Everything after the first word, or "" if there is nothing. */
    public static String rest(String line) {
        int space = line.indexOf(' ');
        return space < 0 ? "" : line.substring(space + 1);
    }

    /**
     * Splits a line into the command and exactly {@code argCount} arguments.
     * The LAST argument keeps its spaces (it is usually the message text).
     * Returns null if an argument is missing or empty.
     */
    public static String[] parse(String line, int argCount) {
        if (line == null) {
            return null;
        }
        String[] parts = line.split(" ", argCount + 1);
        if (parts.length != argCount + 1) {
            return null;
        }
        for (String part : parts) {
            if (part.isEmpty()) {
                return null;
            }
        }
        return parts;
    }

    public static List<String> splitList(String text) {
        return text == null || text.isBlank() ? List.of() : List.of(text.split(","));
    }

    // ---------- Client -> Server ----------

    public static String hello(String username) {
        return HELLO + " " + username;
    }

    public static String join(String room) {
        return JOIN + " " + room;
    }

    public static String say(String text) {
        return SAY + " " + text;
    }

    public static String privateMessage(String to, String text) {
        return PRIVATE + " " + to + " " + text;
    }

    public static String quit() {
        return QUIT;
    }

    // ---------- Server -> Client ----------

    public static String welcome(String username) {
        return WELCOME + " " + username;
    }

    public static String joined(String room) {
        return JOINED + " " + room;
    }

    public static String message(long time, String room, String from, String text) {
        return MESSAGE + " " + time + " " + room + " " + from + " " + text;
    }

    public static String privateDelivery(long time, String from, String to, String text) {
        return PRIVATE_DELIVERY + " " + time + " " + from + " " + to + " " + text;
    }

    public static String info(String text) {
        return INFO + " " + text;
    }

    public static String error(String text) {
        return ERROR + " " + text;
    }

    public static String users(Collection<String> names) {
        return USERS + " " + String.join(",", names);
    }

    public static String rooms(Collection<String> names) {
        return ROOMS + " " + String.join(",", names);
    }
}