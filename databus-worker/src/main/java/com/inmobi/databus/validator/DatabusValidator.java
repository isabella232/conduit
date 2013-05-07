package com.inmobi.databus.validator;

import java.util.Calendar;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.inmobi.databus.DatabusConfig;
import com.inmobi.databus.DatabusConfigParser;
import com.inmobi.databus.utils.CalendarHelper;

public class DatabusValidator {
  private static final Log LOG = LogFactory.getLog(DatabusValidator.class);
  private static int minArgs = 4;

  public DatabusValidator() {
  }

  private static void printUsage() {
    System.out.println("Usage: ");
    System.out.println("-verify " +
        "[-stream (comma separated stream names)]" +
        "[-mode (comma separated stream modes: {local,merge,mirror})]" +
        "[-cluster (comma separated cluster names)]" +
        "<-start (YYYY/MM/DD/HH/mm) | -relstart (minutes from now)>" +
        "<-stop (YYYY/MM/DD/HH/mm) | -relstop (minutes from now)>" +
        "<-conf (databus.xml file path)>");
    System.out.println("-fix " +
        "<-stream (stream name)>" +
        "<-mode (stream mode: {local,merge,mirror})>" +
        "<-cluster (cluster name)>" +
        "<-start (YYYY/MM/DD/HH/mm)>" +
        "<-stop (YYYY/MM/DD/HH/mm)>" +
        "<-conf (databus.xml file path)>");
  }

  public static void main(String[] args) throws Exception {
    if (args.length < minArgs) {
      printUsage();
      System.exit(-1);
    }
    boolean verify = false;
    boolean fix = false;
    String streams = null;
    String modes = null;
    String clusters = null;
    String absoluteStartTime = null;
    String relStartTime = null;
    String absoluteStopTime = null;
    String relStopTime = null;
    String databusXmlFile = null;

    if (args[0].equalsIgnoreCase("-verify")) {
      verify = true;
    } else if (args[0].equalsIgnoreCase("-fix")) {
      fix = true;
    } else {
      printUsage();
      System.exit(-1);
    }

    // check each consecutive pair of command options
    for (int i = 1; i < args.length - 1;) {
      if (args[i].equalsIgnoreCase("-stream")) {
        streams = args[i+1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("-mode")) {
        modes = args[i+1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("-cluster")) {
        clusters = args[i+1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("-start")) {
        absoluteStartTime = args[i+1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("-relstart")) {
        relStartTime = args[i+1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("absoluteStopTime")) {
        absoluteStopTime = args[i+1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("relstop")) {
        relStopTime = args[i+1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("-conf")) {
        databusXmlFile = args[i+1];
        i += 2;
      } else {
        printUsage();
        System.exit(-1);
      }
    }

    // validate the mandatory options
    if (databusXmlFile == null
        || !isTimeProvided(absoluteStartTime, relStartTime)
        || !isTimeProvided(absoluteStopTime, relStopTime)
        || (fix
            && (streams == null || modes == null || clusters == null
            || absoluteStartTime == null || absoluteStopTime == null))) {
      printUsage();
      System.exit(-1);
    }
    
    Date startTime = getTime(absoluteStartTime, relStartTime);
    Date stopTime = getTime(absoluteStopTime, relStopTime);

    // parse databus.xml
    DatabusConfigParser configParser =
        new DatabusConfigParser(databusXmlFile);
    DatabusConfig config = configParser.getConfig();

    StreamsValidator streamsValidator = new StreamsValidator(config,
        streams, modes, clusters, startTime, stopTime);
    // perform streams verification
    streamsValidator.validateStreams(fix);
  }

  /**
   * @returns true if only one absolute/relative time is provided
   *          false if both are provided or none are provided
   */
  private static boolean isTimeProvided(String absoluteTime,
      String relTime) {
    return ((absoluteTime != null && relTime == null) ||
            (relTime != null && absoluteTime == null));
  }
  
  private static Date getTime(String absoluteTime, String relTime)
      throws Exception {
    Calendar cal = Calendar.getInstance();
    if (relTime != null) {
      int minutes = Integer.valueOf(relTime);
      cal.add(Calendar.MINUTE, -minutes);
      return cal.getTime();
    } else {
      try {
        return CalendarHelper.minDirFormat.get().parse(absoluteTime);
      } catch (Exception e) {
        throw new IllegalArgumentException("given time [" + absoluteTime +
            "] is not in the specified format.");
      }
    }
  }
}

