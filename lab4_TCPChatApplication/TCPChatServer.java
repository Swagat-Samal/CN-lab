package lab4_TCPChatApplication;

import java.net.*;
import java.io.*;
import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TCPChatServer {
    private ServerSocket serverSocket;
    private boolean running;

    private final Map<String, ClientHandler> clients = new HashMap<>();

    public TCPChatServer(int port) throws IOException {

        InetAddress localhost = InetAddress.getByName("127.0.0.1");
        serverSocket = new ServerSocket(port, 50, localhost);

        System.out.println("TCP Chat Server started on " + localhost.getHostAddress() + ":" + port);
        System.out.println("IMPORTANT: Server is bound to localhost only.");
        System.out.println("Only local clients can connect. For network-wide access, modify binding address.");
        System.out.println("----------------------------------------");

    }

    public void run() {
        running = true;

        while (running) {
            try {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket);
                handler.start();

            } catch (IOException e) {
                if (running) {
                    System.err.println("Error in server: " + e.getMessage());
                }
            }
        }
    }

    private class ClientHandler extends Thread {
        private final Socket socket;
        private PrintWriter out;
        private String username;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                String line;
                while ((line = in.readLine()) != null) {
                    if (line.equals("LEAVE")) {
                        break;
                    }
                    processMessage(line);
                }
            } catch (IOException e) {
            } finally {
                handleDisconnect(this);
                try {
                    socket.close();
                } catch (IOException e) {

                }
            }
        }

        private void processMessage(String message) {
            if (message.startsWith("JOIN:")) {
                handleJoin(this, message.substring(5));
            } else if (message.startsWith("MSG:")) {
                handleMessage(this, message.substring(4));
            } else if (message.equals("LIST")) {
                handleList(this);
            }
        }

        void send(String message) {
            if (out != null) {
                out.println(message);
            }
        }
    }

    private synchronized void handleJoin(ClientHandler handler, String username) {
        handler.username = username;
        clients.put(username, handler);

        String joinMessage = "SYSTEM: " + username + " has joined the chat!";
        broadcastMessage(joinMessage, handler);

        handler.send("SYSTEM: Welcome to the chat, " + username + "! Type '/list' to see online users.");

        InetAddress address = handler.socket.getInetAddress();
        String connectionInfo = address.isLoopbackAddress() ? " (local connection)" : " (external connection)";
        System.out.println("Client joined: " + username + " from " + address +
                ":" + handler.socket.getPort() + connectionInfo);
    }

    private synchronized void handleMessage(ClientHandler sender, String message) {
        if (sender.username == null) {
            sender.send("SYSTEM: Please JOIN before sending messages.");
            return;
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String formattedMessage = "[" + timestamp + "] " + sender.username + ": " + message;

        broadcastMessage(formattedMessage, null);
        System.out.println(formattedMessage);
    }

    private synchronized void handleList(ClientHandler requester) {
        StringBuilder sb = new StringBuilder("SYSTEM: Online users:");
        for (String name : clients.keySet()) {
            sb.append(" ").append(name);
        }
        requester.send(sb.toString());
    }

    private synchronized void handleDisconnect(ClientHandler handler) {
        if (handler.username != null && clients.remove(handler.username) != null) {
            String leaveMessage = "SYSTEM: " + handler.username + " has left the chat.";
            broadcastMessage(leaveMessage, handler);
            System.out.println("Client left: " + handler.username);
        }
    }

    private synchronized void broadcastMessage(String message, ClientHandler exclude) {
        for (ClientHandler client : clients.values()) {
            if (client != exclude) {
                client.send(message);
            }
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
        }
    }

    public static void main(String[] args) {
        int port = 6001;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default port 6001.");
            }
        }

        System.out.println("\n=== TCP Chat Server ===");
        System.out.println("Starting server on localhost for safe learning environment...");
        System.out.println("To allow connections from other machines:");
        System.out.println("1. Modify the code to bind to 0.0.0.0 or your public IP");
        System.out.println("2. Configure your firewall to allow TCP port " + port);
        System.out.println("3. Share your public IP with clients\n");

        try {
            TCPChatServer server = new TCPChatServer(port);
            System.out.println("Server is ready for connections!");
            System.out.println("Press Ctrl+f2 to stop the server.\n");
            server.run();
        } catch (IOException e) {
            System.err.println("Could not start server: " + e.getMessage());
            System.err.println("TIP: Make sure port " + port + " is not already in use.");
        }
    }
}
