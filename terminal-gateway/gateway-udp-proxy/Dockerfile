FROM openjdk:8-jre-slim

ENV TZ=Asia/Shanghai
ENV LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        curl iptables g++ \
        libdrm2 libxcb1 libxcb-shm0 libxcb-shape0 libxcb-xfixes0 libjpeg62-turbo libv4l-0 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
RUN mkdir -p /app/lib /app/certs /app/config /app/native

COPY target/gateway-udp-proxy-1.0-SNAPSHOT.jar /app/app.jar
COPY src/main/native/VAuthPackBridge.cpp /app/native/VAuthPackBridge.cpp
COPY src/main/docker/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# SVAC PackData SDK loads repack/transcode resources relative to cwd.
WORKDIR /app/lib

EXPOSE 8093

ENTRYPOINT ["/app/entrypoint.sh"]
