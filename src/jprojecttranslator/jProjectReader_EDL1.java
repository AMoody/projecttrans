/*
 * 
 */
package jprojecttranslator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.StringTokenizer;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import java.net.URI;
import java.net.URLDecoder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import wavprocessor.WAVProcessor;

/**
 * This is a project reader for EDL (.edl) files
 * The EDL format is Samplitude EDL File Format Version 1.5 which can be used by Reaper
 * @author scobeam
 */
public class jProjectReader_EDL1 extends jProjectReader {
    File fAudioFolder;
    DateTime dtsCreated;
    List listSourceFileLines = new ArrayList();
    int intLineOffset = 0;
    
    /**
     * This returns a FileFilter which shows the files this class can read
     * @return FileFilter
     */
    @Override
    public javax.swing.filechooser.FileFilter getFileFilter() {
        javax.swing.filechooser.FileFilter filter = new FileNameExtensionFilter("Samplitide EDL (.edl)", "edl");
        return filter;
    }
    /**
     * This loads up an AES31 project in to the database.
     * @return      True if the project was loaded.
     */
    @Override
    protected boolean processProject() {
        intSoundFilesLoaded = 0;
        if (!fSourceFile.exists() || !fSourceFile.canRead()){
            return false;
        }        
        try {
          fAudioFolder = fSourceFile.getParentFile();
          BufferedReader inbuf = new BufferedReader(new FileReader(fSourceFile));
          while (true) {
            String s = inbuf.readLine();
            if (s == null) {
              break;
            }
            listSourceFileLines.add(s);
          }
          inbuf.close();
        } catch ( IOException ioe ) {
            System.out.println("Failed to read input file " + ioe.toString());
        }
        dtsCreated = new DateTime(fSourceFile.lastModified());
        if (parseFile(listSourceFileLines)) {
            System.out.println("EDL file parsed.");
            oProjectTranslator.writeStringToPanel(java.util.ResourceBundle.getBundle("jprojecttranslator/Bundle").getString("jProjectReader.ProjectLoaded"));
        } else {
            System.out.println("Failed to parse file");
            oProjectTranslator.writeStringToPanel(java.util.ResourceBundle.getBundle("jprojecttranslator/Bundle").getString("jProjectReader.FailReadXML"));
            return false;
        }
        
        setChanged();
        notifyObservers();
        oProjectTranslator.updateTable();
        System.out.println("EDL project file loaded");
        return true;
    } 
    /**
     * This tries to parse the edl file  in to the database
     * @param listSourceFileLines Pass in an array list of line of text
     * @return true if parse is successful.
     */
    protected boolean parseFile(List listSourceFileLines) {
        String strTitle = "";
        Matcher mMatcher;
        Pattern pPattern;
        pPattern = Pattern.compile("Samplitude EDL File Format Version\\s*([\\d|\\.]+)");
        mMatcher = pPattern.matcher(listSourceFileLines.get(0).toString());
        if (mMatcher.find()) {
            System.out.println("Samplitude EDL File found, version " + mMatcher.group(1));
        } 
        pPattern = Pattern.compile("Title:\\s*\"(.+)\"");
        for(int i = 1; i < 5 ; i++) {
            mMatcher = pPattern.matcher(listSourceFileLines.get(i).toString());
            if (mMatcher.find()) {
                strTitle = mMatcher.group(1);
                System.out.println("EDL title found " + strTitle);
                break;
            }
        }
        pPattern = Pattern.compile("Sample Rate:\\s*(\\d+)");
        for(int i = 1; i < 5 ; i++) {
            mMatcher = pPattern.matcher(listSourceFileLines.get(i).toString());
            if (mMatcher.find()) {
                int intTempRate = Integer.parseInt(mMatcher.group(1));
                System.out.println("Sample rate found " + intTempRate);
                if (intTempRate == 44100 || intTempRate == 48000) {
                    jProjectTranslator.intProjectSampleRate = intTempRate;
                    System.out.println("Sample rate found " + jProjectTranslator.intProjectSampleRate);
                } else {
                    oProjectTranslator.writeStringToPanel("The sample rate found in the EDL project " + intTempRate + " is not supported. " );
                    return false;
                }
                if (jProjectTranslator.intPreferredSampleRate != jProjectTranslator.intProjectSampleRate) {
                    sampleRateChange();
                }
                intLineOffset = i;
                break;                
            }
        }
        writeProjectToDatabase(strTitle, dtsCreated);
        intLineOffset = parseEDLSources(listSourceFileLines, intLineOffset);
        intLineOffset = parseEDLTracks(listSourceFileLines, intLineOffset);
        // Need to look at the sound files now to get further information
        // loadSoundFiles(st, fAudioFolder);
        return true;
        
    }
    /**
     * 
     * @param strTitle, the title of the project
     * @param dtsLastModified, the last modified date time from the edl file
     * @return True if the data was written successfully
     */
    protected boolean writeProjectToDatabase(String strTitle, DateTime dtsLastModified) {
        String strNotes = "";
        String strCreated = fmtSQL.print(dtsCreated);
        String strOriginator = "";
        String strClientData = "";
        try {
            strOriginator = URLEncoder.encode(strOriginator, "UTF-8");
            strTitle = URLEncoder.encode(strTitle, "UTF-8");
            strNotes = URLEncoder.encode(strNotes, "UTF-8");
            strClientData = URLEncoder.encode(strClientData, "UTF-8");
            strSQL = "INSERT INTO PUBLIC.PROJECT (intIndex, strTitle, strNotes, dtsCreated, strOriginator, strClientData) VALUES (" +
                "1, \'"+strTitle+" \', \'"+strNotes+" \',\'"+strCreated+"\',\' " + strOriginator + "\', \' " + strClientData + "\') ;";
            int i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        } catch(java.io.UnsupportedEncodingException e) {

        }
        return true;
    }
    /**
     * This will parse the Source Table Entries section of an edl file in to the database. 
     * It contains information about the sound files which are used.
     * @param listSourceFileLines An arraylist containing all the line from the source edl file.
     * @param intLineOffset An int which contains the line where the search should start.
     * @return int which is the last line in the EDL which was parsed.
     */
    protected int parseEDLSources(List listSourceFileLines, int intLineOffset) {
        int intIndex = intLineOffset;
        int intCountEntries = 0;
        int intLocalLineOffset = 0;
        String strURI, strFileName, strDestFileName, strSourceFileName;
        File fTemp;
        Matcher mMatcher;
        Pattern pPattern, pPatternEntry;
        pPattern = Pattern.compile("Source Table Entries:\\s*(\\d+)");
        pPatternEntry = Pattern.compile("\\s*(\\d+)\\s*\"(.+)\"");
        for(int i = intLineOffset; i < listSourceFileLines.size() ; i++) {
            mMatcher = pPattern.matcher(listSourceFileLines.get(i).toString());
            if (mMatcher.find()) {
                intCountEntries = Integer.parseInt(mMatcher.group(1));
                System.out.println("There are  " + intCountEntries + " source entries, intLocalLineOffset is " + i);
                intLocalLineOffset = i;
                break;
            }
        }
        for(int i = intLocalLineOffset + 1; i < intLocalLineOffset + 1 + intCountEntries ; i++) {
            mMatcher = pPatternEntry.matcher(listSourceFileLines.get(i).toString());
            if (mMatcher.find()) {
                System.out.println("Line matched  " + listSourceFileLines.get(i).toString());
                intIndex = Integer.parseInt(mMatcher.group(1));
                // Get the raw URI string
                strURI = mMatcher.group(2);
                // Strip off the leading URL: if it exists
                if (strURI.startsWith("URL:")) {
                    strURI = strURI.substring(4, strURI.length());
                }
                fTemp = new File(strURI);
                strSourceFileName = fTemp.toString();
                // The source file name is read from the URI
                strFileName = fTemp.getName();
                // This is encoded for insertion to the database
                strDestFileName = strFileName;
                strDestFileName = strDestFileName.replaceAll("[\\/:*?\"<>|%&]","_");
                try {
                    strFileName = URLEncoder.encode(strFileName, "UTF-8");
                    strDestFileName = URLEncoder.encode(strDestFileName, "UTF-8"); 
                    strSourceFileName = URLEncoder.encode(strSourceFileName, "UTF-8"); 
                    strSQL = "INSERT INTO PUBLIC.SOURCE_INDEX (intIndex, strType, strDestFileName, strUMID, intLength, strName, intFileOffset, intTimeCodeOffset, strSourceFile, intCopied, intVCSInProject, intFileSize) VALUES (" +
                        intIndex + ", \'F\',\'" + strDestFileName + "\',\'" + "" + "\', " + 0 + ", \'" + strFileName + "\', 0, " + 0 + ", \'" + strFileName + "\', 0, 0, 0) ;";
                    int j = st.executeUpdate(strSQL);
                    if (j == -1) {
                        System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                    }                         
                } catch (UnsupportedEncodingException e) {
                    System.out.println("UnsupportedEncodingException in parseEDLSources" + e.toString());
                } catch (SQLException e) {
                    System.out.println("Error on SQL in parseEDLSources " + strSQL + e.toString());
                }
                   
                
            } else {
                intIndex = i;
                break;
            }
            intIndex = i;
            
        }
        return intIndex;
    }
    /**
     * This will look for tracks in a string then examine the events on that track.
     * @param listSourceFileLines An arraylist containing all the line from the source edl file.
     * @param intLineOffset An int which contains the line where the search should start.
     * @return int which is the last line in the EDL which was parsed.
     */
    protected int parseEDLTracks(List listSourceFileLines, int intLineOffset) {
        int intIndex = intLineOffset;
        int intTrack = 0;
        int intLocalLineOffset = 0;
        int intChannelOffset = 0;
        String strTrackName = "";
        Matcher mMatcher;
        Pattern pPattern;
        pPattern = Pattern.compile("Track\\s*(\\d+):\\s*\"(.+)\"");
        for(int i = intLineOffset; i < listSourceFileLines.size() ; i++) {
            mMatcher = pPattern.matcher(listSourceFileLines.get(i).toString());
            if (mMatcher.find()) {
                intTrack = Integer.parseInt(mMatcher.group(1));
                strTrackName = mMatcher.group(2);
                System.out.println("Found track  " + intTrack + " - " + strTrackName + " , intLocalLineOffset is " + i);
                intLocalLineOffset = i;
                
            try {
                strTrackName = URLEncoder.encode(strTrackName, "UTF-8");
                strSQL = "SELECT SUM(intChannels) FROM PUBLIC.TRACKS;";
                ResultSet rs = st.executeQuery(strSQL);
                rs.next();
                if (!(rs.wasNull()) &&  rs.getInt(1) > 0) {
                    intChannelOffset = rs.getInt(1) + 1;
                } 
                strSQL = "INSERT INTO PUBLIC.TRACKS (intIndex, intAltIndex, strName, intChannels, intChannelOffset) VALUES (" +
                    intTrack + ", " + intTrack + ", \'" + strTrackName + "\', " + 2 + ", " + intChannelOffset + " );";
                int j = st.executeUpdate(strSQL);
                if (j == -1) {
                    System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                }
            } catch(java.io.UnsupportedEncodingException e) {
                System.out.println("Exception " + e.toString());
                return -1;
            } catch (java.sql.SQLException e) {
                System.out.println("Error on SQL " + strSQL + e.toString());
                return -1;
            }                  
                
                i = parseEDLEvent(listSourceFileLines, i, intTrack);
            }
        }
        return intLocalLineOffset;

        
    }
    /**
     * This will parse the entries from a single 'EVENT' in to the database including (Cut), (Infade), (Outfade) and (Rem)
     * @param listSourceFileLines An arraylist containing all the line from the source edl file.
     * @param intLineOffset An int which contains the line where the search should start.
     * @return int which is the last line in the EDL which was parsed.
     */
    protected int parseEDLEvent(List listSourceFileLines, int intLineOffset, int intTrack){
        int intIndex = intLineOffset;
        int intLocalLineOffset = 0;
        int intSourceIndex;
        long lDestIn, lDestOut, lSourceIn, lInFade, lOutFade;
        String strType = "Cut";
        String strRef = "I";
        String strTrackMap = "";
        String strRemark = "";
        String strGain = "0.00";
        String strInFade = "LIN _ _ _";
        String strOutFade = "LIN _ _ _";
        Matcher mMatcher;
        Pattern pPattern, pPatternEvent;
        pPattern = Pattern.compile("Track\\s*(\\d+):\\s*\"(.+)\"");
        pPatternEvent = Pattern.compile("\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+([+-]?\\d+\\.?\\d*)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+\"(.+)\"\\s+(\\d+)\\s+(\\d+)\\s+\"(.+)\"\\s+\"(.+)\"");
//        pPatternEvent = Pattern.compile("\\s*(\\d+)");
        for(int i = intLineOffset + 1; i < listSourceFileLines.size() ; i++) {
//            System.out.println(listSourceFileLines.get(i).toString());
            mMatcher = pPattern.matcher(listSourceFileLines.get(i).toString());
            if (mMatcher.find()) {
                return i - 1; // Found next track
            }
            mMatcher = pPatternEvent.matcher(listSourceFileLines.get(i).toString());
            if (mMatcher.find()) {
                // Found next event
                intSourceIndex = Integer.parseInt(mMatcher.group(1));
                lDestIn = Long.parseLong(mMatcher.group(3));
                lDestOut = Long.parseLong(mMatcher.group(4));
                lSourceIn = Long.parseLong(mMatcher.group(5));
                strGain = mMatcher.group(7);
                lInFade = Long.parseLong(mMatcher.group(10));
                lOutFade = Long.parseLong(mMatcher.group(13));
                strRemark = mMatcher.group(16);
                strTrackMap = "1~" + intTrack;
                System.out.println("Found event  " + intTrack + " - " + intSourceIndex + " - " + lDestIn + " , intLocalLineOffset is " + i);
                try {
                    strRemark = URLEncoder.encode(strRemark, "UTF-8");    
                    strSQL = "INSERT INTO PUBLIC.EVENT_LIST (intIndex, strType, strRef, intSourceIndex, strTrackMap, intSourceIn, intDestIn, intDestOut, strRemark"
                        + ", strInFade, intInFade, strOutFade, intOutFade, intRegionIndex, intLayer, intTrackIndex, bOpaque, strGain) VALUES (" +
                    intClipCounter++ + ", \'" + strType + "\',\'" + strRef + "\'," + intSourceIndex + ",\'" + strTrackMap + ""
                        + "\'," + lSourceIn + "," + lDestIn + "," + lDestOut + ",\'" + strRemark + "\', "
                        + "\'" + strInFade + "\', " + lInFade + ", \'" + strOutFade + "\', " + lOutFade + ", " + intIndex + ""
                        + ", 0, 0, \'N\', \'" + strGain + "\') ;";
                    int j = st.executeUpdate(strSQL);
                    if (j == -1) {
                        System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                    }

                
                } catch (java.sql.SQLException e) {
                    System.out.println("Error on SQL " + strSQL + e.toString());
                    return i;
                } catch (UnsupportedEncodingException e) {
                    System.out.println("UnsupportedEncodingException in parseEDLEvent " + e.toString());
                }                 
            }
            
        }
        

       
        
        
        return intIndex;
    }
    protected boolean parseAES31Fader_List(Element xmlFades) {
        if (xmlFades == null || !xmlFades.hasContent()) {
            return false;
        }
        String strFades = xmlFades.getText();
        Matcher mMatcher;
        Pattern pPattern;
        pPattern = Pattern.compile("\\(FP\\)\\s*(\\d+)\\s*(\\d\\d\\D\\d\\d\\D\\d\\d\\D\\d\\d\\D\\d\\d\\d\\d)\\s*([+-]?\\d+\\.?\\d*|_)");
        mMatcher = pPattern.matcher(strFades);
        int j;  
        while (mMatcher.find()) {
            
//            try {
//                strSQL = "INSERT INTO PUBLIC.FADER_LIST (intTrack, intTime, strLevel) VALUES (" +
//                    mMatcher.group(1) + ", " + getADLTimeLong(mMatcher.group(2)) + ",\'" + mMatcher.group(3) + "\') ;";
//                        j = st.executeUpdate(strSQL);
//                        if (j == -1) {
//                            System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
//                        }
//            } catch (java.sql.SQLException e) {
//                System.out.println("Error on SQL " + strSQL + e.toString());
//            }
            System.out.println("FADER_LIST entry found " + mMatcher.group(1) + " " + mMatcher.group(2) + " " + mMatcher.group(3));
        }
        return true;
    }
    
