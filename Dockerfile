FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY . /app
RUN javac -cp ".:Java-WebSocket.jar:slf4j-api.jar:slf4j-simple.jar" ImposterGame.java
EXPOSE 9090
CMD ["java", "-cp", ".:Java-WebSocket.jar:slf4j-api.jar:slf4j-simple.jar", "ImposterGame"]
