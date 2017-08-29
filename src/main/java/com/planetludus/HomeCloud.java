package com.planetludus;

import org.apache.commons.cli.*;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Main class of the HomeCloud application.
 *
 * @author pablo.carnero
 */
public class HomeCloud {

    private final static Logger logger = Logger.getLogger(HomeCloud.class);

    private final static int DEFAULT_PORT = 3999;
    private final static String DEFAULT_STORAGE_PATH = "C:\\Users\\pablo\\Temp";
    private final static int DEFAULT_BUFFER_SIZE = 1024;
    
    private static int port = DEFAULT_PORT;
    private static String storagePath = DEFAULT_STORAGE_PATH;
    private static int bufferSize = DEFAULT_BUFFER_SIZE;
    
    /**
     * Read the input arguments
     *   -d,--directory <arg>   storage directory
     *   -p,--port <arg>        port number
     * 
     * @param args arguments
     */
    private static void readParameters(String[] args) {
        // reading parameters
        Options options = new Options();
        
        Option portOption = new Option("p", "port", true, "port number");
        options.addOption(portOption);
        
        Option pathOption = new Option("d", "directory", true, "storage directory");
        options.addOption(pathOption);

        Option bufferOption = new Option("b", "bufferSize", true, "buffer size");
        options.addOption(bufferOption);
        
        // TODO: add user and password option for authentication
        
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;
        
        try {
            cmd = parser.parse(options, args);
            
            String portStr = cmd.getOptionValue("p");
            if (portStr != null) {
                try {
                    port = Integer.parseInt(portStr);
                } catch (NumberFormatException e) {
                    throw new ParseException("Integer value required for port: " + port);
                }
            }
            
            storagePath = cmd.getOptionValue("d", DEFAULT_STORAGE_PATH);

            String bufferSizeStr = cmd.getOptionValue("b");
            if (bufferSizeStr != null) {
                try {
                    bufferSize = Integer.parseInt(bufferSizeStr);
                } catch (NumberFormatException e) {
                    throw new ParseException("Integer value required for bufferSize: " + bufferSizeStr);
                }
            }
            
        } catch (ParseException ex) {
            logger.error("Invalid arguments: " + ex.getMessage());
            formatter.printHelp("homecloud", options);
            System.exit(1);
        }
    }
    
    /**
     * Starts a server listening to a port for incoming connections.
     * Once a connection is established it sends the last sync date to the client.
     * Then it asks for the number of file to receive and start the transference for all of them.
     * 
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        
        readParameters(args);
        
        // starting the service
        int numRead;
        OutputStream output = null;
        try (
                PropManager propManager = new PropManager(storagePath);
                ServerSocket serverSocket = new ServerSocket(port)) {
            
            while (true) {
                logger.info("awaiting for connection in port " + port + " ...");
                try (
                        Socket clientSocket = serverSocket.accept();
                        InputStream in = clientSocket.getInputStream();
                        DataInputStream clientData = new DataInputStream(in);
                        OutputStream out = clientSocket.getOutputStream();
                        DataOutputStream serverData = new DataOutputStream(out)
                    ) {

                    String clientId = clientData.readUTF();

                    logger.info("connection established with " + clientId);

                    logger.info("sending the buffer size " + bufferSize);
                    serverData.writeInt(bufferSize);

                    logger.info("sending the last sync date " + propManager.getLastSync(clientId));
                    serverData.writeUTF(propManager.getLastSync(clientId));

                    int fileNumbers = clientData.readInt();
                    logger.info("ready to receive " + fileNumbers + " files");

                    MessageDigest digest = MessageDigest.getInstance("MD5");
                    for (int i = 0; i < fileNumbers; i++) {

                        String fileName = clientData.readUTF();
                        logger.info("receiving the file number " + i + " with name: " + fileName);

                        output = new FileOutputStream(propManager.getFolder(clientId) + "\\" + fileName);

                        long size = clientData.readLong();
                        byte[] buffer = new byte[bufferSize];
                        while (size > 0 && (numRead = clientData.read(buffer, 0, (int) Math.min(buffer.length, size))) != -1) {
                            output.write(buffer, 0, numRead);
                            digest.update(buffer, 0, numRead);
                            size -= numRead;
                            logger.info(size + " bytes remaining for " + fileName);
                        }

                        logger.info(fileName + " recived");
                        String md5Client = clientData.readUTF();
                        logger.info("receiving the md5 checksum: " + md5Client);

                        // checking the md5 checksum
                        byte [] md5Bytes = digest.digest();
                        String md5 = convertHashToString(md5Bytes);
                        if (! md5.equals(md5Client)) {
                            logger.error("Failed transaction for file: " + fileName + ". MD5 checksum does not match");
                        } else {
                            logger.info("Transaction OK");
                        }
                    }

                    if (fileNumbers > 0) {
                        propManager.setLastSync(clientId);
                    }
                } catch (IOException ex) {
                    logger.error("Failed transaction: Input/output Error", ex);
                } catch (NoSuchAlgorithmException ex) {
                    logger.error("Unable to find MD5 algorithm", ex);
                } finally {
                    if (output != null) output.close();
                }
            }

        } catch (IOException ex) {
            logger.error("Input/output Error", ex);
        } catch (ConfigurationException ex) {
            logger.error("Failed transaction: Error reading the config file. Probably the format is wrong", ex);
        }
    }

    /**
     * Convert md5 hash to string
     *
     * @param md5Bytes md5 byte array
     * @return md5 string
     */
    private static String convertHashToString(byte[] md5Bytes) {
        String returnVal = "";
        for (int i = 0; i < md5Bytes.length; i++) {
            returnVal += Integer.toString(( md5Bytes[i] & 0xff ) + 0x100, 16).substring(1);
        }
        return returnVal.toUpperCase();
    }

}
