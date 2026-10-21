@echo off
rem adb falso de Windows para los tests de CliAdbGateway; equivalente a adb.sh, con las mismas
rem variables de entorno:
rem   FAKE_ADB_ARGS_FILE  archivo donde se anade una linea con los argumentos de cada llamada
rem   FAKE_ADB_STDOUT     archivos a imprimir por stdout, separados por espacios: la llamada N usa el
rem                       N-esimo (o el ultimo si hay menos). Requiere FAKE_ADB_ARGS_FILE para contar.
rem   FAKE_ADB_STDERR     igual que FAKE_ADB_STDOUT, por stderr
rem   FAKE_ADB_EXIT       codigo de salida (0 por defecto)
rem   FAKE_ADB_SLEEP      segundos de espera antes de responder (para probar timeouts)
setlocal EnableDelayedExpansion

set calls=1
if defined FAKE_ADB_ARGS_FILE (
  >>"%FAKE_ADB_ARGS_FILE%" echo(%*
  for /f %%n in ('%SystemRoot%\System32\find.exe /c /v "" ^< "%FAKE_ADB_ARGS_FILE%"') do set calls=%%n
)

rem ping espera ~1 s entre intentos; timeout.exe no sirve porque falla con la entrada redirigida.
if defined FAKE_ADB_SLEEP (
  set /a pings=FAKE_ADB_SLEEP + 1
  %SystemRoot%\System32\ping.exe -n !pings! 127.0.0.1 >nul
)

if defined FAKE_ADB_STDOUT (
  set i=0
  for %%f in (%FAKE_ADB_STDOUT%) do (
    set /a i+=1
    if !i! leq %calls% set chosen=%%f
  )
  type "!chosen!"
)
if defined FAKE_ADB_STDERR (
  set i=0
  for %%f in (%FAKE_ADB_STDERR%) do (
    set /a i+=1
    if !i! leq %calls% set chosen=%%f
  )
  type "!chosen!" 1>&2
)

if not defined FAKE_ADB_EXIT set FAKE_ADB_EXIT=0
exit %FAKE_ADB_EXIT%
