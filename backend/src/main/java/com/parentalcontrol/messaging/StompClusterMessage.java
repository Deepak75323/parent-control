package com.parentalcontrol.messaging;

/**
 * JSON envelope for Redis STOMP cluster fan-out.
 */
public record StompClusterMessage(String destination, String payloadJson) {}
