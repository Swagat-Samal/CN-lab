package CNL_test.Q1_TCP_RegistrationLogin;

import java.io.*;
import java.net.*;
import java.util.*;

public class TCPServer {
    private static final int PORT = 5001;
    private static final int TOTAL_USERS = 5;

    public static void main(String[] args) {
        List<User> registeredUsers = new ArrayList<>();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started. Waiting for client on port " + PORT + "...");

            try (Socket socket = serverSocket.accept();
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                System.out.println("Client connected: " + socket.getInetAddress());

                for (int i = 0; i < TOTAL_USERS; i++) {
                    User user = (User) in.readObject();
                    registeredUsers.add(user);
                    System.out.println("Registered: " + user);
                    out.writeObject("User " + (i + 1) + " registered successfully.");
                    out.flush();
                }

                System.out.println("\nAll 5 users registered. Waiting for login attempts...\n");

                while (true) {
                    Object obj = in.readObject();

                    if (obj instanceof String && obj.equals("EXIT")) {
                        System.out.println("Client requested to close connection.");
                        break;
                    }

                    User loginAttempt = (User) obj;
                    boolean matched = false;

                    for (User u : registeredUsers) {
                        if (u.getUsername().equals(loginAttempt.getUsername()) &&
                                u.getPassword().equals(loginAttempt.getPassword())) {
                            matched = true;
                            break;
                        }
                    }

                    if (matched) {
                        out.writeObject("Log In Successful");
                        System.out.println("Login attempt for '" + loginAttempt.getUsername() + "': SUCCESS");
                    } else {
                        out.writeObject("Log In Unsuccessful");
                        System.out.println("Login attempt for '" + loginAttempt.getUsername() + "': FAILED");
                    }
                    out.flush();
                }

            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("Server shutting down.");
    }
}

class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final String dob;
    private final String username;
    private final String password;

    User(String name, String dob, String username, String password) {
        this.name = name;
        this.dob = dob;
        this.username = username;
        this.password = password;
    }

    String getName() {
        return name;
    }

    String getDob() {
        return dob;
    }

    String getUsername() {
        return username;
    }

    String getPassword() {
        return password;
    }

    @Override
    public String toString() {
        return "User{name='" + name + "', dob='" + dob + "', userId='" + username + "'}";
    }
}

