FROM openjdk:8-jre-slim

WORKDIR /app

ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime \
    && echo $TZ > /etc/timezone \
    && apt-get update \
    && apt-get install -y --no-install-recommends libusb-1.0-0 libudev1 curl \
    && rm -rf /var/lib/apt/lists/*

COPY target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS:--Xms512m -Xmx1024m} -jar /app/app.jar"]