    /**
     * Read data from sound files which are referenced in the project if possible.
     * The sound files can contain useful information such as duration, USID etc.
     * Files which are written out as part of an AES31 project have to be BWAVs, i.e. they must have a bext chunk.
     * @param st                This allows the database to be updated.
     * @param fAudioFolder      This is the folder on the system where the files should be found.
     * @return                  Return the number of files found.
     */
    protected int loadSoundFiles(Statement st, File fAudioFolder) {
        try {
            strSQL = "SELECT intIndex, strName, strSourceFile, intLength, strDestFileName FROM PUBLIC.SOURCE_INDEX ORDER BY intIndex;";
            st = conn.createStatement();
            ResultSet rs = st.executeQuery(strSQL);
            String strSourceFile, strName, strUMID, strDestFileName, strType;
            File fLocalSourceFile, fTempLocalSourceFile;
            long lIndicatedFileSize, lSampleRate, lSourceFileSize, lTimeCodeOffset;
            double dDuration, dSourceSamples;
            int intSourceIndex, intChannels;
            while (rs.next()) {
                // Loop through the SOURCE_INDEX table and try to find out more about each file by reading data from the actual sound file (if we can find it)
                intSourceIndex = rs.getInt(1);
                strName = URLDecoder.decode(rs.getString(2), "UTF-8");
                strSourceFile = URLDecoder.decode(rs.getString(3), "UTF-8");
                strDestFileName = URLDecoder.decode(rs.getString(5), "UTF-8");
                dSourceSamples = rs.getDouble(4);
                fTempLocalSourceFile = new File(fAudioFolder, strSourceFile);
                if (!fTempLocalSourceFile.exists()) {
                    File[] files = fAudioFolder.listFiles();
                    for (File file : files) {
                        if (file.isDirectory()) {
                            fTempLocalSourceFile = new File(file, strSourceFile);
                            if (fTempLocalSourceFile.exists()) {
                                break;
                            }
                        }
                    }
                }
                fLocalSourceFile = fTempLocalSourceFile;
                tempWAVProc = new WAVProcessor();
                tempWAVProc.setSrcFile(fLocalSourceFile);
                tempWAVProc.setMultipart(false);
                if (fLocalSourceFile.exists()) {
                    System.out.println("Source file " + fLocalSourceFile + " found");
                } else {
                    System.out.println("Source file " + fLocalSourceFile + " not found");
                }
                if (fLocalSourceFile.exists() && fLocalSourceFile.canRead() && tempWAVProc.readFile(0,fLocalSourceFile.length())) {
                    lIndicatedFileSize = tempWAVProc.getIndicatedFileSize();
                    lSampleRate = tempWAVProc.getSampleRate();
                    dDuration =  tempWAVProc.getDuration();
                    intChannels = tempWAVProc.getNoOfChannels();
                    if (dSourceSamples < 2) {
                        dSourceSamples = tempWAVProc.getNoOfSamples()/intChannels;
                    }
                    strType = tempWAVProc.getFileType() + " " + tempWAVProc.getBitsPerSample() + " bit ";
                    if (tempWAVProc.hasBextChunk()) {
                        strType = "B-" + strType;
                    }
                    // Need to update the destination file extension, wav, rf64 or w64 but avoid name clashes, e.g. you could have three different files, test.wav, test.rf64, test.w64
                    if (jProjectTranslator.intSave64BitFilesAs == 0){
                        // Keep original 64 bit format
                        if (tempWAVProc.getFileType().equalsIgnoreCase("rf64") && !strDestFileName.toLowerCase().endsWith(".wav")) {
                            // RF64 file without wav extension, need to add it.
                            strDestFileName = strDestFileName + ".wav";
                        }
                        if (tempWAVProc.getFileType().equalsIgnoreCase("w64") && jProjectTranslator.bAlwaysUseWavExt && !strDestFileName.toLowerCase().endsWith(".wav")) {
                            // W64 file doesn't end in .wav but user always wants to always have .wav at end
                            strDestFileName = strDestFileName + ".wav";
                        }
                    }
                    if (tempWAVProc.getFileType().equalsIgnoreCase("wav")) {
                        if (!strDestFileName.toLowerCase().endsWith(".wav")) {
                            strDestFileName = strDestFileName + ".wav";
                        }
                    }
                    // Always save as RF64
                    if (jProjectTranslator.intSave64BitFilesAs == 1) {
                            if (!strDestFileName.toLowerCase().endsWith(".wav")) {
                            strDestFileName = strDestFileName + ".wav";
                        }
                    }
                    // Always save w64
                    if (!jProjectTranslator.bAlwaysUseWavExt && jProjectTranslator.intSave64BitFilesAs == 2 && tempWAVProc.getFileType().equalsIgnoreCase("RF64") && !strDestFileName.toLowerCase().endsWith(".w64")) {
                        strDestFileName = strDestFileName + ".w64";
                    } 
                    if (jProjectTranslator.bAlwaysUseWavExt && jProjectTranslator.intSave64BitFilesAs == 2 && !strDestFileName.toLowerCase().endsWith(".wav")) {
                        strDestFileName = strDestFileName + ".wav";
                    }  
                    strDestFileName = URLEncoder.encode(strDestFileName, "UTF-8");
                    strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET intIndicatedFileSize = " + lIndicatedFileSize + ", intSampleRate =  " + lSampleRate + ", dDuration =  " + dDuration + ", intChannels = " + intChannels
                            + ", intLength = " + dSourceSamples + ", strDestFileName = \'"
                            + strDestFileName + "\', strType = \'" + strType + "\' WHERE intIndex = " + intSourceIndex + ";";
                    int i = st.executeUpdate(strSQL);
                    if (i == -1) {
                        System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                    }
                    if (tempWAVProc.getBextTitle().length() == 0) {
                        tempWAVProc.setBextTitle(strName);
                    }
                    if (tempWAVProc.getBextOriginatorRef().length() > 0) {
                        strUMID = URLEncoder.encode(tempWAVProc.getBextOriginatorRef(), "UTF-8");
                        strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET strUMID = \'" + strUMID + "\' WHERE intIndex = " + intSourceIndex + ";";
                        i = st.executeUpdate(strSQL);
                        if (i == -1) {
                            System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                        }
                    } else {
                        strUMID = jProjectTranslator.getNewUSID();
                        tempWAVProc.setBextOriginatorRef(strUMID);
                        strUMID = URLEncoder.encode(strUMID, "UTF-8");
                        strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET strUMID = \'" + strUMID + "\' WHERE intIndex = " + intSourceIndex + ";";
                        i = st.executeUpdate(strSQL);
                        if (i == -1) {
                            System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                        }
                    }
                    if (tempWAVProc.getBextTimeCodeOffset() > 0) {
                        lTimeCodeOffset = tempWAVProc.getBextTimeCodeOffset();
                        System.out.println("Timecode ref from source file is " +  lTimeCodeOffset);
                        strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET intTimeCodeOffset = " + lTimeCodeOffset + " WHERE intIndex = " + intSourceIndex + ";";
                        i = st.executeUpdate(strSQL);
                        if (i == -1) {
                            System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                        }
                    }
                    
                    lWAVProcessors.add(tempWAVProc);
                    intSoundFilesLoaded++;
                    setChanged();
                    notifyObservers();
                }
            }    
                
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        } catch (java.io.UnsupportedEncodingException e) {
            System.out.println("Error on decoding at " + strSQL + e.toString());
        }
        System.out.println(" " + intSoundFilesLoaded + " sound files have been read from source file");
        return intSoundFilesLoaded;
    }  
    @Override
    public String getInfoText() {
        return "<b></b><br>"
                + "This importer will read an .edl (Edit Decision List) file which contains the EDL in Samplitude EDL File Format Version 1.5 which can be used by Reaper.<br>"
                + "If the required audio files have been found these will be read to acquire additional information.<br>"
                + "<br>";
    }
}
