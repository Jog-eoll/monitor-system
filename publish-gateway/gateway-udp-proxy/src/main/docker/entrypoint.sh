#!/bin/sh
set -eu

export LD_LIBRARY_PATH="/app/lib:${LD_LIBRARY_PATH:-}"

build_bridge() {
  if [ ! -f /app/lib/libvauthsdk.so ]; then
    return
  fi
  if [ -f /app/lib/libvauthpackbridge.so ]; then
    return
  fi
  echo "[entrypoint] building libvauthpackbridge.so"
  g++ -shared -fPIC -O2 \
    -L/app/lib -Wl,-rpath,/app/lib \
    -o /app/lib/libvauthpackbridge.so \
    /app/native/VAuthPackBridge.cpp \
    -lvauthsdk
}

check_svac_files() {
  case "${VAUTH_SVAC_MODE:-false}" in
    true|TRUE|1|yes|YES) ;;
    *) return ;;
  esac

  missing=""
  for file in \
    libvauthsdk.so \
    libvauthpackbridge.so \
    libCommonLib.so \
    libGmsslUtility.so \
    libzbaselib.so \
    libZxTransRepackSDK.so \
    libavcodec.so.62 \
    libavformat.so.62 \
    libavutil.so.60 \
    libswresample.so.6 \
    libswscale.so.9 \
    StreamAnalyzer.zl \
    SVAC2Encoder.zl \
    SVAC2Decoder.zl \
    FFMPEGUtility.zl \
    pack.svac
  do
    if [ ! -f "/app/lib/$file" ]; then
      missing="$missing $file"
    fi
  done

  if [ -n "$missing" ]; then
    echo "[entrypoint] missing SVAC PackData runtime files:$missing" >&2
    exit 1
  fi
}

build_bridge
check_svac_files

cd /app/lib
exec java \
  -Djava.security.egd=file:/dev/./urandom \
  -Xmx512m -Xms256m \
  -Djna.library.path=/app/lib \
  -Dspring.config.location=optional:classpath:/,optional:file:/app/config/ \
  -jar /app/app.jar
