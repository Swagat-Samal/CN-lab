package lab3_TCP;

import java.net.*;
import java.io.*;
import java.util.Scanner;

public class TCPClient {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public TCPClient(String serverHost, int serverPort) throws IOException {
        socket = new Socket(serverHost, serverPort);
        socket.setSoTimeout(5000);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);

        System.out.println("Connected to " + serverHost +
                " (" + socket.getInetAddress().getHostAddress() + "):" + serverPort);
        System.out.println("Local endpoint: " + socket.getLocalAddress().getHostAddress() +
                ":" + socket.getLocalPort());

        if (socket.getInetAddress().isLoopbackAddress()) {
            System.out.println("NOTE: Connected to localhost. This is perfect for learning!");
        } else {
            System.out.println("WARNING: Connected to external host. Make sure firewall allows TCP on port " + serverPort);
        }
    }

    public void sendMessage(String message) throws IOException {
        out.println(message);
        System.out.println("Sent: " + message);

        try {
            String response = in.readLine();

            if (response == null) {
                System.out.println("Server closed the connection.");
            } else {
                System.out.println("Received: " + response);
            }
        } catch (SocketTimeoutException e) {
            System.out.println("No response received (timeout)");
        }
    }

    public void close() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {

        }
    }

    public static void main(String[] args) {
        String serverHost = "localhost";
        int serverPort = 6000;

        if (args.length >= 1) {
            serverHost = args[0];
        }
        if (args.length >= 2) {
            try {
                serverPort = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default port 6000.");
            }
        }

        System.out.println("\n=== TCP Echo Client ===");
        System.out.println("For learning, it's recommended to use 'localhost' or '127.0.0.1'");
        System.out.println("To connect to external servers, use their IP address or hostname");
        System.out.println("Current target: " + serverHost + ":" + serverPort + "\n");

        try {
            TCPClient client = new TCPClient(serverHost, serverPort);
            Scanner scanner = new Scanner(System.in);

            System.out.println("Enter messages to send (type 'quit' to exit):");

            while (true) {
                System.out.print("> ");
                String message = scanner.nextLine();

                if ("quit".equalsIgnoreCase(message)) {
                    break;
                }

                try {
                    client.sendMessage(message);
                } catch (IOException e) {
                    System.err.println("Error sending message: " + e.getMessage());
                    break;
                }
            }

            scanner.close();
            client.close();
            System.out.println("Client shutdown.");

        } catch (ConnectException e) {
            System.err.println("Could not connect: " + e.getMessage());
            System.err.println("TIP: Make sure the server is running on " + serverHost + ":" + serverPort);
        } catch (UnknownHostException e) {
            System.err.println("Unknown host: " + e.getMessage());
            System.err.println("TIP: Use 'localhost' or '127.0.0.1' for local testing.");
        } catch (IOException e) {
            System.err.println("Could not create client: " + e.getMessage());
        }
    }
}
