package com.inmobi.databus.distcp;

import java.io.BufferedInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.apache.log4j.Logger;

import com.inmobi.databus.Cluster;
import com.inmobi.databus.DatabusConfig;
import com.inmobi.databus.DatabusConfigParser;
import com.inmobi.databus.FSCheckpointProvider;
import com.inmobi.databus.Stream;
import com.inmobi.databus.TestMiniClusterUtil;
import com.inmobi.databus.Stream.SourceStreamCluster;
import com.inmobi.databus.Stream.StreamCluster;
import com.inmobi.databus.local.LocalStreamServiceTest;
import com.inmobi.databus.local.LocalStreamServiceTest.TestLocalStreamService;
import com.inmobi.databus.utils.CalendarHelper;

@Test(groups = { "integration" })
public class MergeStreamTest extends TestMiniClusterUtil {

  private static final Log LOG = LogFactory.getLog(MergeStreamTest.class);

  /*
   * Here is the basic idea, create two clusters of different rootdir paths run
   * the local stream service to create all the files in streams_local directory
   * run the merge stream service and verify all the paths are visible in
   * primary cluster
   */
  /**
   * @throws Exception
   */
  public void testMergeStream() throws Exception {
    final int NUM_OF_FILES = 35;

    DatabusConfigParser configParser = new DatabusConfigParser(
        "test-mss-databus.xml");
    DatabusConfig config = configParser.getConfig();

    FileSystem fs = FileSystem.getLocal(new Configuration());

    List<TestLocalStreamService> services = new ArrayList<TestLocalStreamService>();

    for (Map.Entry<String, Cluster> cluster : config.getAllClusters()
        .entrySet()) {
      services.add(new TestLocalStreamService(config, cluster.getValue(),
          new FSCheckpointProvider(cluster.getValue().getCheckpointDir())));
    }
    
    List<String> pathstoRemove = new LinkedList<String>();

    for (Map.Entry<String, Stream> sstream : config.getAllStreams().entrySet()) {

      Date todaysdate = null;
      Map<String, List<String>> filesList = new HashMap<String, List<String>>();

      for (TestLocalStreamService service : services) {
        List<String> files = new ArrayList<String>(NUM_OF_FILES);
        Cluster cluster = service.getCluster();
        pathstoRemove.add(cluster.getRootDir());

        fs.delete(new Path(cluster.getRootDir()), true);
        Path createPath = new Path(cluster.getDataDir(), sstream.getValue()
            .getName() + File.separator + cluster.getName() + File.separator);
        fs.mkdirs(createPath);
        for (int j = 0; j < NUM_OF_FILES; ++j) {
          files.add(j,new String(sstream.getValue().getName() + "-"
              + cluster.getName() + "-"
              + LocalStreamServiceTest.getDateAsYYYYMMDDHHMMSS(new Date())));
          Path path = new Path(createPath, files.get(j));

          FSDataOutputStream streamout = fs.create(path);
          streamout.writeBytes("Creating Test data for cluster "
              + cluster.getName() + " data -> " + files.get(j));
          streamout.close();

          Assert.assertTrue(fs.exists(path));
        }

        filesList.put(cluster.getName(), files);

        service.runOnce();

        todaysdate = new Date();
        String commitpath = cluster.getLocalFinalDestDirRoot()
            + sstream.getValue().getName() + File.separator
            + CalendarHelper.getDateAsYYYYMMDDHHPath(todaysdate.getTime());
        FileStatus[] mindirs = fs.listStatus(new Path(commitpath));

        FileStatus mindir = mindirs[0];

        for (FileStatus minutedir : mindirs) {
          if (mindir.getPath().getName()
              .compareTo(minutedir.getPath().getName()) < 0) {
            mindir = minutedir;
          }
        }

        try {
          Integer.parseInt(mindir.getPath().getName());
          String streams_local_dir = commitpath + mindir.getPath().getName()
              + File.separator + cluster.getName();

          LOG.debug("Checking in Path for mapred Output: " + streams_local_dir);

          for (int j = 0; j < NUM_OF_FILES; ++j) {
            Assert.assertTrue(fs.exists(new Path(streams_local_dir + "-"
                + files.get(j) + ".gz")));
          }
        } catch (NumberFormatException e) {

        }
        //fs.delete(new Path(testRootDir), true);
      }
      
      Stream primaryStream = sstream.getValue();
      Set<Cluster> primaryCluster = new HashSet<Cluster>();
      Cluster destcluster = primaryStream.getPrimaryDestinationCluster();
      
      for(Iterator<StreamCluster> sourceStream = primaryStream.getSourceStreamClusters().iterator();sourceStream.hasNext();) {
        StreamCluster cluster = sourceStream.next();
        primaryCluster.add(cluster.getCluster());
        TestMergeStreamService service = new TestMergeStreamService(config,
            cluster.getCluster(), destcluster);

        service.execute();
      }

      String commitpath = destcluster.getFinalDestDirRoot()
          + sstream.getValue().getName()
          + File.separator
          + CalendarHelper.getDateAsYYYYMMDDHHPath(todaysdate.getTime());
      FileStatus[] mindirs = fs.listStatus(new Path(commitpath));

      FileStatus mindir = mindirs[0];

      for (FileStatus minutedir : mindirs) {
        if (mindir.getPath().getName()
            .compareTo(minutedir.getPath().getName()) < 0) {
          mindir = minutedir;
        }
      }

      try {
        Integer.parseInt(mindir.getPath().getName());
        String streams_dir = commitpath + mindir.getPath().getName()
            + File.separator;

        LOG.debug("Checking in Path for mapred Output: " + streams_dir);
        
        for (Iterator<Cluster> checkcluster = primaryCluster.iterator(); checkcluster
            .hasNext();) {
            Cluster tmpcluster = checkcluster.next();
          List<String> files = filesList.get(tmpcluster.getName());
            for (int j = 0; j < NUM_OF_FILES; ++j) {
            String checkpath = streams_dir + tmpcluster.getName() + "-"
                + files.get(j) + ".gz";
            LOG.debug("Checking file: " + checkpath);
            Assert.assertTrue(fs.exists(new Path(checkpath)));
            }
          }

      } catch (NumberFormatException e) {

      }
    }

    for (Iterator<String> path = pathstoRemove.iterator(); path.hasNext();) {
      fs.delete(new Path(path.next()), true);
    }

    fs.close();
  }

  public static class TestMergeStreamService extends MergedStreamService {

    public TestMergeStreamService(DatabusConfig config, Cluster srcCluster,
        Cluster destinationCluster) throws Exception {
      super(config, srcCluster, destinationCluster);
      // TODO Auto-generated constructor stub
    }

  }

}
