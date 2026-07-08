#!/usr/bin/env bash
# TURN must listen on all interfaces so the Android emulator can reach it via 10.0.2.2
# (emulator maps 10.0.2.2 -> host loopback). Binding only to LAN IP breaks parent-on-emulator.
set -euo pipefail

LAN_IP="${LAN_IP:-$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo 127.0.0.1)}"
TURN_SECRET="${TURN_SECRET:-pcp_dev_turn_secret_32chars_minimum}"
TURN_REALM="${TURN_REALM:-pcp}"
MIN_PORT="${TURN_MIN_PORT:-49160}"
MAX_PORT="${TURN_MAX_PORT:-49200}"

pkill -f "turnserver.*realm=${TURN_REALM}" 2>/dev/null || true
sleep 1

# relay-ip=0.0.0.0 binds relay sockets on all interfaces — avoids EADDRNOTAVAIL when
# the Mac's LAN IP changes (e.g. 192.168.1.4 → 192.168.1.8) while coturn is running.
# external-ip is what goes in ICE relay candidates (phone on Wi-Fi needs a reachable LAN IP).
# LAN IP for physical phones; 10.0.2.2 for Android emulator (maps to host loopback).
echo "Starting coturn on 0.0.0.0:3478 (external: ${LAN_IP}/10.0.2.2, relay: all interfaces)"
exec turnserver \
  -n \
  --log-file=/tmp/pcp-coturn.log \
  --fingerprint \
  --realm="${TURN_REALM}" \
  --static-auth-secret="${TURN_SECRET}" \
  --listening-ip=0.0.0.0 \
  --relay-ip=0.0.0.0 \
  --external-ip="${LAN_IP}/10.0.2.2" \
  --listening-port=3478 \
  --min-port="${MIN_PORT}" \
  --max-port="${MAX_PORT}"
