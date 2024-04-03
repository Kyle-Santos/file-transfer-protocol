/**
 * The ClientHandler class handles communication with an FTP client.
 * It implements the Runnable interface for concurrent handling of multiple clients.
 * 
 * @author Ching, Nicolas Miguel T.
 * @author Santos, Kyle Adrian L.
 * @version 1.0
 * @since April 3, 2024
*/

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

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

    Boolean isPASV = false;
    String mode = "";
    String type = "";
    String stru = "";

    // data connection
    Socket dataSocket;
    ServerSocket serverSocket;

    /**
     * Constructs a ClientHandler object with the specified client socket.
     * 
     * @param clientSocket The client socket
    */

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
        try {
            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            writer = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Runs the client handler logic, handling FTP commands from the client.
    */

    @Override
    public void run() {
        try {
            writer.printf("220 Welcome to NSCOM01 FTP server\r\n");

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
                            writer.printf("331 User name okay, need password\r\n");
                        } else {
                            writer.printf("501 Syntax error in parameters or arguments\r\n");  // Send a response indicating that the USER command syntax is incorrect
                        }
                        break;
                    case "PASS":
                        if (username.equals("")) {
                            writer.printf("503 Bad sequence of commands\r\n");
                            break;
                        }
                        
                        if (parts.length != 2) {
                            writer.printf("501 Syntax error in parameters or arguments\r\n");  // Send a response indicating that the USER command syntax is incorrect
                            break;
                        }

                        String password = parts[1];
                        currentUser = FTPServer.authenticateUser(username, password);
                        if (currentUser != null) {
                            writer.printf("230 User logged in, proceed\r\n");
                            // handle other FTP commands
                            handleFTPCommands();
                        } else {
                            writer.printf("530 Not logged in\r\n");
                        }
                        break;
                    case "QUIT":
                        writer.printf("221 Goodbye.\r\n");
                        break;
                    default:
                        writer.printf("503 Bad sequence of commands\r\n");
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

    /**
     * Handles FTP commands sent by the client.
    */

    private void handleFTPCommands() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ");
                String command = parts[0].toUpperCase();

                if (!isPASV && (command.equals("STOR") || command.equals("RETR"))) {
                    writer.printf("503 Bad sequence of commands\r\n");
                    continue;
                }
    
                switch (command) {
                    case "PASV":
                        handlePasvCommand();
                        break;
                    case "QUIT":
                        writer.printf("221 Goodbye.\r\n");
                        break;
                    case "PWD":
                        writer.printf("257 \"" + currentDIR + "\" is the current directory\r\n");
                        break;
                    case "CWD":
                        if(checkDIRExist(serverDIR + currentDIR + parts[1])) {
                            parentDIR = currentDIR;
                            currentDIR += parts[1] + "/";
                            writer.printf("250 Directory successfully changed\r\n");
                        }
                        else
                            writer.printf("550 Directory not found\r\n");
                        break;
                    case "CDUP":
                        if (parentDIR.length() == 0) {
                            // If already at the root server directory, cannot move further up
                            writer.printf("550 Cannot change to parent directory\r\n");
                        } else {
                            // Update current directory to parent directory
                            currentDIR = parentDIR;
                            // Update parent directory to the parent directory of the new current directory
                            parentDIR = getParentDirectory(parentDIR);
                    
                            // Send success response to the client
                            writer.printf("200 CDUP command successful\r\n");
                        }
                        break;
                    case "MKD":
                        // Create a File object representing the new directory
                        File newDirectory = new File(serverDIR + currentDIR + parts[1]);

                        // Attempt to create the directory
                        boolean created = newDirectory.mkdir();

                        // Send response to the client based on the success of the operation
                        if (created) {
                            writer.printf("257 \"" + currentDIR + parts[1] + "\" created successfully\r\n");
                        } else {
                            writer.printf("550 Failed to create directory\r\n");
                        }
                        break;
                    case "RMD":
                        // Create a File object representing the directory to be removed
                        File directoryToRemove = new File(serverDIR + currentDIR + parts[1]);

                        // Attempt to delete the directory
                        boolean deleted = deleteDirectory(directoryToRemove);

                        // Send response to the client based on the success of the operation
                        if (deleted) {
                            writer.printf("250 Directory \"" + currentDIR + parts[1] + "\" deleted successfully\r\n");
                        } else {
                            writer.printf("550 Failed to delete directory\r\n");
                        }
                        break;
                    case "LIST":
                        writer.printf(getFileList(serverDIR + currentDIR));
                        break;
                    case "RETR":
                        if (parts.length < 2)
                            writer.printf("501 Syntax error in parameters or arguments\r\n");
                        else 
                            handleRetrCommand(parts[1]);
                        break;
                    case "DELE":
                        handleDelCommand(currentDIR + parts[1]);
                        break;
                
                    case "STOR":
                        if (parts.length < 2)
                            writer.printf("501 Syntax error in parameters or arguments\r\n");
                        else 
                            handleSTORCommand(parts[1]);
                        break;
                    case "HELP":
                        // Send a response containing detailed help information for each command
                        writer.printf("214 The following commands are recognized:\n" +
                                        "USER [user]       - Specify user for authentication\n" +
                                        "PASS [pass]       - Specify password for authentication\n" +
                                        "PWD               - Print working directory\n" +
                                        "CWD [dir]         - Change working directory\n" +
                                        "CDUP              - Change to the parent directory\n" +
                                        "MKD [dir]         - Make directory\n" +
                                        "RMD [dir]         - Remove directory\n" +
                                        "PASV              - Enter passive mode for data transfer\n" +
                                        "LIST              - List files in the current directory\n" +
                                        "RETR [file name]  - Retrieve a file from the server\n" +
                                        "DELE [file name]  - Delete a file\n" +
                                        "STOR [file name]  - Store a file on the server\n" +
                                        "HELP              - Display available commands and their descriptions\n" +
                                        "TYPE [A or I]     - Set transfer mode (ASCII or binary)\n" +
                                        "MODE [S, B, or C] - Set transfer mode (Stream, Block, or Compressed)\n" +
                                        "STRU [F, R, or P] - Set file transfer structure (File, Record, or Page)\n" +
                                        "QUIT              - Terminate the FTP session\n\n" +
                                        "214 Help OK\r\n");
        
                        break;
                    case "TYPE":
                        if (parts.length < 2) {
                            writer.printf("501 Syntax error in parameters or arguments\r\n");
                        } else {
                            handleTypeCommand(parts[1]);
                        }
                        break;
                    case "MODE":
                        if (parts.length < 2) {
                            writer.printf("501 Syntax error in parameters or arguments\r\n");
                        } else {
                            handleModeCommand(parts[1]);
                        }
                        break;
                    case "STRU":
                        if (parts.length < 2) {
                            writer.printf("501 Syntax error in parameters or arguments\r\n");
                        } else {
                            handleSTRUCommand(parts[1]);
                        }
                        break;
                    default:
                        writer.printf("550 Requested action not taken\r\n");
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
            serverSocket = new ServerSocket(passivePort);

            // Get server's IP address
            String ipAddress = clientSocket.getLocalAddress().getHostAddress().replace(".", ",");

            // Inform the client about the passive mode setup
            writer.printf("227 Entering Passive Mode (" + ipAddress + "," + (passivePort / 256) + ","
                    + (passivePort % 256) + ")\r\n");
            
            isPASV = true;

            // Accept incoming data connection
            dataSocket = serverSocket.accept();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleSTORCommand(String filename) {
        try {
            // Create a BufferedInputStream to read data from the client
            BufferedInputStream dataInputStream = new BufferedInputStream(dataSocket.getInputStream());

            // Create a FileOutputStream to write the received data to the file
            FileOutputStream outputStream = new FileOutputStream(serverDIR + currentDIR + filename);

            writer.printf("150 Ready to receive file [" + filename + "].\r\n");

            // Buffer to hold data temporarily
            int bufferSize = 8192;
            byte[] buffer = new byte[bufferSize];
            int bytesRead;
            switch (mode) {
                case "S":
                    while ((bytesRead = dataInputStream.read(buffer)) != -1) 
                        outputStream.write(buffer, 0, bytesRead);
                    break;      
                case "B":
                    buffer = new byte[bufferSize + 3];
                    while ((bytesRead = dataInputStream.read(buffer)) != -1) {
                        int dataSize = (buffer[1] << 8) | (buffer[2] & 0xFF); // Retrieving size from the 2-byte header

                        if (dataSize <= 0) {
                            writer.printf("451: Requested action aborted. Local error in processing.");
                            return;
                        }
                        outputStream.write(buffer, 3, dataSize); // Skipping the header bytes
                    }
                    break;

                case "C":
                    try (GZIPInputStream gzipInputStream = new GZIPInputStream(dataInputStream)) {
                        while ((bytesRead = gzipInputStream.read(buffer)) != -1)
                            outputStream.write(buffer, 0, bytesRead);
                        gzipInputStream.close();
                    }
                    break;
            
                default:
                    break;
            }

            // Send a success response to the client
            writer.printf("226 Closing data connection; transfer complete\r\n");

            // Close streams
            outputStream.close();
            dataInputStream.close();
            isPASV = false;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // RETR command
    private void handleRetrCommand(String filename) {
        File file = new File(serverDIR + currentDIR + filename);
        if (file.exists() && file.isFile()) {
            writer.printf("150 File status [" + filename + "] okay; about to open data connection\r\n");
            if (mode.equals("C")) {
                try (FileInputStream fileInputStream = new FileInputStream(file);
                    GZIPOutputStream gzipOutputStream = new GZIPOutputStream(dataSocket.getOutputStream())) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                        if (type.equals("A")) {
                            // Convert each byte to ASCII
                            System.out.println(buffer[0] + " " + buffer[1] + " " + buffer[2] + " " + bytesRead);
                            for (int i = 0; i < bytesRead; i++) {
                                // Convert byte to ASCII representation
                                buffer[i] = (byte) (buffer[i] & 0x7F);
                            }
                            System.out.println(buffer[0] + " " + buffer[1] + " " + buffer[2] + " " + bytesRead);
                        }
                            
                        gzipOutputStream.write(buffer, 0, bytesRead);
                    }       
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else {
                try (FileInputStream fileInputStream = new FileInputStream(file);
                    BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
                    DataOutputStream outputStream = new DataOutputStream(dataSocket.getOutputStream())) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    
                    while ((bytesRead = bufferedInputStream.read(buffer)) != -1) {
                        if (mode.equals("B")) {
                            // Send block header (description byte + size)
                            outputStream.writeByte(1); // Description byte
                            outputStream.writeShort(bytesRead); // Size
                        }

                        if (type.equals("A")) {
                            // Convert each byte to ASCII
                            System.out.println(buffer[0] + " " + buffer[1] + " " + buffer[2] + " " + bytesRead);
                            for (int i = 0; i < bytesRead; i++) {
                                // Convert byte to ASCII representation
                                buffer[i] = (byte) (buffer[i] & 0x7F);
                            }
                            System.out.println(buffer[0] + " " + buffer[1] + " " + buffer[2] + " " + bytesRead);
                        }
                        // Write file contents line by line to the data connection output stream
                        outputStream.write(buffer, 0, bytesRead);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }


            writer.printf("226 Closing data connection; transfer complete\r\n");
            isPASV = false;

            // Close the dataSocket and serverSocket
            // dataSocket.close();
            // serverSocket.close();
        } else {
            writer.printf("550 File not found or cannot be accessed\r\n");
        }

    }

    //DELE command
    private void handleDelCommand(String filename) {
        File file = new File(serverDIR + filename);
        if (file.exists() && file.isFile()) {
            try {
                // Attempt to delete the file
                if (file.delete()) {
                    // Send a success response to the client
                    writer.printf("250 File deleted successfully\r\n");
                } else {
                    // Send a failure response to the client
                    writer.printf("550 Failed to delete file\r\n");
                }
            } catch (SecurityException e) {
                // Send a failure response to the client
                writer.printf("550 Permission denied\r\n");
            }
        } else {
            // Send a "File not found" response to the client
            writer.printf("550 File not found\r\n");
        }
    }

    // STOR command
    private void storeFile(String filename) {
        try (
            InputStream inputStream = dataSocket.getInputStream();
            OutputStream outputStream = new FileOutputStream(new File(serverDIR + currentDIR + filename));
        ) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer))!= -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                dataSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // MODE command
    private void handleModeCommand(String modeInput) {
        switch (modeInput) {
            case "S":
                // Set transfer mode to Stream (default mode)
                writer.printf("200 Transfer mode set to Stream\r\n");
                mode = "S";
                break;
            case "B":
                // Set transfer mode to Block
                writer.printf("200 Transfer mode set to Block\r\n");
                mode = "B";
                break;
            case "C":
                // Set transfer mode to Compressed
                writer.printf("200 Transfer mode set to Compressed\r\n");
                mode = "C";
                break;
            default:
                writer.printf("504 Command not implemented for that parameter\r\n");
                break;
        }
    }

    // TYPE command
    private void handleTypeCommand(String typeInput) {
        if (typeInput.equals("A")) {
            // Set the data transfer type accordingly (ASCII)
            writer.printf("200 Type set to A\r\n");
            type = "A";
        }
        else if (typeInput.equals("I")) {
            // Set the data transfer type accordingly (BINARY)
            writer.printf("200 Type set to I\r\n");
            type = "I";
        }
        else {
            writer.printf("504 Command not implemented for that parameter\r\n");
        }
    }

    // STRU command
    private void handleSTRUCommand(String structure) {
        // Handle different structure types
        switch (structure) {
            case "F": // File structure
                writer.printf("200 File structure selected\r\n");
                stru = "F";
                break;
            case "R": // Record structure
                writer.printf("200 Record structure selected\r\n");
                stru = "R";
                break;
            case "P": // Page structure
                writer.printf("200 Page structure selected\r\n");
                stru = "P";
                break;
            default:
                // Invalid structure code
                writer.printf("504 Command not implemented for that parameter\r\n");
                break;
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

        fileListBuilder.append("150 Here comes the directory listing\n");
        fileListBuilder.append("Directory \"" + currentDIR + "\" has: \n\n");

        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    // Append file name to the string
                    fileListBuilder.append(file.getName()).append("\n");
                } else if (file.isDirectory()) {
                    // Append directory name with trailing slash to the string
                    fileListBuilder.append(file.getName()).append("\n");
                }
            }
        }
        fileListBuilder.append("\n226 Directory send OK\r\n");

        return fileListBuilder.toString();
    }

}
