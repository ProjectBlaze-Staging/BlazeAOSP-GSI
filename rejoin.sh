#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "=========================================="
echo "   BlazeAOSP GSI Reassembly Utility"
echo "=========================================="
EXPECTED_SHA="21631948647bcc38bf1de100004b399f9f2e057521f2b343bf1b5fe6d4cc01d1"

if [ -f "system.img.gz" ]; then
    echo "[*] Found existing system.img.gz, verifying checksum..."
    ACTUAL_SHA=$(sha256sum system.img.gz | awk '{print $1}')
    if [ "$ACTUAL_SHA" = "$EXPECTED_SHA" ]; then
        echo "[+] system.img.gz is already present and matches the official checksum!"
        exit 0
    else
        echo "[!] Checksum mismatch, reassembling from parts..."
        rm -f system.img.gz
    fi
fi

echo "[*] Rejoining 20 parts into system.img.gz..."
cat system.img.gz.part* > system.img.gz

echo "[*] Verifying SHA256 checksum..."
ACTUAL_SHA=$(sha256sum system.img.gz | awk '{print $1}')

if [ "$ACTUAL_SHA" = "$EXPECTED_SHA" ]; then
    echo "[+] SUCCESS! Checksum verified: $ACTUAL_SHA"
    echo ""
    echo "To extract raw system.img for fastbootd flashing:"
    echo "  gzip -d -k system.img.gz"
else
    echo "[-] ERROR: Checksum validation failed!"
    echo "Expected: $EXPECTED_SHA"
    echo "Actual:   $ACTUAL_SHA"
    exit 1
fi
