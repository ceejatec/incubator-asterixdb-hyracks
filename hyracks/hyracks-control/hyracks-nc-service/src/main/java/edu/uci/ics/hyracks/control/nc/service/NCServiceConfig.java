package edu.uci.ics.hyracks.control.nc.service;

import org.kohsuke.args4j.Option;

/**
 * Created by ceej on 5/28/15.
 */
public class NCServiceConfig {

    @Option(name = "-config-file", usage = "Local NC configuration file (default: /etc/nc.conf", required = false)
    public String configFile = "/etc/nc.conf";
}
