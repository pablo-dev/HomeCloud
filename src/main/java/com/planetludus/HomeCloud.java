package com.planetludus;

import org.apache.commons.cli.*;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Main class of the HomeCloud application.
 *
 * @author pablo.carnero
 */
public class HomeCloud {

    private final static Logger logger = Logger.getLogger(HomeCloud.class);

    private final static int DEFAULT_PORT = 3999;
    private final static String DEFAULT_STORAGE_PATH = "D:\\Sync";
    private final static int DEFAULT_BUFFER_SIZE = 1024;
    
    private static int port = DEFAULT_PORT;
    private static String storagePath = DEFAULT_STORAGE_PATH;
    private static int bufferSize = DEFAULT_BUFFER_SIZE;
    
    /**
     * Read the input arguments
     * -b,--bufferSize <arg>   buffer size
     * -d,--directory <arg>    storage directory
     * -p,--port <arg>         port number
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
        int bytesRead;
        OutputStream output = null;
        try (
                PropManager propManager = new PropManager(storagePath);
                ServerSocket serverSocket = new ServerSocket(port)) {
            
            while (true)
            {
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
                    
                    for (int i = 0; i < fileNumbers; i++) {
                        
                        String fileName = clientData.readUTF();
                        logger.info("receiving the file number " + i + " with name: " + fileName);

                        output = new FileOutputStream(storagePath + "\\" + fileName);

                        long size = clientData.readLong();     
                        byte[] buffer = new byte[bufferSize];
                        while (size > 0 && (bytesRead = clientData.read(buffer, 0, (int) Math.min(buffer.length, size))) != -1) {
                            output.write(buffer, 0, bytesRead);
                            size -= bytesRead;
                            logger.info(size + " bytes remaining for " + fileName);
                        }

                        logger.info(fileName + " recived");
                    
                    }
                    
                    propManager.setLastSync(clientId);
                    
                } finally {
                    if (output != null) output.close();
                }
            }
            
        } catch (IOException ex) {
            logger.error("Input/output Error", ex);
        } catch (ConfigurationException ex) {
            logger.error("Error reading the config file. Probably the format is wrong", ex);
        }
    }

}
