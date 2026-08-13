package CNL_test.Q4_TCP_MergeSortedArray;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.Scanner;

public class Client {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int PORT = 5004;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_ADDRESS, PORT);
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             Scanner sc = new Scanner(System.in)) {

            System.out.print("Enter array size: ");
            int size = sc.nextInt();
            int[] arr = new int[size];

            System.out.println("Enter " + size + " integers:");
            for (int i = 0; i < size; i++) {
                arr[i] = sc.nextInt();
            }

            out.writeInt(size);
            for (int value : arr) {
                out.writeInt(value);
            }
            out.flush();

            System.out.println("Array sent to server. Waiting for merged sorted array...");

            int mergedSize = in.readInt();
            int[] merged = new int[mergedSize];
            for (int i = 0; i < mergedSize; i++) {
                merged[i] = in.readInt();
            }

            System.out.println("Merged sorted array received: " + Arrays.toString(merged));

        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
