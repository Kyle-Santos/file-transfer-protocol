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
                    receiveFileData(dataSocket, clientDIR + input.split(" ")[1], mode, type);
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
                    while (!(line = reader.readLine()).startsWith("226")) {
                        System.out.println(line);
                    }
                    System.out.println(line); // Print the final response line
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

    private static void uploadFileData(Socket dataSocket, String filename, String mode, String type) throws IOException {
        File file = new File(filename);
        if (file.exists() && file.isFile()) {
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

    private static void receiveFileData(Socket dataSocket, String filename, String mode, String type) throws IOException {
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

