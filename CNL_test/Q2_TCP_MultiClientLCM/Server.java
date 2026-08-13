package CNL_test.Q2_TCP_MultiClientLCM;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class Server {
    private static final int PORT = 5002;
    private static final int NUM_CLIENTS = 3;

    private static final int[] numbers = new int[NUM_CLIENTS];
    private static long lcmResult;

    public static void main(String[] args) {
        CyclicBarrier barrier = new CyclicBarrier(NUM_CLIENTS, () -> {
            lcmResult = computeLCM(numbers);
            System.out.println("All numbers received: " + Arrays.toString(numbers));
            System.out.println("LCM computed: " + lcmResult);
        });

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT + ". Waiting for " + NUM_CLIENTS + " clients...");

            for (int i = 0; i < NUM_CLIENTS; i++) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client " + (i + 1) + " connected: " + clientSocket.getInetAddress());
                new Thread(new ClientHandler(clientSocket, i, barrier)).start();
            }

        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static long gcd(long a, long b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    private static long computeLCM(int[] nums) {
        long lcm = nums[0];
        for (int i = 1; i < nums.length; i++) {
            lcm = (lcm * nums[i]) / gcd(lcm, nums[i]);
        }
        return lcm;
    }

    static class ClientHandler implements Runnable {
        private final Socket socket;
        private final int index;
        private final CyclicBarrier barrier;

        ClientHandler(Socket socket, int index, CyclicBarrier barrier) {
            this.socket = socket;
            this.index = index;
            this.barrier = barrier;
        }

        @Override
        public void run() {
            try (DataInputStream in = new DataInputStream(socket.getInputStream());
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

                int number = in.readInt();
                numbers[index] = number;
                System.out.println("Received " + number + " from Client " + (index + 1));
                out.writeUTF("ACK: Received your number " + number);
                out.flush();
                barrier.await();
                out.writeLong(lcmResult);
                out.flush();
                System.out.println("Sent LCM to Client " + (index + 1));

            } catch (IOException | InterruptedException | BrokenBarrierException e) {
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    System.err.println("Error closing socket: " + e.getMessage());
                }
            }
        }
    }
}
