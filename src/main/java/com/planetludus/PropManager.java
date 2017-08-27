package com.planetludus;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class used to manage the properties such as the last sync date
 * 
 * @author pablo.carnero
 */
public class PropManager implements Closeable {
    
    private final static String CONFIG_FILE = ".properties";
    private final static DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // properties names
    private final static String PROP_LAST_SYNC = "lastSync";
    private final static String DEFAULT_LAST_SYNC = "1970-01-01 00:00:00";
    
    private File configFile;
    private String globalPath;

    /**
     * Class used to maintain information of property for each user
     */
    private class PropInformation {

        private PropertiesConfiguration propertiesConfiguration;
        private String userPath;

        public PropInformation(PropertiesConfiguration propertiesConfiguration, String userPath) {
            this.propertiesConfiguration = propertiesConfiguration;
            this.userPath = userPath;
        }

        public PropertiesConfiguration getPropertiesConfiguration() {
            return propertiesConfiguration;
        }

        public void setPropertiesConfiguration(PropertiesConfiguration propertiesConfiguration) {
            this.propertiesConfiguration = propertiesConfiguration;
        }

        public String getUserPath() {
            return userPath;
        }

        public void setUserPath(String userPath) {
            this.userPath = userPath;
        }
    }

    /**
     * The map where we will store all the properties file for all users
     */
    private Map<String, PropInformation> propsMap;

    /**
     * Initialize the map and assign the global path value
     * 
     * @param path The sync path
     */
    public PropManager(String path) throws IOException, ConfigurationException {
        this.globalPath = path;
        propsMap = new ConcurrentHashMap<>();
    }

    /**
     * Create or read the config file for the given clientId and read all the properties
     *
     * @param clientId The client id
     * @return The PropInformation object initialized
     * @throws IOException Error creating or reading the config file
     * @throws ConfigurationException Error loading the config file
     */
    private PropInformation getProps(String clientId) throws IOException, ConfigurationException {
        if (propsMap.containsKey(clientId)) {
            return propsMap.get(clientId);
        } else {
            // read the properties
            configFile = new File(globalPath + "\\" + clientId + "\\" + CONFIG_FILE);
            configFile.getParentFile().mkdirs();
            configFile.createNewFile();
            PropertiesConfiguration props = new PropertiesConfiguration();
            try (FileInputStream configFileInput = new FileInputStream(configFile)) {
                props.load(configFileInput);
                PropInformation propInformation = new PropInformation(props, configFile.getParent());
                propsMap.put(clientId, propInformation);
                return propInformation;
            }
        }
    }

    /**
     * Reads the properties and gets the last time when the files were synchronized
     * 
     * @return The last time when the files were synchronized
     */
    public String getLastSync(String clientId) throws IOException, ConfigurationException {
        String localdDateTime = getProps(clientId)
                .getPropertiesConfiguration().getString(clientId + "." + PROP_LAST_SYNC, DEFAULT_LAST_SYNC);
        return localdDateTime;
    }
    
    /**
     * Save the property last sync time with the current date and time
     * 
     * @throws IOException Any read/write error
     * @throws ConfigurationException Error saving the property file
     */
    public void setLastSync(String clientId) throws IOException, ConfigurationException {
        String nowDateTime = LocalDateTime.now().format(dateFormatter);
        getProps(clientId).getPropertiesConfiguration().setProperty(clientId + "." + PROP_LAST_SYNC, nowDateTime);
        try (FileOutputStream configFileOutput = new FileOutputStream(configFile)) {
            getProps(clientId).getPropertiesConfiguration().save(configFileOutput);
        }
    }

    /**
     * Get the path for the given clientId
     *
     * @param clientId
     * @return
     * @throws IOException
     * @throws ConfigurationException
     */
    public String getFolder(String clientId) throws IOException, ConfigurationException {
        return getProps(clientId).getUserPath();
    }

    @Override
    public void close() throws IOException {
        // TODO: check needed actions
    }
}
