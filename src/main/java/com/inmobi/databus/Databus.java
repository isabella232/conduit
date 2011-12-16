package com.inmobi.databus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.inmobi.databus.DatabusConfig.Cluster;
import com.inmobi.databus.consume.DataConsumer;
import com.inmobi.databus.distcp.RemoteCopier;

public class Databus {
    static Logger logger = Logger.getLogger(Databus.class);
    private DatabusConfig config;
    private String myClusterName;

    public Databus(String myClusterName, String databusconfigFile) throws Exception{
        DatabusConfigParser configParser;
        this.myClusterName = myClusterName;
        if(databusconfigFile == null)
            configParser= new DatabusConfigParser(null);
        else
            configParser = new DatabusConfigParser(databusconfigFile);
        Map<String, Cluster> clusterMap = configParser.getClusterMap();
        this.config = new DatabusConfig(configParser.getRootDir(), configParser.getStreamMap(),
                clusterMap, clusterMap.get(myClusterName));
        logger.debug("my cluster details " + clusterMap.get(myClusterName));
    }

    public void start() throws Exception {
        List<AbstractCopier> copiers = new ArrayList<AbstractCopier>();
        logger.warn("My clusterName is [" + myClusterName + "] " +
                config.getDestinationCluster().getName());
        logger.warn("clusters " + config.getClusters().size());
        for (Cluster c : config.getClusters().values()) {
            AbstractCopier copier = null;
            if (myClusterName.equalsIgnoreCase(config.getDestinationCluster().getName())) {
                logger.warn("Starting data consumer for Cluster[" +
                        myClusterName + "]");
                copier = new DataConsumer(config);
            } else {
                logger.warn("Starting remote copier for cluster [" +
                        config.getDestinationCluster().getName() + "]");
                copier = new RemoteCopier(config, c);
            }
            copiers.add(copier);
            copier.start();
        }

        for (AbstractCopier copier : copiers) {
            copier.join();
        }

    }

    public static void main(String[] args) throws Exception {
        String myClusterName = null;
        Databus databus;
        if (args != null && args.length >=1)
            myClusterName = args[0].trim();
        else {
            logger.warn("Specify this cluster name.");
            return;
        }
        if(args.length <= 1)
            databus = new Databus(myClusterName, null);
        else {
            String databusconfigFile = args[1].trim();
            databus = new Databus(myClusterName, databusconfigFile);
        }
        databus.start();
    }
}
