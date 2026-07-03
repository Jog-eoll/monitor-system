FROM openjdk:8-jre-slim
WORKDIR /app

ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

COPY target/*.jar app.jar

EXPOSE 8067

ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]
