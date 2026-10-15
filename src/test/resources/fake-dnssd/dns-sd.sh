#!/bin/sh
# dns-sd falso: imprime el fixture que corresponde a los argumentos y se queda esperando, como el real.
# FAKE_DNSSD_DIR apunta a los fixtures; FAKE_DNSSD_LOG registra cada llamada.
[ -n "$FAKE_DNSSD_LOG" ] && printf '%s\n' "$*" >> "$FAKE_DNSSD_LOG"
case "$1 $2" in
  "-B _adb-tls-pairing._tcp") cat "$FAKE_DNSSD_DIR/browse-pairing.txt" ;;
  "-B _adb-tls-connect._tcp") cat "$FAKE_DNSSD_DIR/browse-connect.txt" ;;
  "-L studio-VISAKqPGtD") cat "$FAKE_DNSSD_DIR/lookup-pairing.txt" ;;
  "-L adb-58181FDCR0065F-XeVEcV") cat "$FAKE_DNSSD_DIR/lookup-connect.txt" ;;
esac
exec sleep 30
