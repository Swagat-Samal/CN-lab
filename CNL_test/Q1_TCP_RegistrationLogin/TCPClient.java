package CNL_test.Q1_TCP_RegistrationLogin;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class TCPClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int PORT = 5001;
    private static final int TOTAL_USERS = 5;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_ADDRESS, PORT);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
             Scanner sc = new Scanner(System.in)) {

            System.out.println("Connected to server. Enter details for " + TOTAL_USERS + " users.\n");

            for (int i = 0; i < TOTAL_USERS; i++) {
                System.out.println("--- User " + (i + 1) + " ---");
                System.out.print("Name: ");
                String name = sc.nextLine();
                System.out.print("DOB (dd-mm-yyyy): ");
                String dob = sc.nextLine();
                System.out.print("Username: ");
                String userId = sc.nextLine();
                System.out.print("Password: ");
                String password = sc.nextLine();

                User user = new User(name, dob, userId, password);
                out.writeObject(user);
                out.flush();

                String ack = (String) in.readObject();
                System.out.println("Server: " + ack + "\n");
            }

            String choice;
            do {
                System.out.println("--- Login ---");
                System.out.print("UserId: ");
                String userId = sc.nextLine();
                System.out.print("Password: ");
                String password = sc.nextLine();

                User loginAttempt = new User(null, null, userId, password);
                out.writeObject(loginAttempt);
                out.flush();

                String result = (String) in.readObject();
                System.out.println("Server: " + result + "\n");

                System.out.print("Try another login? (y/n): ");
                choice = sc.nextLine();
            } while (choice.equalsIgnoreCase("y"));

            out.writeObject("EXIT");
            out.flush();

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Client error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
