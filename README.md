# Java Chat App

![Build](https://github.com/kassembeirouty/java-chat-app/actions/workflows/build.yml/badge.svg)

A real-time, multi-user chat application built from scratch with **Java 21**, **TCP sockets**, and **Swing**. It includes a multithreaded server, a desktop chat client, chat rooms, private messages, and a custom text protocol, all covered by integration tests that run a real server with real clients.



## Features

**Server**
- Accepts many clients at the same time: **one thread per client** from a thread pool
- Thread-safe state with `ConcurrentHashMap` and synchronized operations
- Chat rooms (#general, #random, #help), and users can create new ones
- Keeps the **last 50 messages** of each room and sends them to newcomers
- Unique usernames (case-insensitive), validated on the server
- Server window with a live log, online users, and the IP address others can connect to

**Client**
- Rooms list: click to switch, **+ New Room** to create one
- Online users list, updated live
- **Private messages**: double-click a user
- Timestamps and a different color for each username
- Clear errors for a taken username, an unreachable server, or a lost connection

**Networking**
- Works on one computer (`localhost`) or between computers on the same Wi-Fi
- A custom line-based protocol over TCP (see below)

## How It Works

```
 Chat window ──┐                         ┌── ClientHandler thread ──┐
 Chat window ──┼── TCP (port 5050) ──►  ChatServer ── ClientHandler thread ──┼── rooms, users, history
 Chat window ──┘                         └── ClientHandler thread ──┘
```

- The server's accept thread waits for connections and gives each one its own `ClientHandler` running in a thread pool.
- Each client has a background reader thread, so the window never freezes while waiting for messages. Network events are passed to the Swing thread with `SwingUtilities.invokeLater`.
- All messages are UTF-8 text, one line per message.

### Protocol

| Direction | Message | Meaning |
|-----------|---------|---------|
| Client → Server | `HELLO <username>` | Log in |
| Client → Server | `JOIN <room>` | Switch room (created if new) |
| Client → Server | `SAY <text>` | Message to the current room |
| Client → Server | `PM <user> <text>` | Private message |
| Client → Server | `QUIT` | Leave |
| Server → Client | `WELCOME <username>` | Login accepted |
| Server → Client | `JOINED <room>` | You are now in this room |
| Server → Client | `MSG <time> <room> <from> <text>` | Room message |
| Server → Client | `PRIVMSG <time> <from> <to> <text>` | Private message |
| Server → Client | `USERS <a,b,c>` | Everyone online |
| Server → Client | `ROOMS <a,b,c>` | All rooms |
| Server → Client | `INFO <text>` / `ERROR <text>` | Notices and errors |

## Getting Started

Requires **Java 21+** and **Maven**.

```bash
git clone https://github.com/kassembeirouty/java-chat-app.git
cd java-chat-app
mvn package
java -jar target/java-chat-app.jar
```

1. Click **Start Server**
2. Click **Join Chat** (open it several times to have several users)
3. To chat from another computer on the same Wi-Fi, run the app there and use the server's IP address (shown in the server window)

## Tests

```bash
mvn test
```

19 tests, including **integration tests** that start a real server on a random free port and connect real clients over TCP. They verify:
- login, duplicate and invalid usernames
- messages reach everyone in a room, and **only** that room
- private messages reach **only** their target
- the online users list updates when people join and leave
- newcomers receive recent history
- new rooms are announced to everyone
- protocol parsing, validation, and text cleaning

## Project Structure

```
src/main/java/com/chatapp/
├── common/
│   ├── Protocol.java        # message format, parsing, validation
│   └── NetworkInfo.java     # local IP addresses
├── server/
│   ├── ChatServer.java      # accept loop, rooms, users, broadcasting
│   └── ClientHandler.java   # one thread per connected client
├── client/
│   └── ChatClient.java      # socket connection + background reader
├── ui/
│   ├── LauncherWindow.java  # start screen
│   ├── ServerWindow.java    # server log and users
│   └── ChatWindow.java      # chat interface
└── Main.java
```

## Tech Stack

Java 21 · TCP sockets · Multithreading (ExecutorService, concurrent collections) · Swing · FlatLaf · Maven · JUnit 5 · GitHub Actions