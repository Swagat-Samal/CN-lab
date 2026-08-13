package CNL_test.Q2_TCP_MultiClientLCM;

import java.io.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.Scanner;

public class client {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int PORT = 5002;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_ADDRESS, PORT);
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             Scanner sc = new Scanner(System.in)) {

            System.out.print("Enter an integer: ");
            int number = sc.nextInt();

            out.writeInt(number);
            out.flush();

            String ack = in.readUTF();
            System.out.println("Server: " + ack);

            System.out.println("Waiting for the other clients to send their numbers...");
            long lcm = in.readLong();
            System.out.println("LCM of all 3 numbers: " + lcm);

        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
