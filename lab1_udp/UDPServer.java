package lab1_udp;

import java.net.*;
import java.io.*;

public class UDPServer {
    private DatagramSocket socket;
    private boolean running;
    private byte[] buffer = new byte[256];

    public UDPServer(int port) throws SocketException, UnknownHostException {
        InetAddress localhost = InetAddress.getByName("127.0.0.1");
        socket = new DatagramSocket(port, localhost);

        System.out.println("UDP Server started on " + localhost.getHostAddress() + ":" + port);
        System.out.println("NOTE: Server is bound to localhost only. Connections from other machines will be refused.");
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

                System.out.println("Received from " + clientAddress + ":" + clientPort + " - " + received);

                String response = "Echo: " + received;
                byte[] responseData = response.getBytes();
                DatagramPacket responsePacket = new DatagramPacket(
                        responseData,
                        responseData.length,
                        clientAddress,
                        clientPort
                );
                socket.send(responsePacket);
                buffer = new byte[256];

            } catch (IOException e) {
                System.err.println("Error in server: " + e.getMessage());
            }
        }
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    public static void main(String[] args) {
        int port = 5000;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default port 5000.");
            }
        }

        try {
            UDPServer server = new UDPServer(port);

            System.out.println("\n=== UDP Echo Server ===");
            System.out.println("Server is running on localhost only for security.");
            System.out.println("Clients must connect to 127.0.0.1:" + port);
            System.out.println("Press Ctrl+F2 to stop the server.\n");

            server.run();
        } catch (SocketException e) {
            System.err.println("Could not start server: " + e.getMessage());
        } catch (UnknownHostException e) {
            System.err.println("Could not bind to localhost: " + e.getMessage());
        }
    }
}
