package lab2_UDPFileTransfer;

import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class UDPFileTransfer {

    private static final byte TYPE_FILE_INFO = 1;
    private static final byte TYPE_DATA = 2;
    private static final byte TYPE_ACK = 3;
    private static final byte TYPE_COMPLETE = 4;
    private static final byte TYPE_ERROR = 5;

    private static final int MAX_PACKET_SIZE = 1400;
    private static final int HEADER_SIZE = 9;
    private static final int MAX_DATA_SIZE = MAX_PACKET_SIZE - HEADER_SIZE;
    private static final int TIMEOUT_MS = 1000;
    private static final int MAX_RETRIES = 3;


    public static class FileSender {
        private DatagramSocket socket;
        private InetAddress receiverAddress;
        private int receiverPort;

        public FileSender() throws SocketException {
            this.socket = new DatagramSocket();
            socket.setSoTimeout(TIMEOUT_MS);
        }

        public void sendFile(String filePath, String receiverHost, int receiverPort)
                throws IOException, NoSuchAlgorithmException {

            this.receiverAddress = InetAddress.getByName(receiverHost);
            this.receiverPort = receiverPort;

            if (this.receiverAddress.isLoopbackAddress()) {
                System.out.println("Sending to localhost - ideal for testing and learning!");
            } else {
                System.out.println("Sending to remote host: " + receiverHost);
                System.out.println("Ensure receiver is listening and firewall allows UDP port " + receiverPort);
            }

            File file = new File(filePath);
            if (!file.exists() || !file.isFile()) {
                throw new FileNotFoundException("File not found: " + filePath);
            }

            String fileHash = calculateFileHash(file);

            sendFileInfo(file.getName(), file.length(), fileHash);

            FileInputStream fis = null;
            try {
                fis = new FileInputStream(file);
                byte[] fileData = new byte[(int) file.length()];
                fis.read(fileData);

                int totalPackets = (int) Math.ceil((double) fileData.length / MAX_DATA_SIZE);

                System.out.println("Sending file: " + file.getName());
                System.out.println("Size: " + file.length() + " bytes");
                System.out.println("Packets: " + totalPackets);
                System.out.println("Destination: " + receiverAddress.getHostAddress() + ":" + receiverPort);

                for (int i = 0; i < totalPackets; i++) {
                    int offset = i * MAX_DATA_SIZE;
                    int length = Math.min(MAX_DATA_SIZE, fileData.length - offset);

                    byte[] packetData = new byte[length];
                    System.arraycopy(fileData, offset, packetData, 0, length);

                    sendDataPacket(i, totalPackets, packetData);

                    int progress = (int) (((i + 1) / (double) totalPackets) * 100);
                    System.out.print("\rProgress: " + progress + "%");
                }

                System.out.println("\nFile sent successfully!");

                sendCompletionPacket();
            } finally {
                if (fis != null) {
                    fis.close();
                }
            }
        }

        private void sendFileInfo(String fileName, long fileSize, String hash)
                throws IOException {
            String info = fileName + "|" + fileSize + "|" + hash;
            byte[] infoBytes = info.getBytes();

            ByteBuffer buffer = ByteBuffer.allocate(1 + infoBytes.length);
            buffer.put(TYPE_FILE_INFO);
            buffer.put(infoBytes);

            sendPacketWithRetry(buffer.array());
        }

        private void sendDataPacket(int sequence, int total, byte[] data)
                throws IOException {
            ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + data.length);
            buffer.put(TYPE_DATA);
            buffer.putInt(sequence);
            buffer.putInt(total);
            buffer.put(data);

            sendPacketWithRetry(buffer.array());
        }

        private void sendCompletionPacket() throws IOException {
            ByteBuffer buffer = ByteBuffer.allocate(1);
            buffer.put(TYPE_COMPLETE);

            sendPacketWithRetry(buffer.array());
        }

        private void sendPacketWithRetry(byte[] data) throws IOException {
            DatagramPacket packet = new DatagramPacket(
                    data, data.length, receiverAddress, receiverPort
            );

            for (int retry = 0; retry < MAX_RETRIES; retry++) {
                socket.send(packet);

                byte[] ackBuffer = new byte[1];
                DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);

                try {
                    socket.receive(ackPacket);
                    if (ackBuffer[0] == TYPE_ACK) {
                        return;
                    }
                } catch (SocketTimeoutException e) {
                    if (retry == MAX_RETRIES - 1) {
                        throw new IOException("Failed to receive ACK after " + MAX_RETRIES + " retries");
                    }
                    System.out.print(".");
                }
            }
        }

        private String calculateFileHash(File file) throws IOException, NoSuchAlgorithmException {
            MessageDigest md = MessageDigest.getInstance("MD5");
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(file);
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    md.update(buffer, 0, bytesRead);
                }
            } finally {
                if (fis != null) {
                    fis.close();
                }
            }

            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }

        public void close() {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    public static class FileReceiver {
        private DatagramSocket socket;
        private Map<Integer, byte[]> receivedPackets = new HashMap<>();
        private String expectedFileName;
        private long expectedFileSize;
        private String expectedHash;
        private int totalExpectedPackets;

        public FileReceiver(int port) throws SocketException, UnknownHostException {

            InetAddress localhost = InetAddress.getByName("127.0.0.1");
            this.socket = new DatagramSocket(port, localhost);

            System.out.println("File receiver listening on " + localhost.getHostAddress() + ":" + port);
            System.out.println("NOTE: Only accepting connections from localhost.");
            System.out.println("For network transfers, modify code to bind to all interfaces.\n");
        }

        public void receiveFile(String outputDirectory) throws IOException, NoSuchAlgorithmException {
            byte[] buffer = new byte[MAX_PACKET_SIZE];
            boolean receiving = true;

            System.out.println("Waiting for file transfer...");

            while (receiving) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                InetAddress senderAddress = packet.getAddress();
                int senderPort = packet.getPort();

                ByteBuffer data = ByteBuffer.wrap(packet.getData(), 0, packet.getLength());
                byte packetType = data.get();

                switch (packetType) {
                    case TYPE_FILE_INFO:
                        handleFileInfo(data, senderAddress, senderPort);
                        break;

                    case TYPE_DATA:
                        handleDataPacket(data, senderAddress, senderPort);
                        break;

                    case TYPE_COMPLETE:
                        receiving = false;
                        sendAck(senderAddress, senderPort);
                        saveFile(outputDirectory);
                        break;

                    default:
                        System.err.println("Unknown packet type: " + packetType);
                }
            }
        }

        private void handleFileInfo(ByteBuffer data, InetAddress sender, int port)
                throws IOException {
            byte[] infoBytes = new byte[data.remaining()];
            data.get(infoBytes);

            String info = new String(infoBytes);
            String[] parts = info.split("\\|");

            expectedFileName = parts[0];
            expectedFileSize = Long.parseLong(parts[1]);
            expectedHash = parts[2];

            System.out.println("Receiving file: " + expectedFileName);
            System.out.println("Expected size: " + expectedFileSize + " bytes");
            System.out.println("From: " + sender.getHostAddress() + ":" + port);

            if (sender.isLoopbackAddress()) {
                System.out.println("Source: Local transfer");
            } else {
                System.out.println("Source: Network transfer from " + sender.getHostAddress());
            }

            sendAck(sender, port);
        }

        private void handleDataPacket(ByteBuffer data, InetAddress sender, int port)
                throws IOException {
            int sequence = data.getInt();
            int total = data.getInt();

            if (totalExpectedPackets == 0) {
                totalExpectedPackets = total;
            }

            byte[] packetData = new byte[data.remaining()];
            data.get(packetData);

            receivedPackets.put(sequence, packetData);

            int progress = (int) ((receivedPackets.size() / (double) totalExpectedPackets) * 100);
            System.out.print("\rProgress: " + progress + "%");

            sendAck(sender, port);
        }

        private void sendAck(InetAddress address, int port) throws IOException {
            byte[] ack = {TYPE_ACK};
            DatagramPacket packet = new DatagramPacket(ack, ack.length, address, port);
            socket.send(packet);
        }

        private void saveFile(String outputDirectory) throws IOException, NoSuchAlgorithmException {
            System.out.println("\nSaving file...");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (int i = 0; i < totalExpectedPackets; i++) {
                byte[] packetData = receivedPackets.get(i);
                if (packetData == null) {
                    throw new IOException("Missing packet: " + i);
                }
                baos.write(packetData);
            }

            byte[] fileData = baos.toByteArray();

            String receivedHash = calculateHash(fileData);
            if (!receivedHash.equals(expectedHash)) {
                throw new IOException("File integrity check failed!");
            }

            File outputDir = new File(outputDirectory);
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            File outputFile = new File(outputDir, expectedFileName);
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(outputFile);
                fos.write(fileData);
            } finally {
                if (fos != null) {
                    fos.close();
                }
            }

            System.out.println("File saved: " + outputFile.getAbsolutePath());
            System.out.println("File integrity verified!");
        }

        private String calculateHash(byte[] data) throws NoSuchAlgorithmException {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);

            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }

        public void close() {
            if (socket != null && !socket.isClosed()) {
                socket.close();
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

                System.out.println("\n=== UDP File Transfer - Sender Mode ===");
                System.out.println("For local testing: use 'localhost' or '127.0.0.1' as receiver host");
                System.out.println("For network transfer: use receiver's IP address and ensure firewall allows UDP\n");

                FileSender sender = new FileSender();
                sender.sendFile(filePath, receiverHost, receiverPort);
                sender.close();

            } else if (mode.equals("receive")) {
                if (args.length < 3) {
                    printUsage();
                    return;
                }

                int port = Integer.parseInt(args[1]);
                String outputDir = args[2];

                System.out.println("\n=== UDP File Transfer - Receiver Mode ===");
                System.out.println("Receiver is bound to localhost for security.");
                System.out.println("Only local senders can transfer files to this receiver.");
                System.out.println("For network transfers, modify the code to bind to all interfaces.\n");

                FileReceiver receiver = new FileReceiver(port);
                receiver.receiveFile(outputDir);
                receiver.close();

            } else {
                printUsage();
            }
        } catch (SocketException e) {
            System.err.println("Socket error: " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("already in use")) {
                System.err.println("TIP: Port is already in use. Try a different port number.");
            }
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
        System.out.println("  Send mode:    java UDPFileTransfer send <file_path> <receiver_host> <receiver_port>");
        System.out.println("  Receive mode: java UDPFileTransfer receive <listen_port> <output_directory>");
        System.out.println();
        System.out.println("Example (local transfer - recommended for learning):");
        System.out.println("  Terminal 1: java UDPFileTransfer receive 5002 ./received/");
        System.out.println("  Terminal 2: java UDPFileTransfer send myfile.pdf localhost 5002");
        System.out.println();
        System.out.println("Example (network transfer):");
        System.out.println("  Receiver: java UDPFileTransfer receive 5002 ./received/");
        System.out.println("  Sender:   java UDPFileTransfer send myfile.pdf 192.168.1.100 5002");
    }
}
