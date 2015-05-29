package edu.uci.ics.hyracks.control.nc.service;

import edu.uci.ics.hyracks.control.common.controllers.NCConfig;
import org.apache.commons.lang3.StringUtils;
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

    private static NCConfig createNCConfig() {
        // QQQ error checking for required args
        NCConfig nc = new NCConfig();
        // QQQ this default-value stuff is spread over too many places - NCConfig parameters itself,
        // these getFooINIOpt() methods (and their values here), NCConfig.applyDefaults()...
        // need to consolidate.
        nc.ccHost = getStringINIOpt("cc", "host", nc.ccHost);
        // QQQ Not sure cluster.port is the right name here, but it has to
        // be different than the servlet port that main() reads. It should
        // come back from the CC, come to think of it.
        nc.ccPort = getIntINIOpt("cc", "cluster.port", nc.ccPort);
        nc.nodeId = getStringINIOpt("nc", "id", nc.nodeId);

        // Network ports

        nc.ipAddress = getStringINIOpt("nc", "address", nc.ipAddress);

        nc.clusterNetIPAddress = getStringINIOpt("nc", "cluster.address", nc.clusterNetIPAddress);
        nc.clusterNetPort = getIntINIOpt("nc", "cluster.port", nc.clusterNetPort);
        nc.dataIPAddress = getStringINIOpt("nc", "data.address", nc.dataIPAddress);
        nc.dataPort = getIntINIOpt("nc", "data.port", nc.dataPort);
        nc.resultIPAddress = getStringINIOpt("nc", "result.address", nc.resultIPAddress);
        nc.resultPort = getIntINIOpt("nc", "result.port", nc.resultPort);

        nc.clusterNetPublicIPAddress = getStringINIOpt("nc", "public.cluster.address", nc.clusterNetPublicIPAddress);
        nc.clusterNetPublicPort = getIntINIOpt("nc", "public.cluster.port", nc.clusterNetPublicPort);
        nc.dataPublicIPAddress = getStringINIOpt("nc", "public.data.address", nc.dataPublicIPAddress);
        nc.dataPublicPort = getIntINIOpt("nc", "public.data.port", nc.dataPublicPort);
        nc.resultPublicIPAddress = getStringINIOpt("nc", "public.result.address", nc.resultPublicIPAddress);
        nc.resultPublicPort = getIntINIOpt("nc", "public.result.port", nc.resultPublicPort);

        // Directories
        nc.ioDevices = getStringINIOpt("nc", "iodevices", null);

        nc.applyDefaults();
        return nc;
    }

    private static List<String> buildCommand() {
        List<String> cList = new ArrayList<String>();
        cList.add("hyracksnc");
        createNCConfig().toCommandLine(cList);
        for (String foo : cList) {
            System.out.println("###### " + foo);
        }
        return cList;
    }

    private static void configEnvironment(Map<String,String> env) {
        if (env.containsKey("JAVA_OPTS")) {
            return;
        }
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
