FROM openjdk:8-jre-slim
WORKDIR /app

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

COPY target/monitor-platform-registry-server-1.0.0.jar app.jar
EXPOSE 8069
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]