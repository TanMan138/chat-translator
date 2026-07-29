package com.tanman.chattranslator.client.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncomingMessageFilterTest {

    @Test
    void offersOrdinaryPlayerChat() {
        assertTrue(IncomingMessageFilter.shouldOffer(
                "<Alice> bonjour tout le monde", "bonjour tout le monde", "Bob"));
    }

    @Test
    void skipsBlankAndTooShortBodies() {
        assertFalse(IncomingMessageFilter.shouldOffer("<Alice> ", "", "Bob"));
        assertFalse(IncomingMessageFilter.shouldOffer("<Alice> ", null, "Bob"));
        assertFalse(IncomingMessageFilter.shouldOffer("<Alice> gg", "gg", "Bob"));
        assertFalse(IncomingMessageFilter.shouldOffer("<Alice> :)", ":)", "Bob"));
        assertFalse(IncomingMessageFilter.shouldOffer("<Alice> 123 456", "123 456", "Bob"));
    }

    @Test
    void skipsOwnMessagesInCommonServerLayouts() {
        assertFalse(IncomingMessageFilter.shouldOffer(
                "<Bob> hallo zusammen", "hallo zusammen", "Bob"));
        assertFalse(IncomingMessageFilter.shouldOffer(
                "[VIP] Bob: hallo zusammen", "hallo zusammen", "Bob"));
        assertFalse(IncomingMessageFilter.shouldOffer(
                "[VIP] [MVP] Bob » hallo zusammen", "hallo zusammen", "bob"));
    }

    @Test
    void ownNameMentionedByAnotherPlayerStillTranslates() {
        assertTrue(IncomingMessageFilter.shouldOffer(
                "<Alice> Bob, viens ici", "Bob, viens ici", "Bob"));
        assertTrue(IncomingMessageFilter.shouldOffer(
                "<Alice> salut Bob comment ca va", "salut Bob comment ca va", "Bob"));
    }

    @Test
    void skipsServerEventLines() {
        assertFalse(IncomingMessageFilter.shouldOffer(
                "Alice joined the game", "Alice joined the game", "Bob"));
        assertFalse(IncomingMessageFilter.shouldOffer(
                "Alice left the game", "Alice left the game", "Bob"));
        assertFalse(IncomingMessageFilter.shouldOffer(
                "Alice has made the advancement [Stone Age]",
                "Alice has made the advancement [Stone Age]", "Bob"));
    }

    @Test
    void unknownSelfNameDoesNotFilter() {
        assertTrue(IncomingMessageFilter.shouldOffer(
                "<Bob> hallo zusammen", "hallo zusammen", null));
    }
}
