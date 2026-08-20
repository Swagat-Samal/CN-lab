package lab5_HTTP;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class HTTPClient {
    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        URI uri = URI.create("http://localhost:8080/api/users");

        send(client, HttpRequest.newBuilder(uri).GET().build());
        send(client, HttpRequest.newBuilder(uri).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"test\",\"email\":\"test@example.com\",\"course\":\"CN\"}", StandardCharsets.UTF_8)).build());
        send(client, HttpRequest.newBuilder(uri).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"id\":1,\"name\":\"updated\",\"email\":\"updated@example.com\",\"course\":\"CN\"}", StandardCharsets.UTF_8)).build());
        send(client, HttpRequest.newBuilder(uri).method("PATCH", HttpRequest.BodyPublishers.ofString("{\"name\":\"patched\"}")).build());
        send(client, HttpRequest.newBuilder(URI.create(uri + "?id=1")).DELETE().build());
        send(client, HttpRequest.newBuilder(uri).method("HEAD", HttpRequest.BodyPublishers.noBody()).build());
        send(client, HttpRequest.newBuilder(uri).method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build());
    }

    private static void send(HttpClient client, HttpRequest request) throws Exception {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("\n" + request.method() + " -> Status: " + response.statusCode());
        if (!response.body().isEmpty()) System.out.println(response.body());
        if (request.method().equals("OPTIONS")) System.out.println("Allow: " + response.headers().firstValue("Allow").orElse(""));
    }
}
