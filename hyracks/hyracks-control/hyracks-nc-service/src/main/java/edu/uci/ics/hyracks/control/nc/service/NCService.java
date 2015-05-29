package edu.uci.ics.hyracks.control.nc.service;

import org.ini4j.Ini;
import org.kohsuke.args4j.CmdLineParser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by ceej on 5/28/15.
 */
public class NCService {

    private static final Logger LOGGER = Logger.getLogger(NCService.class.getName());

    private static final ArrayList<Ini> inis = new ArrayList<>();

    private static Ini loadConf(String conffilename) throws IOException {
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.info("Reading configuration from " + conffilename);
        }
        Ini ini = new Ini();
        File conffile = new File(conffilename);
        if (!conffile.exists()) {
            throw new IOException ("Cannot read configuration file " + conffilename + "!");
        }
        ini.load(conffile);
        return ini;
    }

    private static <T> T getINIOpt(String section, String key, T default_value, Class<T> clazz) {
        T value = null;
        for (int i = inis.size() - 1 ; i >= 0 ; i--) {
            Ini ini = inis.get(i);
            value = ini.get(section, key, clazz);
            if (value != null) {
                break;
            }
        }
        return (value != null) ? value : default_value;
    }

    private static String getStringINIOpt(String section, String key, String default_value) {
        return getINIOpt(section, key, default_value, String.class);
    }

    private static int getIntINIOpt(String section, String key, int default_value) {
        return getINIOpt(section, key, default_value, Integer.class);
    }

    private static Ini getConfigFromCC(URL url) {
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO, "Connecting to CC at " + url);
        }
        try {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            InputStream input = conn.getInputStream();
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("Connected to " + url + ", reading configuration...");
            }
            return new Ini(input);
        } catch (IOException e) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.log(Level.INFO, "Failure reading configuration from " + url + "; will try again", e);
            }
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ignored) { }
            return null;
        }
    }
    private static List<String> buildCommand() {
        List<String> cList = new ArrayList<String>();
        String val;
        cList.add("hyracksnc");
        cList.add("-node-id");
        cList.add("nc1");
        cList.add("-cc-host");
        cList.add("localhost");
        cList.add("-iodevices");
        cList.add("/tmp/nc1");
        return cList;
    }

    private static void configEnvironment(Map<String,String> env) {
        String jvmargs = getStringINIOpt("nc", "jvm.args", "-Xmx1536m");
        env.put("JAVA_OPTS", jvmargs);
    }

    private static Process launchNCProcess() {
        try {
            ProcessBuilder pb = new ProcessBuilder(buildCommand());
            configEnvironment(pb.environment());
            // QQQ inheriting probably isn't right
            pb.inheritIO();
            // QQQ some way to see if this doesn't immediately exit with error?
            return pb.start();
        } catch (Exception e) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING, "Configuration from CC broken; will try again\n", e);
            }
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ignored) { }
            return null;
        }
    }

    public static void main(String[] args) {
        try {
            NCServiceConfig config = new NCServiceConfig();
            CmdLineParser cp = new CmdLineParser(config);
            cp.parseArgument(args);

            while (true) {
                inis.clear();
                inis.add(loadConf(config.configFile));

                String ccHost = getStringINIOpt("cc", "host", "localhost");
                int ccPort = getIntINIOpt("cc", "port", 16001);
                URL url = new URL("http://" + ccHost + ":" + ccPort + "/config");

                Ini ini = getConfigFromCC(url);
                if (ini == null) {
                    continue;
                }
                inis.add(ini);
                Process ncproc = launchNCProcess();
                if (ncproc == null) {
                    continue;
                }
                break;
            }

            System.err.println("exiting main");
            System.exit(0);
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
