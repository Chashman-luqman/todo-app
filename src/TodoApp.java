import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;


public class TodoApp {

    // ---------- Data ----------
    static class Todo {
        int id;
        boolean done;
        String text;

        Todo(int id, boolean done, String text) {
            this.id = id;
            this.done = done;
            this.text = text;
        }
    }

    static final List<Todo> todos = new ArrayList<>();
    static final Path DATA_FILE = Paths.get("todos.txt");
    static final Path PUBLIC_DIR = Paths.get("public").toAbsolutePath().normalize();
    static int nextId = 1;

    // ---------- Main ----------
    public static void main(String[] args) throws IOException {
        loadTodos();

        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8083"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/api/todos", TodoApp::handleTodos);
        server.createContext("/api/toggle", TodoApp::handleToggle);
        server.createContext("/api/delete", TodoApp::handleDelete);
        server.createContext("/api/clear-done", TodoApp::handleClearDone);
        server.createContext("/", TodoApp::handleStatic);

        server.start();
        System.out.println("To-Do app is running at http://localhost:" + port);
        System.out.println("Press Ctrl+C to stop.");
    }

    // ---------- API handlers ----------
    // GET  /api/todos  -> list all tasks as JSON
    // POST /api/todos  -> add a task (request body = task text)
    static void handleTodos(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            if (method.equals("GET")) {
                sendJson(ex, 200, toJson());
            } else if (method.equals("POST")) {
                String text = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                text = clean(text);
                if (text.isEmpty()) {
                    sendJson(ex, 400, "{\"error\":\"Task text is empty\"}");
                    return;
                }
                addTodo(text);
                sendJson(ex, 200, toJson());
            } else {
                sendJson(ex, 405, "{\"error\":\"Method not allowed\"}");
            }
        } catch (Exception e) {
            sendJson(ex, 500, "{\"error\":\"Something went wrong\"}");
        }
    }

    // POST /api/toggle?id=3 -> flip done / not done
    static void handleToggle(HttpExchange ex) throws IOException {
        try {
            int id = readId(ex);
            toggleTodo(id);
            sendJson(ex, 200, toJson());
        } catch (Exception e) {
            sendJson(ex, 400, "{\"error\":\"Invalid request\"}");
        }
    }

    // POST /api/delete?id=3 -> remove a task
    static void handleDelete(HttpExchange ex) throws IOException {
        try {
            int id = readId(ex);
            deleteTodo(id);
            sendJson(ex, 200, toJson());
        } catch (Exception e) {
            sendJson(ex, 400, "{\"error\":\"Invalid request\"}");
        }
    }

    // POST /api/clear-done -> remove every completed task
    static void handleClearDone(HttpExchange ex) throws IOException {
        try {
            clearDone();
            sendJson(ex, 200, toJson());
        } catch (Exception e) {
            sendJson(ex, 500, "{\"error\":\"Something went wrong\"}");
        }
    }

    // ---------- Static files (HTML, CSS, JS) ----------
    static void handleStatic(HttpExchange ex) throws IOException {
        String uriPath = ex.getRequestURI().getPath();
        if (uriPath.equals("/")) {
            uriPath = "/index.html";
        }

        Path file = PUBLIC_DIR.resolve(uriPath.substring(1)).normalize();

        // Safety: never serve anything outside the "public" folder
        if (!file.startsWith(PUBLIC_DIR) || !Files.isRegularFile(file)) {
            send(ex, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
            return;
        }

        String name = file.getFileName().toString();
        String type = "application/octet-stream";
        if (name.endsWith(".html")) type = "text/html; charset=utf-8";
        else if (name.endsWith(".css")) type = "text/css; charset=utf-8";
        else if (name.endsWith(".js")) type = "application/javascript; charset=utf-8";
        else if (name.endsWith(".svg")) type = "image/svg+xml";

        send(ex, 200, type, Files.readAllBytes(file));
    }

    // ---------- Task logic (synchronized so two requests never clash) ----------
    static synchronized void addTodo(String text) throws IOException {
        todos.add(new Todo(nextId++, false, text));
        saveTodos();
    }

    static synchronized void toggleTodo(int id) throws IOException {
        for (Todo t : todos) {
            if (t.id == id) {
                t.done = !t.done;
            }
        }
        saveTodos();
    }

    static synchronized void deleteTodo(int id) throws IOException {
        todos.removeIf(t -> t.id == id);
        saveTodos();
    }

    static synchronized void clearDone() throws IOException {
        todos.removeIf(t -> t.done);
        saveTodos();
    }

    // ---------- Saving and loading (plain text file: id <TAB> done <TAB> text) ----------
    static synchronized void saveTodos() throws IOException {
        List<String> lines = new ArrayList<>();
        for (Todo t : todos) {
            lines.add(t.id + "\t" + t.done + "\t" + t.text);
        }
        Files.write(DATA_FILE, lines, StandardCharsets.UTF_8);
    }

    static synchronized void loadTodos() {
        if (!Files.exists(DATA_FILE)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(DATA_FILE, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\t", 3);
                if (parts.length < 3) {
                    continue; // skip damaged lines
                }
                int id = Integer.parseInt(parts[0]);
                todos.add(new Todo(id, Boolean.parseBoolean(parts[1]), parts[2]));
                nextId = Math.max(nextId, id + 1);
            }
        } catch (Exception e) {
            System.out.println("Could not read " + DATA_FILE + ": " + e.getMessage());
        }
    }

    // ---------- Helpers ----------
    static int readId(HttpExchange ex) {
        String query = ex.getRequestURI().getQuery(); // e.g. "id=3"
        return Integer.parseInt(query.substring(query.indexOf("id=") + 3).split("&")[0]);
    }

    /** Trim, turn tabs/new lines into spaces, and limit the length. */
    static String clean(String text) {
        text = text.replaceAll("[\\t\\r\\n]+", " ").trim();
        if (text.length() > 200) {
            text = text.substring(0, 200);
        }
        return text;
    }

    static synchronized String toJson() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < todos.size(); i++) {
            Todo t = todos.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"id\":").append(t.id)
              .append(",\"done\":").append(t.done)
              .append(",\"text\":\"").append(escapeJson(t.text)).append("\"}");
        }
        return sb.append("]").toString();
    }

    static String escapeJson(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    static void sendJson(HttpExchange ex, int status, String json) throws IOException {
        send(ex, status, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    static void send(HttpExchange ex, int status, String contentType, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }
}
