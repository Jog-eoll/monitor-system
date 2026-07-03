FROM openjdk:8-jre-slim
WORKDIR /app

ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# UKey 在 Linux 下依赖 libusb 和 libudev 识别 USB 设备
# 缺失将导致 UKey 插拔事件无法触发
RUN apt-get update && apt-get install -y \
    libusb-1.0-0 \
    libudev1 \
    && rm -rf /var/lib/apt/lists/*

COPY target/*.jar app.jar

EXPOSE 8063

ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]
