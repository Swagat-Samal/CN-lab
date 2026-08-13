package CNL_test.Q3_UDP_OnlineBanking;

import java.io.*;
import java.net.*;
import java.util.Scanner;

class UDPClient {
    private static final String SERVER_HOST = "localhost";
    private static final int PORT = 6003;
    private static final int TOTAL_USERS = 5;
    private static final int BUFFER_SIZE = 4096;

    public static void main(String[] args) {
        try (DatagramSocket socket = new DatagramSocket();
             Scanner sc = new Scanner(System.in)) {

            InetAddress serverAddress = InetAddress.getByName(SERVER_HOST);
            byte[] buffer = new byte[BUFFER_SIZE];

            System.out.println("Enter details for " + TOTAL_USERS + " users.\n");

            for (int i = 0; i < TOTAL_USERS; i++) {
                System.out.println("--- User " + (i + 1) + " ---");
                System.out.print("Account_No: ");
                String accNo = sc.nextLine();
                System.out.print("Name: ");
                String name = sc.nextLine();
                System.out.print("Type (saving/current): ");
                String type = sc.nextLine();
                System.out.print("UserId: ");
                String userId = sc.nextLine();
                System.out.print("Password: ");
                String password = sc.nextLine();

                UDPServer.Account account = new UDPServer.Account(accNo, name, type, userId, password);
                sendObject(socket, account, serverAddress, PORT);

                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String ack = (String) deserialize(packet.getData(), packet.getLength());
                System.out.println("Server: " + ack + "\n");
            }

            String choice;
            do {
                System.out.println("--- Login for Transfer ---");
                System.out.print("UserId: ");
                String userId = sc.nextLine();
                System.out.print("Password: ");
                String password = sc.nextLine();

                UDPServer.Account loginAttempt = new UDPServer.Account(null, null, null, userId, password);
                sendObject(socket, loginAttempt, serverAddress, PORT);

                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String result = (String) deserialize(packet.getData(), packet.getLength());
                System.out.println("Server: " + result + "\n");

                System.out.print("Try another login? (y/n): ");
                choice = sc.nextLine();
            } while (choice.equalsIgnoreCase("y"));

            sendObject(socket, "EXIT", serverAddress, PORT);

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Client error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void sendObject(DatagramSocket socket, Object obj, InetAddress address, int port) throws IOException {
        byte[] data = serialize(obj);
        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
        socket.send(packet);
    }

    private static byte[] serialize(Object obj) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
            oos.flush();
            return bos.toByteArray();
        }
    }

    private static Object deserialize(byte[] data, int length) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data, 0, length);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return ois.readObject();
        }
    }
}
