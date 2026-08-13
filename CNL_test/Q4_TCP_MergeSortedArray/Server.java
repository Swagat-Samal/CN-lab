package CNL_test.Q4_TCP_MergeSortedArray;

import java.io.*;
import java.net.*;
import java.util.Arrays;

public class Server {
    private static final int PORT = 5004;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT + ". Waiting for pairs of clients...");

            while (true) {
                handleClientPair(serverSocket);
            }

        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleClientPair(ServerSocket serverSocket) throws IOException {
        System.out.println("Waiting for Client 1...");
        try (Socket socket1 = serverSocket.accept();
             DataInputStream in1 = new DataInputStream(socket1.getInputStream());
             DataOutputStream out1 = new DataOutputStream(socket1.getOutputStream())) {

            System.out.println("Client 1 connected: " + socket1.getInetAddress());
            System.out.println("Waiting for Client 2...");
            try (Socket socket2 = serverSocket.accept();
                 DataInputStream in2 = new DataInputStream(socket2.getInputStream());
                 DataOutputStream out2 = new DataOutputStream(socket2.getOutputStream())) {

                System.out.println("Client 2 connected: " + socket2.getInetAddress());
                int[] arr1 = readArray(in1);
                System.out.println("Received from Client 1: " + Arrays.toString(arr1));

                int[] arr2 = readArray(in2);
                System.out.println("Received from Client 2: " + Arrays.toString(arr2));

                int[] merged = mergeSorted(arr1, arr2);
                System.out.println("Merged sorted array: " + Arrays.toString(merged));
                sendArray(out1, merged);
                sendArray(out2, merged);
                System.out.println("Merged array sent to both clients.");
            }
        }
    }

    private static int[] readArray(DataInputStream in) throws IOException {
        int size = in.readInt();
        if (size < 0) {
            throw new IOException("Array size cannot be negative: " + size);
        }

        int[] arr = new int[size];
        for (int i = 0; i < size; i++) {
            arr[i] = in.readInt();
        }
        return arr;
    }

    private static int[] mergeSorted(int[] a, int[] b) {
        int[] copyA = Arrays.copyOf(a, a.length);
        int[] copyB = Arrays.copyOf(b, b.length);
        Arrays.sort(copyA);
        Arrays.sort(copyB);

        int[] merged = new int[copyA.length + copyB.length];
        int i = 0, j = 0, k = 0;

        while (i < copyA.length && j < copyB.length) {
            merged[k++] = (copyA[i] <= copyB[j]) ? copyA[i++] : copyB[j++];
        }
        while (i < copyA.length) merged[k++] = copyA[i++];
        while (j < copyB.length) merged[k++] = copyB[j++];

        return merged;
    }

    private static void sendArray(DataOutputStream out, int[] arr) throws IOException {
        out.writeInt(arr.length);
        for (int value : arr) {
            out.writeInt(value);
        }
        out.flush();
    }
}
