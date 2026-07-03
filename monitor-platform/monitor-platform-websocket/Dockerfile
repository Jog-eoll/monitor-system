FROM openjdk:8-jre-slim
WORKDIR /app

ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

COPY target/*.jar app.jar

EXPOSE 8070

ENTRYPOINT ["java", "-Xms128m", "-Xmx256m", "-jar", "app.jar"]
