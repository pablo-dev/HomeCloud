/*
 * ...
 */
package homecloud;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;

/**
 * Class used to manage the properties such as the last sync date
 * 
 * @author pablo.carnero
 */
public class PropManager implements Closeable {
    
    private final static String CONFIG_FILE = ".properties";
    private final static DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmmss");

    // properties names
    private final static String PROP_LAST_SYNC = "lastSync";
    private final static String DEFAULT_LAST_SYNC = "1970-01-01 00:00:00";
    
    private FileOutputStream configFileOutput = null;
    private PropertiesConfiguration props;
    private File configFile;
    
    /**
     * Create the <code>FileInputStream</code> by loading the properties
     * and creating an input stream for saving when needed
     * 
     * @param path the path where the property file will be store in
     * @throws FileNotFoundException
     * @throws IOException 
     */
    public PropManager(String path) throws FileNotFoundException, IOException, ConfigurationException {
        // read the properties
        configFile = new File(path + "\\" + CONFIG_FILE);
        configFile.createNewFile();
        props = new PropertiesConfiguration(configFile);
        try (FileInputStream configFileInput = new FileInputStream(configFile)) {
            props.load(configFileInput);
        }
    }
    
    /**
     * Reads the properties and gets the last time when the files were synchronized
     * 
     * @return the last time when the files were synchronized
     */
    public String getLastSync(String clientId) {
        String localdDateTime = props.getString(clientId + "." + PROP_LAST_SYNC, DEFAULT_LAST_SYNC);
        return localdDateTime;
    }
    
    /**
     * Save the property last sync time with the current date and time
     * 
     * @throws IOException 
     */
    public void setLastSync(String clientId) throws IOException, ConfigurationException {
        String nowDateTime = LocalDateTime.now().format(dateFormatter);
        props.refresh();
        props.setProperty(clientId + "." + PROP_LAST_SYNC, nowDateTime);
        try (FileOutputStream configFileOutput = new FileOutputStream(configFile)) {
            props.save(configFileOutput);
        }
    }

    @Override
    public void close() throws IOException {
        if (configFileOutput != null) configFileOutput.close();
    }
}
