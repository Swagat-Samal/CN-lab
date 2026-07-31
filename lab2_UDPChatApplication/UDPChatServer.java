package lab2_UDPChatApplication;

import java.net.*;
import java.io.*;
import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class UDPChatServer {
    private DatagramSocket socket;
    private boolean running;
    private byte[] buffer = new byte[1024];

    private Map<String, ClientInfo> clients = new HashMap<>();
    private static final long CLIENT_TIMEOUT = 30000;

    private static class ClientInfo {
        InetAddress address;
        int port;
        String username;
        long lastSeen;

        ClientInfo(InetAddress address, int port, String username) {
            this.address = address;
            this.port = port;
            this.username = username;
            this.lastSeen = System.currentTimeMillis();
        }

        void updateLastSeen() {
            this.lastSeen = System.currentTimeMillis();
        }

        boolean isActive() {
            return (System.currentTimeMillis() - lastSeen) < CLIENT_TIMEOUT;
        }
    }

    private class CleanupThread extends Thread {
        public void run() {
            while (running) {
                try {
                    Thread.sleep(10000);
                    removeInactiveClients();
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    public UDPChatServer(int port) throws SocketException, UnknownHostException {

        InetAddress localhost = InetAddress.getByName("127.0.0.1");
        socket = new DatagramSocket(port, localhost);

        System.out.println("UDP Chat Server started on " + localhost.getHostAddress() + ":" + port);
        System.out.println("IMPORTANT: Server is bound to localhost only.");
        System.out.println("Only local clients can connect. For network-wide access, modify binding address.");
        System.out.println("----------------------------------------");

        startCleanupThread();
    }

    private void startCleanupThread() {
        CleanupThread cleanupThread = new CleanupThread();
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    private synchronized void removeInactiveClients() {
        Iterator<Map.Entry<String, ClientInfo>> iterator = clients.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ClientInfo> entry = iterator.next();
            if (!entry.getValue().isActive()) {
                String message = "SYSTEM: " + entry.getValue().username + " has left the chat (timeout)";
                broadcastMessage(message, null);
                iterator.remove();
            }
        }
    }

    public void run() {
        running = true;

        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                InetAddress clientAddress = packet.getAddress();
                int clientPort = packet.getPort();
                String received = new String(packet.getData(), 0, packet.getLength());

                processMessage(received, clientAddress, clientPort);

                buffer = new byte[1024];
            } catch (IOException e) {
                if (running) {
                    System.err.println("Error in server: " + e.getMessage());
                }
            }
        }
    }

    private synchronized void processMessage(String message, InetAddress address, int port) {
        String clientKey = address.toString() + ":" + port;

        if (message.startsWith("JOIN:")) {
            handleJoin(message.substring(5), address, port, clientKey);
        } else if (message.startsWith("MSG:")) {
            handleMessage(message.substring(4), clientKey);
        } else if (message.equals("LEAVE")) {
            handleLeave(clientKey);
        } else if (message.equals("LIST")) {
            handleList(address, port);
        } else if (message.equals("PING")) {
            handlePing(clientKey);
        }
    }

    private void handleJoin(String username, InetAddress address, int port, String clientKey) {
        ClientInfo client = new ClientInfo(address, port, username);
        clients.put(clientKey, client);

        String joinMessage = "SYSTEM: " + username + " has joined the chat!";
        broadcastMessage(joinMessage, clientKey);

        sendToClient("SYSTEM: Welcome to the chat, " + username + "! Type '/list' to see online users.", address, port);

        String connectionInfo = address.isLoopbackAddress() ? " (local connection)" : " (external connection)";
        System.out.println("Client joined: " + username + " from " + address + ":" + port + connectionInfo);
    }

    private void handleMessage(String message, String senderKey) {
        ClientInfo sender = clients.get(senderKey);
        if (sender != null) {
            sender.updateLastSeen();

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String formattedMessage = "[" + timestamp + "] " + sender.username + ": " + message;

            broadcastMessage(formattedMessage, null);
            System.out.println(formattedMessage);
        }
    }

    private void handleLeave(String clientKey) {
        ClientInfo client = clients.remove(clientKey);
        if (client != null) {
            String leaveMessage = "SYSTEM: " + client.username + " has left the chat.";
            broadcastMessage(leaveMessage, clientKey);
            System.out.println("Client left: " + client.username);
        }
    }

    private void handleList(InetAddress address, int port) {
        StringBuilder sb = new StringBuilder("SYSTEM: Online users:\n");
        for (ClientInfo client : clients.values()) {
            if (client.isActive()) {
                sb.append("  - ").append(client.username).append("\n");
            }
        }
        sendToClient(sb.toString(), address, port);
    }

    private void handlePing(String clientKey) {
        ClientInfo client = clients.get(clientKey);
        if (client != null) {
            client.updateLastSeen();
            sendToClient("PONG", client.address, client.port);
        }
    }

    private synchronized void broadcastMessage(String message, String excludeKey) {
        byte[] messageData = message.getBytes();

        for (Map.Entry<String, ClientInfo> entry : clients.entrySet()) {
            if (!entry.getKey().equals(excludeKey)) {
                ClientInfo client = entry.getValue();
                sendToClient(message, client.address, client.port);
            }
        }
    }

    private void sendToClient(String message, InetAddress address, int port) {
        try {
            byte[] messageData = message.getBytes();
            DatagramPacket packet = new DatagramPacket(
                    messageData,
                    messageData.length,
                    address,
                    port
            );
            socket.send(packet);
        } catch (IOException e) {
            System.err.println("Error sending to client: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    public static void main(String[] args) {
        int port = 5001;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default port 5001.");
            }
        }

        System.out.println("\n=== UDP Chat Server ===");
        System.out.println("Starting server on localhost for safe learning environment...");
        System.out.println("To allow connections from other machines:");
        System.out.println("1. Modify the code to bind to 0.0.0.0 or your public IP");
        System.out.println("2. Configure your firewall to allow UDP port " + port);
        System.out.println("3. Share your public IP with clients\n");

        try {
            UDPChatServer server = new UDPChatServer(port);
            System.out.println("Server is ready for connections!");
            System.out.println("Press Ctrl+F2 to stop the server.\n");
            server.run();
        } catch (SocketException e) {
            System.err.println("Could not start server: " + e.getMessage());
            System.err.println("TIP: Make sure port " + port + " is not already in use.");
        } catch (UnknownHostException e) {
            System.err.println("Could not bind to localhost: " + e.getMessage());
        }
    }
}
