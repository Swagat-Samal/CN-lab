package lab4_TCPChatApplication;

import java.net.*;
import java.io.*;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class TCPChatClient {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;
    private AtomicBoolean running = new AtomicBoolean(false);
    private Thread receiveThread;

    private class ReceiveThread extends Thread {
        public void run() {
            try {
                String message;
                while (running.get() && (message = in.readLine()) != null) {
                    System.out.println(message);
                }
            } catch (IOException e) {
                if (running.get()) {
                    System.err.println("Connection lost: " + e.getMessage());
                }
            }

            if (running.get()) {
                System.out.println("Disconnected from server. Press Enter to exit.");
                running.set(false);
            }
        }
    }

    public TCPChatClient(String serverHost, int serverPort, String username)
            throws IOException {
        this.socket = new Socket(serverHost, serverPort);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
        this.username = username;

        if (socket.getInetAddress().isLoopbackAddress()) {
            System.out.println("Connected to local server (localhost) - perfect for learning!");
        } else {
            System.out.println("Connected to remote server at " + serverHost);
            System.out.println("Make sure the server allows external connections and firewall permits TCP port " + serverPort);
        }
    }

    public void connect() {
        out.println("JOIN:" + username);

        running.set(true);
        receiveThread = new ReceiveThread();
        receiveThread.start();

        System.out.println("Connected to chat server as: " + username);
        System.out.println("Server: " + socket.getInetAddress().getHostAddress() +
                ":" + socket.getPort());
        System.out.println("\nCommands: /list (show users), /quit (exit), or just type to chat");

    }

    public void sendChatMessage(String message) {
        if (message.trim().isEmpty()) {
            return;
        }

        if (message.equalsIgnoreCase("/list")) {
            out.println("LIST");
        } else {
            out.println("MSG:" + message);
        }
    }

    public void disconnect() {
        if (running.getAndSet(false)) {
            out.println("LEAVE");

            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
            }

            try {
                if (receiveThread != null) {
                    receiveThread.join(2000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void main(String[] args) {
        String serverHost = "localhost";
        int serverPort = 6001;

        Scanner scanner = new Scanner(System.in);

        if (args.length >= 1) {
            serverHost = args[0];
        }
        if (args.length >= 2) {
            try {
                serverPort = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default port 6001.");
            }
        }

        System.out.println("\n=== TCP Chat Client ===");
        System.out.println("Default server: " + serverHost + ":" + serverPort);
        System.out.println("\nNOTE: For learning, connect to 'localhost' or '127.0.0.1'");
        System.out.println("      For network chat, use the server's IP address");
        System.out.println("      Example: java TCPChatClient 192.168.1.100 6001\n");

        System.out.print("Enter your username: ");
        String username = scanner.nextLine().trim();

        if (username.isEmpty()) {
            System.err.println("Username cannot be empty!");
            scanner.close();
            return;
        }

        try {
            TCPChatClient client = new TCPChatClient(serverHost, serverPort, username);

            client.connect();

            while (client.running.get()) {
                String message = scanner.nextLine();

                if (message.equalsIgnoreCase("/quit")) {
                    break;
                }

                client.sendChatMessage(message);
            }

            client.disconnect();

        } catch (ConnectException e) {
            System.err.println("Could not connect: " + e.getMessage());
            System.err.println("TIP: Make sure the server is running on " + serverHost + ":" + serverPort);
        } catch (UnknownHostException e) {
            System.err.println("Unknown host: " + e.getMessage());
            System.err.println("TIP: Use 'localhost' for local testing or verify the server address.");
        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
            System.err.println("TIP: Make sure the server is running on " + serverHost + ":" + serverPort);
        } finally {
            scanner.close();
        }

        System.out.println("Chat client closed.");
    }
}
