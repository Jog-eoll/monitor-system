FROM openjdk:8-jre-slim
WORKDIR /app

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 复制jar包（需先在本地执行 mvn package 打包）
COPY target/*.jar app.jar

EXPOSE 8068

ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]
