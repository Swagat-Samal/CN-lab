package lab5_HTTP;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HTTPServer {
    record User(int id, String name, String email, String course, String status) {}

    static final List<User> users = new CopyOnWriteArrayList<>();
    static final AtomicInteger nextId = new AtomicInteger(1);
    static final AtomicInteger apiRequests = new AtomicInteger(0);
    static final int PORT = 8080;

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", PORT), 0);

        server.createContext("/api/users", HTTPServer::handleUsers);
        server.createContext("/api/stats", ex -> {
            sendJson(ex, 200, "{\"totalUsers\":" + users.size() + ",\"apiRequests\":" + apiRequests.get()
                    + ",\"port\":" + PORT + ",\"status\":\"Online\"}");
        });
        server.createContext("/", ex -> {
            if (!ex.getRequestURI().getPath().equals("/")) { ex.sendResponseHeaders(404, -1); return; }
            byte[] bytes = PAGE.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });

        server.setExecutor(null);
        server.start();
        System.out.println("Dashboard running at http://localhost:" + PORT + "/");
    }

    static void handleUsers(com.sun.net.httpserver.HttpExchange ex) throws IOException {
        apiRequests.incrementAndGet();
        String method = ex.getRequestMethod();

        if (method.equals("OPTIONS")) {
            ex.getResponseHeaders().set("Allow", "GET, POST, PUT, DELETE, OPTIONS");
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            sendEmpty(ex, 204);
            return;
        }
        if (method.equals("HEAD")) {
            ex.getResponseHeaders().set("Allow", "GET, POST, PUT, DELETE, OPTIONS");
            sendEmpty(ex, 200);
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        switch (method) {
            case "GET" -> {
                String idParam = query(ex.getRequestURI(), "id");
                if (idParam.isEmpty()) {
                    sendJson(ex, 200, toJsonArray(users));
                } else {
                    Integer lookupId = parseIntOrNull(idParam);
                    if (lookupId == null) { sendJson(ex, 400, "{\"error\":\"invalid id\"}"); return; }
                    User found = users.stream().filter(u -> u.id() == lookupId).findFirst().orElse(null);
                    if (found == null) sendJson(ex, 404, "{\"error\":\"user not found\"}");
                    else sendJson(ex, 200, toJson(found));
                }
            }
            case "POST" -> {
                String name = field(body, "name"), email = field(body, "email"), course = field(body, "course");
                if (blank(name) || blank(email) || blank(course)) {
                    sendJson(ex, 400, "{\"error\":\"name, email, and course are required\"}");
                    return;
                }
                User u = new User(nextId.getAndIncrement(), name, email, course, "Active");
                users.add(u);
                sendJson(ex, 201, toJson(u));
            }
            case "PUT" -> {
                Integer id = parseIntOrNull(field(body, "id"));
                if (id == null) { sendJson(ex, 400, "{\"error\":\"missing or invalid id\"}"); return; }
                String name = field(body, "name"), email = field(body, "email"), course = field(body, "course");
                User existing = users.stream().filter(u -> u.id() == id).findFirst().orElse(null);
                if (existing == null) {
                    sendJson(ex, 404, "{\"error\":\"user not found\"}");
                    return;
                }
                User updated = new User(id,
                        blank(name) ? existing.name() : name,
                        blank(email) ? existing.email() : email,
                        blank(course) ? existing.course() : course,
                        existing.status());
                users.removeIf(u -> u.id() == id);
                users.add(updated);
                sendJson(ex, 200, toJson(updated));
            }
            case "DELETE" -> {
                Integer id = parseIntOrNull(query(ex.getRequestURI(), "id"));
                if (id == null) { sendJson(ex, 400, "{\"error\":\"missing or invalid id\"}"); return; }
                if (!users.removeIf(u -> u.id() == id)) {
                    sendJson(ex, 404, "{\"error\":\"user not found\"}");
                    return;
                }
                sendJson(ex, 200, "{\"deleted\":" + id + "}");
            }
            default -> sendJson(ex, 405, "{\"error\":\"method not allowed\"}");
        }
    }

    static Integer parseIntOrNull(String s) {
        try { return s.isEmpty() ? null : Integer.parseInt(s); }
        catch (NumberFormatException e) { return null; }
    }

    static boolean blank(String value) { return value == null || value.isBlank(); }

    static void sendJson(com.sun.net.httpserver.HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    static void sendEmpty(com.sun.net.httpserver.HttpExchange ex, int status) throws IOException {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(status, -1);
        ex.close();
    }

    // ponytail: hand-rolled instead of a JSON library — only pulls known flat string fields, breaks on nested/escaped JSON
    static String field(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(?:\"((?:\\\\.|[^\"\\\\])*)\"|(-?\\d+))").matcher(json);
        if (!m.find()) return "";
        String value = m.group(1) != null ? m.group(1) : m.group(2);
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    static String query(URI uri, String key) {
        String q = uri.getRawQuery();
        if (q == null) return "";
        for (String pair : q.split("&")) {
            String[] kv = pair.split("=", 2);
            if (URLDecoder.decode(kv[0], StandardCharsets.UTF_8).equals(key))
                return kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
        }
        return "";
    }

    static String toJson(User u) {
        return "{\"id\":%d,\"name\":\"%s\",\"email\":\"%s\",\"course\":\"%s\",\"status\":\"%s\"}"
                .formatted(u.id(), escapeJson(u.name()), escapeJson(u.email()), escapeJson(u.course()), escapeJson(u.status()));
    }

    static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    static String toJsonArray(List<User> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append(toJson(list.get(i)));
            if (i < list.size() - 1) sb.append(",");
        }
        return sb.append("]").toString();
    }

    static final String PAGE = """
    <!DOCTYPE html>
    <html>
    <head>
    <meta charset="utf-8">
    <title>Java REST API Dashboard</title>
    <style>
      body { background:#0d1117; color:#e6edf3; font-family: Arial, sans-serif; margin:0; padding:24px; }
      h1 { margin:0; }
      .sub { color:#8b949e; margin-top:4px; }
      .online { color:#3fb950; float:right; }
      .dot { height:10px; width:10px; background:#3fb950; border-radius:50%; display:inline-block; margin-right:6px; }
      .cards { display:grid; grid-template-columns: repeat(4,1fr); gap:16px; margin:24px 0; }
      .card { background:#161b22; border:1px solid #30363d; border-radius:10px; padding:16px; }
      .card .label { color:#8b949e; font-size:14px; }
      .card .value { font-size:28px; font-weight:bold; margin-top:6px; }
      .green { color:#3fb950; }
      section { background:#161b22; border:1px solid #30363d; border-radius:10px; padding:16px; margin-bottom:24px; }
      .tabs button { padding:8px 18px; margin-right:8px; border-radius:6px; border:1px solid #30363d; background:#0d1117; color:#e6edf3; cursor:pointer; }
      .tabs button.active[data-m="GET"] { background:#30363d; }
      .tabs button.active[data-m="POST"] { background:#238636; }
      .tabs button.active[data-m="PUT"] { background:#1f6feb; }
      .tabs button.active[data-m="DELETE"] { background:#da3633; }
      label { display:block; margin-top:12px; color:#8b949e; font-size:13px; }
      input { width:100%; box-sizing:border-box; padding:8px; margin-top:4px; background:#0d1117; border:1px solid #30363d; border-radius:6px; color:#e6edf3; }
      .send { width:100%; margin-top:16px; padding:12px; background:#1f6feb; border:none; border-radius:6px; color:white; font-weight:bold; cursor:pointer; }
      pre { background:#0d1117; border:1px solid #30363d; border-radius:6px; padding:12px; overflow:auto; }
      table { width:100%; border-collapse:collapse; margin-top:12px; }
      th, td { text-align:left; padding:10px; border-bottom:1px solid #30363d; }
      .badge { background:#238636; color:white; padding:3px 10px; border-radius:12px; font-size:12px; }
      .del { background:#da3633; color:white; border:none; padding:6px 12px; border-radius:6px; cursor:pointer; }
      .status { color:#3fb950; font-weight:bold; }
    </style>
    </head>
    <body>

    <div><h1>API Dashboard <span class="online"><span class="dot"></span>Server Online</span></h1>
    <div class="sub">Manage your Java REST API</div></div>

    <div class="cards">
      <div class="card"><div class="label">Total Users</div><div class="value" id="totalUsers">-</div></div>
      <div class="card"><div class="label">API Requests</div><div class="value" id="apiRequests">-</div></div>
      <div class="card"><div class="label">Server Port</div><div class="value">8080</div></div>
      <div class="card"><div class="label">Status</div><div class="value green">Online</div></div>
    </div>

    <section>
      <h3>API Request</h3>
      <div class="tabs" id="tabs">
        <button data-m="GET" class="active">GET</button>
        <button data-m="POST">POST</button>
        <button data-m="PUT">PUT</button>
        <button data-m="DELETE">DELETE</button>
      </div>
      <label>Endpoint</label>
      <input id="endpoint" value="/api/users" readonly>
      <div id="idField"><label>User ID</label><input id="userId" placeholder="Leave blank for GET to list all"></div>
      <div id="nameField"><label>Name</label><input id="name"></div>
      <div id="emailField"><label>Email</label><input id="email"></div>
      <div id="courseField"><label>Course</label><input id="course"></div>
      <button class="send" onclick="sendRequest()">Send Request &rarr;</button>
    </section>

    <section>
      <h3>API Response</h3>
      <div>Response <span id="respStatus" style="float:right;color:#3fb950;"></span></div>
      <pre id="response">{}</pre>
    </section>

    <section>
      <h3>Users</h3>
      <table>
        <thead><tr><th>ID</th><th>NAME</th><th>EMAIL</th><th>COURSE</th><th>STATUS</th><th>ACTION</th></tr></thead>
        <tbody id="userRows"></tbody>
      </table>
    </section>

    <script>
    let method = "GET";
    document.getElementById("tabs").addEventListener("click", e => {
      if (e.target.tagName !== "BUTTON") return;
      method = e.target.dataset.m;
      document.querySelectorAll("#tabs button").forEach(b => b.classList.toggle("active", b === e.target));
      document.getElementById("nameField").style.display = (method === "POST" || method === "PUT") ? "block" : "none";
      document.getElementById("emailField").style.display = (method === "POST" || method === "PUT") ? "block" : "none";
      document.getElementById("courseField").style.display = (method === "POST" || method === "PUT") ? "block" : "none";
      document.getElementById("idField").style.display = (method === "GET" || method === "PUT" || method === "DELETE") ? "block" : "none";
    });
    document.getElementById("tabs").firstElementChild.click();

    async function sendRequest() {
      let url = "/api/users";
      let opts = { method };
      const id = document.getElementById("userId").value;
      if (method === "GET" && id) {
        url += "?id=" + id;
      }
      if (method === "POST" || method === "PUT") {
        opts.body = JSON.stringify({
          id: document.getElementById("userId").value,
          name: document.getElementById("name").value,
          email: document.getElementById("email").value,
          course: document.getElementById("course").value
        });
      }
      if (method === "DELETE") {
        url += "?id=" + document.getElementById("userId").value;
      }
      const res = await fetch(url, opts);
      const text = await res.text();
      document.getElementById("respStatus").textContent = res.status + " " + (res.ok ? "OK" : "");
      document.getElementById("response").textContent = text;
      await refresh();
    }

    async function refresh() {
      const stats = await (await fetch("/api/stats")).json();
      document.getElementById("totalUsers").textContent = stats.totalUsers;
      document.getElementById("apiRequests").textContent = stats.apiRequests;

      const users = await (await fetch("/api/users")).json();
      const rows = document.getElementById("userRows");
      rows.replaceChildren(...users.map(u => {
        const row = document.createElement("tr");
        ["#" + u.id, u.name, u.email, u.course].forEach(value => {
          const cell = document.createElement("td");
          cell.textContent = value;
          row.append(cell);
        });
        const status = document.createElement("span");
        status.className = "status";
        status.textContent = u.status;
        const statusCell = document.createElement("td");
        statusCell.append(status);
        const button = document.createElement("button");
        button.className = "del";
        button.textContent = "Delete";
        button.onclick = () => del(u.id);
        const actionCell = document.createElement("td");
        actionCell.append(button);
        row.append(statusCell, actionCell);
        return row;
      }));
    }

    async function del(id) {
      await fetch("/api/users?id=" + id, { method: "DELETE" });
      await refresh();
    }

    refresh();
    </script>
    </body>
    </html>
    """;
}
