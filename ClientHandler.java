import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private BufferedReader reader;
    private PrintWriter writer;
    private final int MIN_PASSIVE_PORT = 49152; // Minimum passive port number
    private final int MAX_PASSIVE_PORT = 65535; // Maximum passive port number
    private String serverDIR = "Server";
    private String parentDIR = "";
    private String currentDIR = "/";
    private User currentUser = null;

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
        try {
            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            writer = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            writer.println("220 Welcome to NSCOM01 FTP server");

            String username = "";

            // Handle client commands
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ");
                String command = parts[0].toUpperCase();

                switch (command) {
                    case "USER":
                        if (parts.length == 2) {
                            username = parts[1];
                            writer.println("331 User name okay, need password");
                        } else {
                            writer.println("501 Syntax error in parameters or arguments");  // Send a response indicating that the USER command syntax is incorrect
                        }
                        break;
                    case "PASS":
                        if (parts.length != 2) {
                            writer.println("501 Syntax error in parameters or arguments");  // Send a response indicating that the USER command syntax is incorrect
                            break;
                        }

                        String password = parts[1];
                        currentUser = FTPServer.authenticateUser(username, password);
                        if (currentUser != null) {
                            writer.println("230 User logged in, proceed");
                            // handle other FTP commands
                            handleFTPCommands();
                        } else {
                            writer.println("530 Not logged in");
                        }
                        break;
                    case "QUIT":
                        writer.println("221 Goodbye.");
                        break;
                    default:
                        writer.println("550 Requested action not taken");
                        break;
                    
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleFTPCommands() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ");
                String command = parts[0].toUpperCase();
    
                switch (command) {
                    case "PASV":
                        handlePasvCommand();
                        break;
                    case "QUIT":
                        writer.println("221 Goodbye.");
                        break;
                    case "PWD":
                        writer.println("257 \"" + currentDIR + "\" is the current directory");
                        break;
                    case "CWD":
                        if(checkDIRExist(serverDIR + currentDIR + parts[1])) {
                            parentDIR = currentDIR;
                            currentDIR += parts[1] + "/";
                            writer.println("250 Directory successfully changed");
                        }
                        else
                            writer.println("550 Directory not found");
                        break;
                    case "CDUP":
                        if (parentDIR.length() == 0) {
                            // If already at the root server directory, cannot move further up
                            writer.println("550 Cannot change to parent directory");
                        } else {
                            // Update current directory to parent directory
                            currentDIR = parentDIR;
                            // Update parent directory to the parent directory of the new current directory
                            parentDIR = getParentDirectory(parentDIR);
                    
                            // Send success response to the client
                            writer.println("200 CDUP command successful");
                        }
                        break;
                    case "MKD":
                        // Create a File object representing the new directory
                        File newDirectory = new File(serverDIR + currentDIR + parts[1]);

                        // Attempt to create the directory
                        boolean created = newDirectory.mkdir();

                        // Send response to the client based on the success of the operation
                        if (created) {
                            writer.println("257 \"" + currentDIR + parts[1] + "\" created successfully");
                        } else {
                            writer.println("550 Failed to create directory");
                        }
                        break;
                    case "RMD":
                        // Create a File object representing the directory to be removed
                        File directoryToRemove = new File(serverDIR + currentDIR + parts[1]);

                        // Attempt to delete the directory
                        boolean deleted = deleteDirectory(directoryToRemove);

                        // Send response to the client based on the success of the operation
                        if (deleted) {
                            writer.println("250 Directory \"" + currentDIR + parts[1] + "\" deleted successfully");
                        } else {
                            writer.println("550 Failed to delete directory");
                        }
                        break;
                    case "LIST":
                        writer.println("150 Here comes the directory listing");
                        writer.println("Directory \"" + currentDIR + "\" has: \n");
                        writer.println(getFileList(serverDIR + currentDIR));
                        writer.println("226 Directory send OK");
                        break;
                    case "RETR":
                        if (parts.length < 2) {
                            writer.println("501 Syntax error in parameters or arguments");
                        } else {
                            handleRetrCommand(currentDIR + parts[1]);
                        }
                        break;
                    case "DELE":
                        writer.println("250 File deleted successfully");
                        break;
                    case "STOR":
                        writer.println("150 Opening data connection");
                        // Implement file storage logic here
                        writer.println("226 Transfer complete");
                        break;
                    case "HELP":
                        writer.println("214 Help message");
                        // Implement help message here
                        break;
                    case "TYPE":
                        if (parts.length < 2) {
                            writer.println("501 Syntax error in parameters or arguments");
                        } else {
                            handleTypeCommand(parts[1]);
                        }
                        break;
                    case "MODE":
                        if (parts.length < 2) {
                            writer.println("501 Syntax error in parameters or arguments");
                        } else {
                            handleModeCommand(parts[1]);
                        }
                        break;
                    case "STRU":
                        writer.println("200 Structure set to");
                        // Implement structure setting logic here
                        break;
                    default:
                        writer.println("550 Requested action not taken");
                        break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handlePasvCommand() {
        try {
            // Generate a random port for passive mode
            int passivePort = getRandomPort();
            ServerSocket serverSocket = new ServerSocket(passivePort);

            // Get server's IP address
            String ipAddress = clientSocket.getLocalAddress().getHostAddress().replace(".", ",");

            // Inform the client about the passive mode setup
            writer.println("227 Entering Passive Mode (" + ipAddress + "," + (passivePort / 256) + ","
                    + (passivePort % 256) + ")");
            writer.flush();

            // Accept incoming data connection
            Socket dataSocket = serverSocket.accept();

            // Close the dataSocket and serverSocket
            // dataSocket.close();
            // serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // RETR command
    private void handleRetrCommand(String filename) {
        try {
            File file = new File(serverDIR + filename);
            if (file.exists() && file.isFile()) {
                writer.println("150 File status okay; about to open data connection");
                try (FileInputStream fileInputStream = new FileInputStream(file);
                     BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
                     OutputStream outputStream = new DataOutputStream(clientSocket.getOutputStream())) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = bufferedInputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }
            } else {
                writer.println("550 File not found or cannot be accessed");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // MODE command
    private void handleModeCommand(String mode) {
        switch (mode) {
            case "S":
                // Set transfer mode to Stream (default mode)
                writer.println("200 Transfer mode set to Stream");
                break;
            case "B":
                // Set transfer mode to Block
                writer.println("200 Transfer mode set to Block");
                break;
            case "C":
                // Set transfer mode to Compressed
                writer.println("200 Transfer mode set to Compressed");
                break;
            default:
                writer.println("504 Command not implemented for that parameter");
                break;
        }
    }

    // TYPE command
    private void handleTypeCommand(String type) {
        if (type.equals("A") || type.equals("I")) {
            // Set the data transfer type accordingly (ASCII or Binary)
            writer.println("200 Type set to " + type);
        } else {
            writer.println("504 Command not implemented for that parameter");
        }
    }


    // generate a random port
    private int getRandomPort() {
        Random random = new Random();
        return random.nextInt(MAX_PASSIVE_PORT - MIN_PASSIVE_PORT + 1) + MIN_PASSIVE_PORT;
    }

    // checks if DIR exists
    private Boolean checkDIRExist(String directory) {
        // Get the absolute path of the directory
        Path directoryPath = Paths.get(directory).toAbsolutePath();

        // Check if the directory exists
        boolean directoryExists = Files.exists(directoryPath) && Files.isDirectory(directoryPath);

        // If directory exists, update current directory for the client session
        return directoryExists;
    }

    // Method to get the parent directory
    private String getParentDirectory(String directory) {
        int lastSlashIndex = directory.lastIndexOf('/');
        directory = directory.substring(0, lastSlashIndex);
        int secondToLastSlashIndex = directory.lastIndexOf('/');

        if (secondToLastSlashIndex == -1) {
            // If there is no parent directory, return ""
            return "";
        } else {
            // Return the substring before the last slash
            return directory.substring(0, secondToLastSlashIndex + 1);
        }
    }

    // Method to recursively delete a directory
    private boolean deleteDirectory(File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            return false;
        }

        // Get list of files and subdirectories in the directory
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    // Recursively delete subdirectory
                    deleteDirectory(file);
                } else {
                    // Delete file
                    file.delete();
                }
            }
        }

        // Delete the empty directory
        return directory.delete();
    }

    // Method to handle LIST command and return string representation of files
    private String getFileList(String currentDirectory) {
        StringBuilder fileListBuilder = new StringBuilder();
        File directory = new File(currentDirectory);
        File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    // Append file name to the string
                    fileListBuilder.append(file.getName()).append("\r\n");
                } else if (file.isDirectory()) {
                    // Append directory name with trailing slash to the string
                    fileListBuilder.append(file.getName()).append("/\r\n");
                }
            }
        }

        return fileListBuilder.toString();
    }

}
