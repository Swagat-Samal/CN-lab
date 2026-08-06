package lab3_TCP;

import java.net.*;
import java.io.*;

public class TCPServer {
    private ServerSocket serverSocket;
    private boolean running;
    public TCPServer(int port) throws IOException {
        InetAddress localhost = InetAddress.getByName("127.0.0.1");
        serverSocket = new ServerSocket(port, 50, localhost);
        System.out.println("TCPServer started at " + localhost.getHostAddress() + ":" + port);
        System.out.println("NOTE: Server is on localhost only. Connection from other machines will be refused.");
    }
    public void run() {
        running = true;

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket);
                handler.start();
            } catch (IOException e) {
                if  (running) {
                    System.err.println("Error in server socket: " + e.getMessage());
                }
            }
        }
    }
    private static class ClientHandler extends Thread {
        private final Socket socket;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            String clientInfo = socket.getInetAddress() + ":" + socket.getPort();
            System.out.println("Client connected: " + clientInfo);

            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println("Received from " + clientInfo + " - " + line);
                    out.println("Echo: " + line);
                }
            } catch (IOException e) {
                System.err.println("Connection error with " + clientInfo + ": " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    System.err.println("Could not close connection with " + clientInfo + ": " + e.getMessage());
                }
                System.out.println("Client disconnected: " + clientInfo);
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
            System.err.println("Could not stop server: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        int port = 6000;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default port 6000");
            }
        }
        try {
            TCPServer server = new TCPServer(port);
            System.out.println("\n==== TCP Echo server ====");
            System.out.println("Server is on localhost only for security.");
            System.out.println("Client must connect to 127.0.0.1:" + port);
            System.out.println("Press Ctrl+F2 to stop the server.\n");

            server.run();
        } catch (IOException e) {
            System.err.println("Server Error - Could not start server: " + e.getMessage());
            System.err.println("TIP: Make sure port " + port + " is not already in use");
        }
    }
}
