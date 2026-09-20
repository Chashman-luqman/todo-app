FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY src/TodoApp.java .
COPY public ./public
RUN javac TodoApp.java && chmod -R 777 /app
ENV PORT=7860
CMD ["java", "TodoApp"]