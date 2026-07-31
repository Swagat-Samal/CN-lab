package lab2_UDPChatApplication;

import java.net.*;
import java.io.*;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class UDPChatClient {
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int serverPort;
    private String username;
    private AtomicBoolean running = new AtomicBoolean(false);
    private Thread receiveThread;
    private Thread pingThread;

    private class ReceiveThread extends Thread {
        public void run() {
            byte[] buffer = new byte[1024];

            while (running.get()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength());

                    if (!message.equals("PONG")) {
                        System.out.println(message);
                    }

                } catch (SocketTimeoutException e) {
                } catch (IOException e) {
                    if (running.get()) {
                        System.err.println("Error receiving message: " + e.getMessage());
                    }
                }
            }
        }
    }

    private class PingThread extends Thread {
        public void run() {
            while (running.get()) {
                try {
                    Thread.sleep(15000);
                    if (running.get()) {
                        sendMessage("PING");
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (IOException e) {
                    System.err.println("Error sending ping: " + e.getMessage());
                }
            }
        }
    }

    public UDPChatClient(String serverHost, int serverPort, String username)
            throws SocketException, UnknownHostException {
        this.socket = new DatagramSocket();

        this.serverAddress = InetAddress.getByName(serverHost);
        this.serverPort = serverPort;
        this.username = username;

        socket.setSoTimeout(1000);
        if (this.serverAddress.isLoopbackAddress()) {
            System.out.println("Connecting to local server (localhost)");
        } else {
            System.out.println("Connecting to remote server at " + serverHost);
            System.out.println("Make sure the server allows external connections and firewall permits UDP port " + serverPort);
        }
    }

    public void connect() throws IOException {

        String joinMessage = "JOIN:" + username;
        sendMessage(joinMessage);


        running.set(true);
        startReceiveThread();
        startPingThread();

        System.out.println("Connected to chat server as: " + username);
        System.out.println("Server: " + serverAddress.getHostAddress() + ":" + serverPort);
        System.out.println("\nCommands: /list (show users), /quit (exit), or just type to chat");
    }

    private void startReceiveThread() {
        receiveThread = new ReceiveThread();
        receiveThread.start();
    }

    private void startPingThread() {
        pingThread = new PingThread();
        pingThread.setDaemon(true);
        pingThread.start();
    }

    private void sendMessage(String message) throws IOException {
        byte[] buffer = message.getBytes();
        DatagramPacket packet = new DatagramPacket(
                buffer,
                buffer.length,
                serverAddress,
                serverPort
        );
        socket.send(packet);
    }

    public void sendChatMessage(String message) throws IOException {
        if (message.trim().isEmpty()) {
            return;
        }

        if (message.equalsIgnoreCase("/list")) {
            sendMessage("LIST");
        } else if (message.equalsIgnoreCase("/quit")) {
            disconnect();
        } else {
            sendMessage("MSG:" + message);
        }
    }

    public void disconnect() {
        if (running.get()) {
            running.set(false);

            try {
                sendMessage("LEAVE");
            } catch (IOException e) {

            }


            try {
                if (receiveThread != null) {
                    receiveThread.join(2000);
                }
                if (pingThread != null) {
                    pingThread.interrupt();
                    pingThread.join(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    public static void main(String[] args) {
        String serverHost = "localhost";
        int serverPort = 5001;

        Scanner scanner = new Scanner(System.in);
        if (args.length >= 1) {
            serverHost = args[0];
        }
        if (args.length >= 2) {
            try {
                serverPort = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default port 5001.");
            }
        }

        System.out.println("\n=== UDP Chat Client ===");
        System.out.println("Default server: " + serverHost + ":" + serverPort);
        System.out.println("\nNOTE: For learning, connect to 'localhost' or '127.0.0.1'");
        System.out.println("        For network chat, use the server's IP address");
        System.out.println("        Example: java UDPChatClient 192.168.1.100 5001\n");


        System.out.print("Enter your username: ");
        String username = scanner.nextLine().trim();

        if (username.isEmpty()) {
            System.err.println("Username cannot be empty!");
            scanner.close();
            return;
        }

        try {
            UDPChatClient client = new UDPChatClient(serverHost, serverPort, username);


            client.connect();

            while (true) {
                String message = scanner.nextLine();

                if (message.equalsIgnoreCase("/quit")) {
                    break;
                }

                try {
                    client.sendChatMessage(message);
                } catch (IOException e) {
                    System.err.println("Error sending message: " + e.getMessage());
                    if (!serverHost.equals("localhost") && !serverHost.equals("127.0.0.1")) {
                        System.err.println("TIP: Check if the server is reachable and firewall allows UDP traffic.");
                    }
                }
            }

            client.disconnect();

        } catch (SocketException e) {
            System.err.println("Could not create client: " + e.getMessage());
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
