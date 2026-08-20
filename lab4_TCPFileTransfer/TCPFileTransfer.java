package lab4_TCPFileTransfer;

import java.net.*;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class TCPFileTransfer {

    private static final int BUFFER_SIZE = 8192;

    public static class FileSender {

        public void sendFile(String filePath, String receiverHost, int receiverPort)
                throws IOException, NoSuchAlgorithmException {
            File file = new File(filePath);
            if (!file.exists() || !file.isFile()) {
                throw new FileNotFoundException("File not found: " + filePath);
            }

            String fileHash = calculateFileHash(file);

            try (Socket socket = new Socket(receiverHost, receiverPort)) {

                if (socket.getInetAddress().isLoopbackAddress()) {
                    System.out.println("Sending to localhost - ideal for testing and learning!");
                } else {
                    System.out.println("Sending to remote host: " + receiverHost);
                    System.out.println("Ensure receiver is listening and firewall allows TCP port " + receiverPort);
                }

                DataOutputStream out = new DataOutputStream(
                        new BufferedOutputStream(socket.getOutputStream()));
                DataInputStream in = new DataInputStream(socket.getInputStream());

                out.writeUTF(file.getName());
                out.writeLong(file.length());
                out.writeUTF(fileHash);

                System.out.println("Sending file: " + file.getName());
                System.out.println("Size: " + file.length() + " bytes");
                System.out.println("Destination: " + socket.getInetAddress().getHostAddress() +
                        ":" + receiverPort);

                long totalSent = 0;
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        totalSent += bytesRead;

                        int progress = file.length() == 0 ? 100
                                : (int) ((totalSent / (double) file.length()) * 100);
                        System.out.print("\rProgress: " + progress + "%");
                    }
                }
                out.flush();

                System.out.println("\nWaiting for receiver confirmation...");

                String result = in.readUTF();
                if (result.equals("OK")) {
                    System.out.println("File sent and verified successfully!");
                } else {
                    System.err.println("Receiver reported a problem: " + result);
                }
            }
        }

        private String calculateFileHash(File file) throws IOException, NoSuchAlgorithmException {
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    md.update(buffer, 0, bytesRead);
                }
            }

            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
    }

    public static class FileReceiver {
        private ServerSocket serverSocket;

        public FileReceiver(int port) throws IOException {

            InetAddress localhost = InetAddress.getByName("127.0.0.1");
            this.serverSocket = new ServerSocket(port, 50, localhost);

            System.out.println("File receiver listening on " + localhost.getHostAddress() + ":" + port);
            System.out.println("NOTE: Only accepting connections from localhost.");
            System.out.println("For network transfers, modify code to bind to all interfaces.\n");
        }

        public void receiveFile(String outputDirectory) throws IOException, NoSuchAlgorithmException {
            System.out.println("Waiting for file transfer...");

            try (Socket socket = serverSocket.accept()) {

                InetAddress senderAddress = socket.getInetAddress();

                DataInputStream in = new DataInputStream(
                        new BufferedInputStream(socket.getInputStream()));
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                String fileName = in.readUTF();
                long fileSize = in.readLong();
                String expectedHash = in.readUTF();

                System.out.println("Receiving file: " + fileName);
                System.out.println("Expected size: " + fileSize + " bytes");
                System.out.println("From: " + senderAddress.getHostAddress() + ":" + socket.getPort());

                if (senderAddress.isLoopbackAddress()) {
                    System.out.println("Source: Local transfer");
                } else {
                    System.out.println("Source: Network transfer from " + senderAddress.getHostAddress());
                }

                File outputDir = new File(outputDirectory);
                if (!outputDir.exists()) {
                    outputDir.mkdirs();
                }
                File outputFile = new File(outputDir, new File(fileName).getName());

                MessageDigest md = MessageDigest.getInstance("MD5");
                long totalReceived = 0;

                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    while (totalReceived < fileSize) {
                        int toRead = (int) Math.min(buffer.length, fileSize - totalReceived);
                        int bytesRead = in.read(buffer, 0, toRead);
                        if (bytesRead == -1) {
                            throw new IOException("Connection closed before transfer completed");
                        }

                        fos.write(buffer, 0, bytesRead);
                        md.update(buffer, 0, bytesRead);
                        totalReceived += bytesRead;

                        int progress = fileSize == 0 ? 100
                                : (int) ((totalReceived / (double) fileSize) * 100);
                        System.out.print("\rProgress: " + progress + "%");
                    }
                }

                System.out.println("\nVerifying file integrity...");

                String receivedHash = toHex(md.digest());
                if (receivedHash.equals(expectedHash)) {
                    System.out.println("File saved: " + outputFile.getAbsolutePath());
                    System.out.println("File integrity verified!");
                    out.writeUTF("OK");
                } else {
                    System.err.println("File integrity check failed!");
                    out.writeUTF("HASH_MISMATCH");
                }
                out.flush();
            }
        }

        private String toHex(byte[] digest) {
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }

        public void close() {
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
            } catch (IOException e) {
            }
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            return;
        }

        String mode = args[0].toLowerCase();

        try {
            if (mode.equals("send")) {
                if (args.length < 4) {
                    printUsage();
                    return;
                }

                String filePath = args[1];
                String receiverHost = args[2];
                int receiverPort = Integer.parseInt(args[3]);

                System.out.println("\n=== TCP File Transfer - Sender Mode ===");
                System.out.println("For local testing: use 'localhost' or '127.0.0.1' as receiver host");
                System.out.println("For network transfer: use receiver's IP address and ensure firewall allows TCP\n");

                FileSender sender = new FileSender();
                sender.sendFile(filePath, receiverHost, receiverPort);

            } else if (mode.equals("receive")) {
                if (args.length < 3) {
                    printUsage();
                    return;
                }

                int port = Integer.parseInt(args[1]);
                String outputDir = args[2];

                System.out.println("\n=== TCP File Transfer - Receiver Mode ===");
                System.out.println("Receiver is bound to localhost for security.");
                System.out.println("Only local senders can transfer files to this receiver.");
                System.out.println("For network transfers, modify the code to bind to all interfaces.\n");

                FileReceiver receiver = new FileReceiver(port);
                receiver.receiveFile(outputDir);
                receiver.close();

            } else {
                printUsage();
            }
        } catch (ConnectException e) {
            System.err.println("Could not connect: " + e.getMessage());
            System.err.println("TIP: Make sure the receiver is running first.");
        } catch (BindException e) {
            System.err.println("Socket error: " + e.getMessage());
            System.err.println("TIP: Port is already in use. Try a different port number.");
        } catch (UnknownHostException e) {
            System.err.println("Unknown host: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("IO error: " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            System.err.println("Algorithm error: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  Send mode:    java TCPFileTransfer send <file_path> <receiver_host> <receiver_port>");
        System.out.println("  Receive mode: java TCPFileTransfer receive <listen_port> <output_directory>");
        System.out.println();
        System.out.println("Example (local transfer - recommended for learning):");
        System.out.println("  Terminal 1: java TCPFileTransfer receive 6002 ./received/");
        System.out.println("  Terminal 2: java TCPFileTransfer send myfile.pdf localhost 6002");
        System.out.println();
        System.out.println("Example (network transfer):");
        System.out.println("  Receiver: java TCPFileTransfer receive 6002 ./received/");
        System.out.println("  Sender:   java TCPFileTransfer send myfile.pdf 192.168.1.100 6002");
    }
}
