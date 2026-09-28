package com.chatapp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chatapp.common.Protocol;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtocolTest {

    @Test
    void validUsernames() {
        assertTrue(Protocol.isValidUsername("kassem"));
        assertTrue(Protocol.isValidUsername("User_123"));
        assertFalse(Protocol.isValidUsername("ab"));                 // too short
        assertFalse(Protocol.isValidUsername("a".repeat(17)));       // too long
        assertFalse(Protocol.isValidUsername("has space"));
        assertFalse(Protocol.isValidUsername("bad!"));
        assertFalse(Protocol.isValidUsername(null));
    }

    @Test
    void validRooms() {
        assertTrue(Protocol.isValidRoom("general"));
        assertTrue(Protocol.isValidRoom("study-group_2"));
        assertFalse(Protocol.isValidRoom("x"));
        assertFalse(Protocol.isValidRoom("Upper"));
        assertFalse(Protocol.isValidRoom("with space"));
    }

    @Test
    void parseKeepsSpacesInTheLastArgument() {
        String[] parts = Protocol.parse("MSG 123 general alice hello there friend", 4);
        assertArrayEquals(new String[] {"MSG", "123", "general", "alice", "hello there friend"}, parts);
    }

    @Test
    void parseReturnsNullWhenArgumentsAreMissing() {
        assertNull(Protocol.parse("PM bob", 2));
        assertNull(Protocol.parse("HELLO", 1));
        assertNull(Protocol.parse("SAY ", 1));
    }

    @Test
    void cleanTextRemovesLineBreaksAndLimitsLength() {
        assertEquals("line one line two", Protocol.cleanText("  line one\nline two  "));
        assertEquals(Protocol.MAX_MESSAGE_LENGTH, Protocol.cleanText("x".repeat(1000)).length());
        assertEquals("", Protocol.cleanText(null));
    }

    @Test
    void commandAndRest() {
        assertEquals("INFO", Protocol.command("INFO bob joined #general"));
        assertEquals("bob joined #general", Protocol.rest("INFO bob joined #general"));
        assertEquals("QUIT", Protocol.command("QUIT"));
        assertEquals("", Protocol.rest("QUIT"));
    }

    @Test
    void listsRoundTrip() {
        assertEquals("USERS alice,bob", Protocol.users(List.of("alice", "bob")));
        assertEquals(List.of("alice", "bob"), Protocol.splitList("alice,bob"));
        assertEquals(List.of(), Protocol.splitList(""));
    }
}