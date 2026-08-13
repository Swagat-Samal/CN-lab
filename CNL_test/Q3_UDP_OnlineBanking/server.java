package CNL_test.Q3_UDP_OnlineBanking;

import java.io.*;
import java.net.*;
import java.util.*;

class UDPServer {
    private static final int PORT = 6003;
    private static final int TOTAL_USERS = 5;
    private static final int BUFFER_SIZE = 4096;

    public static void main(String[] args) {
        List<Account> registeredAccounts = new ArrayList<>();

        try (DatagramSocket socket = new DatagramSocket(PORT)) {
            System.out.println("UDP Banking Server started on port " + PORT + "...");

            byte[] buffer = new byte[BUFFER_SIZE];

            for (int i = 0; i < TOTAL_USERS; i++) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                Account account = (Account) deserialize(packet.getData(), packet.getLength());
                registeredAccounts.add(account);
                System.out.println("Registered: " + account);

                String ackMsg = "User " + (i + 1) + " registered successfully.";
                sendMessage(socket, ackMsg, packet.getAddress(), packet.getPort());
            }

            System.out.println("\nAll 5 users registered. Waiting for login/transfer requests...\n");

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                Object obj = deserialize(packet.getData(), packet.getLength());

                if (obj instanceof String && obj.equals("EXIT")) {
                    System.out.println("Client requested to close.");
                    break;
                }

                Account loginAttempt = (Account) obj;
                boolean matched = false;

                for (Account a : registeredAccounts) {
                    if (a.getUserId().equals(loginAttempt.getUserId()) &&
                            a.getPassword().equals(loginAttempt.getPassword())) {
                        matched = true;
                        break;
                    }
                }

                String result = matched
                        ? "Log In Successful and now you can transfer"
                        : "Log In Unsuccessful";

                System.out.println("Login attempt for '" + loginAttempt.getUserId() + "': " + (matched ? "SUCCESS" : "FAILED"));
                sendMessage(socket, result, packet.getAddress(), packet.getPort());
            }

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("Server shutting down.");
    }

    private static void sendMessage(DatagramSocket socket, String message, InetAddress address, int port) throws IOException {
        byte[] data = serialize(message);
        DatagramPacket sendPacket = new DatagramPacket(data, data.length, address, port);
        socket.send(sendPacket);
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

    public static class Account implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String accountNo;
        private final String name;
        private final String type;
        private final String userId;
        private final String password;

        public Account(String accountNo, String name, String type, String userId, String password) {
            this.accountNo = accountNo;
            this.name = name;
            this.type = type;
            this.userId = userId;
            this.password = password;
        }

        public String getUserId() {
            return userId;
        }

        public String getPassword() {
            return password;
        }

        @Override
        public String toString() {
            return "Account{accNo='" + accountNo + "', name='" + name
                    + "', type='" + type + "', userId='" + userId + "'}";
        }
    }
}
