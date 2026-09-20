FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY src/TodoApp.java .
COPY public ./public
RUN javac TodoApp.java
CMD ["java", "TodoApp"]