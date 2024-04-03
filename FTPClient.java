/**
 * FTPClient is a simple FTP client implementation in Java.
 * It supports basic FTP commands such as RETR, STOR, PASV, MODE, TYPE, STRU, etc.
 * The client establishes a connection to a specified host and port to communicate with an FTP server.
 * It can upload and download files using various modes and types.
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
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;


public class FTPClient {
    public static void main(String[] args) {
        String host = "127.0.0.1"; // Change host if needed
        int port = 2048; // Change port if needed
        String clientDIR = "Clients/";

        try (Socket socket = new Socket(host, port);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
             BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.println(reader.readLine()); // Welcome message from server

            String response = "";
            String input;
            String command;
            String mode = null, type = null, stru = null;

            Socket dataSocket = null;
            // InputStream dataInputStream = null;
            
            do {
                System.out.print("\nCOMMAND: ");
                input = consoleReader.readLine();
                command = input.split(" ")[0].toUpperCase();

                writer.println(input);
                
                response = reader.readLine();
                System.out.println(response);

                // HANDLE RETR
                if (command.equals("RETR") && response.startsWith("150")) {
                    receiveFileData(dataSocket, clientDIR + input.split(" ")[1], mode, type, stru);
                    System.out.println(reader.readLine());
                    dataSocket.close();
                }
                // HANDLE STOR
                else if (command.equals("STOR") && response.startsWith("150")) {
                    uploadFileData(dataSocket, clientDIR + input.split(" ")[1], mode, type);
                    System.out.println(reader.readLine());
                    dataSocket.close();
                } 
                // HADNLE multi-line response (LIST & HELP)
                else if (response.startsWith("150") || response.startsWith("214")) {
                    // Read all lines of the multi-line response until the final response code (226)
                    String line;
                    System.out.println();
                    do {
                        line = reader.readLine();
                        System.out.println(line);
                    } while (!line.startsWith("226") && !line.startsWith("214"));
                }
                // HANDLE PASV
                else if (command.equals("PASV")) {
                    // Find the index of the opening parenthesis '(' and closing parenthesis ')'
                    int startIndex = response.indexOf('(');
                    int endIndex = response.indexOf(')');

                    // Extract the substring between the parentheses
                    String ipAddressAndPort = response.substring(startIndex + 1, endIndex);

                    // Extract IP address and port from PASV response (you need to parse it properly)
                    String[] pasvParts = ipAddressAndPort.split(",");
                    String ipAddress = pasvParts[0] + "." + pasvParts[1] + "." + pasvParts[2] + "." + pasvParts[3];
                    int portH = Integer.parseInt(pasvParts[4]);
                    int portL = Integer.parseInt(pasvParts[5]);
                    
                    // Calculate the data port
                    int dataPort = (portH << 8) + portL;

                    // Establish data connection
                    dataSocket = new Socket(ipAddress, dataPort);
                    // dataInputStream = dataSocket.getInputStream();
                }
                else if (response.startsWith("230")) {
                    writer.println("TYPE I");
                    System.out.println("\n" + reader.readLine());

                    writer.println("MODE S");
                    System.out.println("\n" + reader.readLine());

                    writer.println("STRU F");
                    System.out.println("\n" + reader.readLine());

                    mode = "S";
                    type = "I";
                    stru = "F";
                }
                else if (response.startsWith("200") && command.equals("MODE")) {
                    mode = input.split(" ")[1].toUpperCase();
                }
                else if (response.startsWith("200") && command.equals("TYPE")) {
                    type = input.split(" ")[1].toUpperCase();
                }
                else if (response.startsWith("200") && command.equals("STRU")) {
                    stru = input.split(" ")[1].toUpperCase();
                }


            } while (!response.contains("221"));

            // Close the socket
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Uploads file data to the FTP server using the specified mode and type.
     * 
     * @param dataSocket The data socket for the data connection
     * @param filename The name of the file to upload
     * @param mode The transfer mode (C for compressed, B for binary, S for stream)
     * @param type The transfer type (A for ASCII, I for binary)
     * @param stru The data stucture (F for File Structure, R for Record Structure, P for Page Structure)
     * @throws IOException If an I/O error occurs during file upload
     */

    private static void uploadFileData(Socket dataSocket, String filename, String mode, String type) throws IOException {
        File file = new File(filename);
        if (file.exists() && file.isFile()) {
            if (stru.equals("R") && type.equals("A")) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
                    DataOutputStream outputStream = new DataOutputStream(dataSocket.getOutputStream())) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        outputStream.write(line.getBytes("UTF-8"));
                        outputStream.write("\n".getBytes("UTF-8")); // Append newline character
                    }
                    reader.close();
                    outputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            else if (mode.equals("C")) {
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
                    gzipOutputStream.close();
                    fileInputStream.close();     
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
                            // Block header: 1 byte for description + 2 bytes for size
                            byte[] header = new byte[3];
                            header[0] = 1; // Description byte
                            header[1] = (byte) ((bytesRead >> 8) & 0xFF); // High byte of size
                            header[2] = (byte) (bytesRead & 0xFF); // Low byte of size
                            outputStream.write(header);

                            // Send block header (description byte + size)
                            // outputStream.writeByte(1); // Description byte
                            // outputStream.writeShort(bytesRead); // Size
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
                    // Close the outputStream & InputStream after data transfer completes
                    bufferedInputStream.close();
                    outputStream.close();
                }
            }
        } else {
            System.out.println("550 File not found or cannot be accessed\r\n");
        }
    }

    /**
     * Downloads file data from the FTP server using the specified mode and type.
     * 
     * @param dataSocket The data socket for the data connection
     * @param filename The name of the file to download
     * @param mode The transfer mode (S for stream, B for block, C for compressed)
     * @param type The transfer type (A for ASCII, I for binary)
     * @param stru The data stucture (F for File Structure, R for Record Structure, P for Page Structure)
     * @throws IOException If an I/O error occurs during file download
     */

    private static void receiveFileData(Socket dataSocket, String filename, String mode, String type, String stru) throws IOException {
        // handle STRU R & TYPE A
        if (stru.equals("R") && type.equals("A")) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "UTF-8"));
                DataOutputStream outputStream = new DataOutputStream(dataSocket.getOutputStream())) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputStream.write(line.getBytes("UTF-8"));
                    outputStream.write("\n".getBytes("UTF-8")); // Append newline character
                }

                reader.close();
                outputStream.close();
            }
        }
        else {
            try (InputStream dataInputStream = new BufferedInputStream(dataSocket.getInputStream());
                FileOutputStream outputStream = new FileOutputStream(filename)) {
                int bufferSize = 8192;
                byte[] buffer = new byte[bufferSize];
                int bytesRead;
                switch (mode) {
                    case "S":
                        while ((bytesRead = dataInputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                            outputStream.flush();
                        }
                        break;      
                    case "B":
                        buffer = new byte[bufferSize + 3];
                        while ((bytesRead = dataInputStream.read(buffer)) != -1) {
                            int dataSize = (buffer[1] << 8) | (buffer[2] & 0xFF); // Retrieving size from the 2-byte header

                            if (dataSize <= 0) {
                                System.out.println("451: Requested action aborted. Local error in processing.");
                                break;
                            }

                            System.out.println(buffer[0] + " " + buffer[1] + " " + buffer[2] + " " + dataSize);
                            outputStream.write(buffer, 3, dataSize); // Skipping the header bytes
                        }
                        break;

                    case "C":
                        try (GZIPInputStream gzipInputStream = new GZIPInputStream(dataInputStream)) {
                            while ((bytesRead = gzipInputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, bytesRead);
                            }
                        }
                        break;
                
                    default:
                        break;
                }
                // Close the outputStream & InputStream after data transfer completes 
                dataInputStream.close();
                outputStream.close();
            }
        }
    }
}

