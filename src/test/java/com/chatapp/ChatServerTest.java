package com.chatapp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.chatapp.client.ChatClient;
import com.chatapp.server.ChatServer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Integration tests: a real server on a random free port, with real clients. */
class ChatServerTest {

    private ChatServer server;
    private final List<TestClient> clients = new ArrayList<>();

    @BeforeEach
    void startServer() throws Exception {
        server = new ChatServer(0, null);
        server.start();
    }

    @AfterEach
    void stopServer() {
        for (TestClient c : clients) {
            c.client.disconnect();
        }
        server.stop();
    }

    private TestClient connect(String username) throws Exception {
        TestClient c = new TestClient();
        clients.add(c);
        c.client.connect("localhost", server.getPort(), username);
        return c;
    }

    /** Connects and waits until the user is logged in and inside #general. */
    private TestClient login(String username) throws Exception {
        TestClient c = connect(username);
        c.await("WELCOME " + username);
        c.await("JOINED general");
        return c;
    }

    @Test
    void userIsWelcomedAndJoinsGeneral() throws Exception {
        TestClient alice = login("alice");
        assertTrue(alice.seen.contains("ROOMS general,help,random"));
        alice.await("USERS alice");
    }

    @Test
    void duplicateUsernameIsRejectedIgnoringCase() throws Exception {
        login("alice");
        TestClient copy = connect("ALICE");
        copy.await(e -> e.startsWith("ERROR") && e.contains("already taken"));
    }

    @Test
    void invalidUsernameIsRejected() throws Exception {
        TestClient bad = connect("a!");
        bad.await(e -> e.startsWith("ERROR") && e.contains("Invalid username"));
    }

    @Test
    void messageIsDeliveredToEveryoneInTheRoom() throws Exception {
        TestClient alice = login("alice");
        TestClient bob = login("bob");

        alice.client.say("hello everyone");

        bob.await("MSG general alice hello everyone");
        alice.await("MSG general alice hello everyone");
    }

    @Test
    void messagesStayInsideTheirRoom() throws Exception {
        TestClient alice = login("alice");
        TestClient bob = login("bob");
        TestClient carol = login("carol");

        bob.client.join("random");
        bob.await("JOINED random");

        alice.client.say("general only");
        carol.await("MSG general alice general only"); // the message was delivered

        bob.client.say("marker");
        bob.await("MSG random bob marker");
        assertFalse(bob.seen.contains("MSG general alice general only"));
    }

    @Test
    void privateMessageReachesOnlyTheTarget() throws Exception {
        TestClient alice = login("alice");
        TestClient bob = login("bob");
        TestClient carol = login("carol");

        alice.client.privateMessage("bob", "secret");

        bob.await("PM alice bob secret");
        alice.await("PM alice bob secret");

        carol.client.say("marker");
        carol.await("MSG general carol marker");
        assertFalse(carol.seen.contains("PM alice bob secret"));
    }

    @Test
    void privateMessageToOfflineUserGivesError() throws Exception {
        TestClient alice = login("alice");
        alice.client.privateMessage("nobody", "hi");
        alice.await(e -> e.startsWith("ERROR") && e.contains("not online"));
    }

    @Test
    void emptyMessageGivesError() throws Exception {
        TestClient alice = login("alice");
        alice.client.say("   ");
        alice.await(e -> e.startsWith("ERROR"));
    }

    @Test
    void userListUpdatesWhenPeopleJoinAndLeave() throws Exception {
        TestClient alice = login("alice");
        alice.await("USERS alice");

        TestClient bob = login("bob");
        alice.await("USERS alice,bob");

        bob.client.disconnect();
        alice.await("INFO bob left the chat");
        alice.await("USERS alice");
    }

    @Test
    void newcomerSeesRecentHistory() throws Exception {
        TestClient alice = login("alice");
        alice.client.say("first message");
        alice.await("MSG general alice first message");

        TestClient bob = login("bob");
        bob.await("MSG general alice first message");
    }

    @Test
    void joiningANewRoomCreatesItForEveryone() throws Exception {
        TestClient alice = login("alice");
        TestClient bob = login("bob");

        alice.client.join("gaming");
        alice.await("JOINED gaming");
        bob.await("ROOMS gaming,general,help,random");
    }

    @Test
    void invalidRoomNameGivesError() throws Exception {
        TestClient alice = login("alice");
        alice.client.join("bad room!");
        alice.await(e -> e.startsWith("ERROR") && e.contains("room"));
    }

    // ---------- Test helper ----------

    /** A client that records every event as a simple string, so tests can wait for them. */
    static class TestClient implements ChatClient.Listener {
        final BlockingQueue<String> events = new LinkedBlockingQueue<>();
        final List<String> seen = new CopyOnWriteArrayList<>();
        final ChatClient client = new ChatClient(this);

        String await(String exact) throws InterruptedException {
            return await(e -> e.equals(exact));
        }

        /** Waits (max 3 seconds) for an event that matches. */
        String await(Predicate<String> match) throws InterruptedException {
            long deadline = System.currentTimeMillis() + 3000;
            while (true) {
                long left = deadline - System.currentTimeMillis();
                String event = left > 0 ? events.poll(left, TimeUnit.MILLISECONDS) : null;
                if (event == null) {
                    fail("Timed out. Events received: " + seen);
                }
                seen.add(event);
                if (match.test(event)) {
                    return event;
                }
            }
        }

        @Override
        public void onWelcome(String username) {
            events.add("WELCOME " + username);
        }

        @Override
        public void onJoined(String room) {
            events.add("JOINED " + room);
        }

        @Override
        public void onMessage(long time, String room, String from, String text) {
            events.add("MSG " + room + " " + from + " " + text);
        }

        @Override
        public void onPrivateMessage(long time, String from, String to, String text) {
            events.add("PM " + from + " " + to + " " + text);
        }

        @Override
        public void onInfo(String text) {
            events.add("INFO " + text);
        }

        @Override
        public void onError(String text) {
            events.add("ERROR " + text);
        }

        @Override
        public void onUsers(List<String> users) {
            events.add("USERS " + String.join(",", users));
        }

        @Override
        public void onRooms(List<String> rooms) {
            events.add("ROOMS " + String.join(",", rooms));
        }

        @Override
        public void onDisconnected() {
            events.add("DISCONNECTED");
        }
    }
}