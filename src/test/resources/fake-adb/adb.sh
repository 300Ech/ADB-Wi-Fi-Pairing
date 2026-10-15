#!/bin/sh
# adb falso para los tests de CliAdbGateway. Se configura por variables de entorno:
#   FAKE_ADB_ARGS_FILE  archivo donde se añade una línea con los argumentos de cada llamada
#   FAKE_ADB_STDOUT     archivos a imprimir por stdout, separados por espacios: la llamada N usa el
#                       N-ésimo (o el último si hay menos). Requiere FAKE_ADB_ARGS_FILE para contar.
#   FAKE_ADB_STDERR     igual que FAKE_ADB_STDOUT, por stderr
#   FAKE_ADB_EXIT       código de salida (0 por defecto)
#   FAKE_ADB_SLEEP      segundos de espera antes de responder (para probar timeouts)

calls=1
if [ -n "$FAKE_ADB_ARGS_FILE" ]; then
  printf '%s\n' "$*" >> "$FAKE_ADB_ARGS_FILE"
  calls=$(wc -l < "$FAKE_ADB_ARGS_FILE")
fi

pick() {
  n=$1
  shift
  i=0
  for f in "$@"; do
    i=$((i + 1))
    chosen=$f
    [ "$i" -eq "$n" ] && break
  done
  echo "$chosen"
}

[ -n "$FAKE_ADB_SLEEP" ] && sleep "$FAKE_ADB_SLEEP"
# shellcheck disable=SC2086 # las listas se separan por espacios a propósito
[ -n "$FAKE_ADB_STDOUT" ] && cat "$(pick "$calls" $FAKE_ADB_STDOUT)"
# shellcheck disable=SC2086
[ -n "$FAKE_ADB_STDERR" ] && cat "$(pick "$calls" $FAKE_ADB_STDERR)" >&2
exit "${FAKE_ADB_EXIT:-0}"
