# 使用 openjdk:8-jre-slim（glibc环境，JNA加载libvauthsdk.so必需；alpine基于musl不兼容）
# 镜像加速通过 /etc/docker/daemon.json 配置的 registry-mirrors 自动走毫秒镜像
FROM openjdk:8-jre-slim

# 时区配置
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 安装必要工具：curl(健康检查)、iptables(CatchAll动态端口REDIRECT)、中文字体支持
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl fonts-dejavu fontconfig iptables iproute2 g++ \
    libdrm2 libxcb1 libxcb-shm0 libxcb-shape0 libxcb-xfixes0 libjpeg62-turbo libv4l-0 \
    && rm -rf /var/lib/apt/lists/*

# 创建工作目录及子目录
WORKDIR /app
RUN mkdir -p /app/lib /app/certs /app/config /app/native

# 复制编译好的 JAR（构建前请先执行 mvn clean package）
COPY target/gateway-udp-proxy-1.0.0.jar /app/app.jar
COPY src/main/native/VAuthPackBridge.cpp /app/native/VAuthPackBridge.cpp
COPY src/main/docker/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# SVAC PackData SDK loads repack/transcode resources relative to cwd.
WORKDIR /app/lib

# 暴露 HTTP 管理端口和 UDP 端口范围
EXPOSE 8092
EXPOSE 9000-9999/udp

# 启动命令：
#   -Djna.library.path=/app/lib     JNA 从此路径加载 libvauthsdk.so
#   -Dspring.config.location        优先加载外部 config/application.yml（覆盖jar内配置）
ENTRYPOINT ["/app/entrypoint.sh"]
