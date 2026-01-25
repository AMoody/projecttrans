package jprojecttranslator;

import javax.swing.filechooser.FileNameExtensionFilter;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.io.SAXReader;
import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.dom4j.Element;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import wavprocessor.WAVProcessor;

/**
 * Project reader for Ardour projects.
 * Regions from edl tracks are read. 
 * As ardour supports an arbitrary number of audio channels per edl track these have been split apart in to mono tracks in the ADL file.
 * Regions in Ardour can be opaque or transparent, it's not always possible to support this in other editors.
 * Crossfades are converted to a fade in and a fade out.
 * Gain automation is copied across.
 * Ardour defaults to recording 32 bit floating point files, these may not be supported on other systems. 
 * For best compatibility Ardour files should be recorded as 16 or 24 bit BWAVs.
 * @author arth
 */
public class jProjectReader_ARDOUR extends jProjectReader {
    /** This is the xml document object which is used for loading and saving to an xml file.*/
    static Document xmlDocument = DocumentHelper.createDocument();
    DateTime dtsCreated;
    static final long lSuperclockTicksPerSecond = 282240000;
    /**
     * This returns a FileFilter which shows the files this class can read
     * @return FileFilter
     */
    @Override
    public javax.swing.filechooser.FileFilter getFileFilter() {
        javax.swing.filechooser.FileFilter filter = new FileNameExtensionFilter("Ardour (.ardour)", "ardour");
        return filter;
    }    
    /**
     * This loads up an Ardour xml project in to the database.
     * @return      True if the project was loaded.
     */
    @Override
    protected boolean processProject() {
        dtsCreated = new DateTime(fSourceFile.lastModified(), DateTimeZone.getDefault());
        intSoundFilesLoaded = 0;
        if (!loadXMLData(fSourceFile)) {
            return false;
        }
        if (parseARDOURXML(xmlDocument.getRootElement())) {
            System.out.println("XML parsed in to database");
            oProjectTranslator.writeStringToPanel(java.util.ResourceBundle.getBundle("jprojecttranslator/Bundle").getString("jProjectReader.ProjectLoaded"));
        } else {
            System.out.println("Failed to parse XML data");
            oProjectTranslator.writeStringToPanel(java.util.ResourceBundle.getBundle("jprojecttranslator/Bundle").getString("jProjectReader.FailReadXML"));
            return false;
        }
        
        setChanged();
        notifyObservers();
        oProjectTranslator.updateTable();
        System.out.println("Ardour project file loaded");
        return true;
    }
    
    /**
     * This loads the Ardour project file which is XML in to and internal xml Document.
     * @param setSourceFile     This is the source file.
     * @return                  Returns true if the file was loaded.
     */
    protected boolean loadXMLData(File setSourceFile) {
        try {
            SAXReader reader = new SAXReader();
            xmlDocument = reader.read(setSourceFile);
        } catch (DocumentException de) {
            System.out.println("Exception while loading XML data " + de.toString());
            return false;
        } catch (java.net.MalformedURLException e) {
            System.out.println("Exception while loading XML file " + e.toString());
            return false;
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
            strSQL = "SELECT intIndex, strName, strSourceFile, strDestFileName FROM PUBLIC.SOURCE_INDEX ORDER BY intIndex;";
            st = conn.createStatement();
            ResultSet rs = st.executeQuery(strSQL);
            ResultSet rs1;
            String strSourceFile, strName, strUMID, strEncodedSourceFile, strDestFileName, strType;
            File fLocalSourceFile;
            long lIndicatedFileSize, lSampleRate, lTimeCodeOffset;
            double dDuration, dSourceSamples;
            int intSourceIndex, intChannels;
            while (rs.next()) {
                // Loop through the SOURCE_INDEX table and try to find out more about each file by reading data from the actual sound file (if we can find it)
                intSourceIndex = rs.getInt(1);
                strName = URLDecoder.decode(rs.getString(2), "UTF-8");
                strEncodedSourceFile = rs.getString(3);
                strSourceFile = URLDecoder.decode(strEncodedSourceFile, "UTF-8");
                strDestFileName = URLDecoder.decode(rs.getString(4), "UTF-8");
                fLocalSourceFile = new File(fAudioFolder, strSourceFile);
                if (fLocalSourceFile.exists()) {
                    System.out.println("Source file " + fLocalSourceFile + " found");
                } else {
                    // Sometimes the file name also contains the full path.
                    fLocalSourceFile = new File(strSourceFile);
                    if (fLocalSourceFile.exists()) {
                        System.out.println("Source file " + fLocalSourceFile + " found");
                    } else {
                        System.out.println("Source file " + fLocalSourceFile + " not found");
                        continue;
                    }
                }
                tempWAVProc = new WAVProcessor();
                tempWAVProc.setSrcFile(fLocalSourceFile);
                tempWAVProc.setMultipart(false);
                if (fLocalSourceFile.exists() && fLocalSourceFile.canRead() && tempWAVProc.readFile(0,fLocalSourceFile.length())) {
                    lIndicatedFileSize = tempWAVProc.getIndicatedFileSize();
                    lSampleRate = tempWAVProc.getSampleRate();
                    dDuration =  tempWAVProc.getDuration();
                    intChannels = tempWAVProc.getNoOfChannels();
                    dSourceSamples = tempWAVProc.getNoOfSamples()/intChannels;
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
                    strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET intIndicatedFileSize = " + lIndicatedFileSize + ", intSampleRate =  " + lSampleRate + ", dDuration =  " + dDuration + ", "
                            + "intChannels = " + intChannels + ", intLength = " + dSourceSamples + ", strDestFileName = \'"
                            + strDestFileName + "\', strType = \'" + strType + "\' WHERE intIndex = " + intSourceIndex + ";";
                    int i = st.executeUpdate(strSQL);
                    if (i == -1) {
                        System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                    }
                    if (tempWAVProc.getBextTitle().length() == 0) {
                        tempWAVProc.setBextTitle(strName);
                    }
                    if (tempWAVProc.getBextOriginatorRef().length() > 0) {
                        // The UMID in the audio file should be unique, however Ardour sometimes creates multiple files with the same UMID.
                        // This occurs if you import an audio file with two or more channels, Ardour splits this in to multiple mono files which all have the same UMID, need to fix this here.
                        strUMID = URLEncoder.encode(tempWAVProc.getBextOriginatorRef(), "UTF-8");
                        strSQL = "SELECT COUNT(*) FROM PUBLIC.SOURCE_INDEX WHERE strUMID = \'" + strUMID + "\' AND strSourceFile <> \'" + strEncodedSourceFile + "\';";
                        rs1 = st.executeQuery(strSQL);
                        if (rs1.next() && rs1.getInt(1) > 0) {
                            strUMID = jProjectTranslator.getNewUSID();
                            tempWAVProc.setBextOriginatorRef(strUMID);
                            strUMID = URLEncoder.encode(strUMID, "UTF-8");
                        } 
                    } else {
                        strSQL = "SELECT strUMID FROM PUBLIC.SOURCE_INDEX WHERE strSourceFile = \'" + strEncodedSourceFile + "\';";
                        rs1 = st.executeQuery(strSQL);
                        if (rs1.next() && rs1.getString(1).length() > 0) {
                            strUMID = URLDecoder.decode(rs1.getString(1), "UTF-8");
                        } else {
                            strUMID = jProjectTranslator.getNewUSID();
                        }
                        tempWAVProc.setBextOriginatorRef(strUMID);
                        strUMID = URLEncoder.encode(strUMID, "UTF-8");
                    }
                    strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET strUMID = \'" + strUMID + "\' WHERE intIndex = " + intSourceIndex + ";";
                    i = st.executeUpdate(strSQL);
                    if (i == -1) {
                        System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
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

    
    /**
     * 
     * This method parses the xml fields from the .ardour file and adds the
     * information to the database.
     * @param setRoot   This is the root element in the xml file.
     * @return          Return true if the file was parsed.
     */
    protected boolean parseARDOURXML(Element setRoot) {
        Element xmlRoot = setRoot;
        String strCreator = "Ardour";
        String strCreatorVersion = xmlRoot.attributeValue("version");
        String strADLVersion = "01.01.00";
        String strTitle = xmlRoot.attributeValue("name");
        String strSampleRate = xmlRoot.attributeValue("sample-rate");
        String strNotes = "";
        String strAudioSubFolder = "interchange/" + strTitle + "/audiofiles";
        String strCreated = fmtSQL.withZone(DateTimeZone.UTC).print(dtsCreated);
        jProjectTranslator.intProjectSampleRate = Integer.parseInt(strSampleRate);
        if (jProjectTranslator.intPreferredSampleRate != jProjectTranslator.intProjectSampleRate) {
            sampleRateChange();
//            oProjectTranslator.writeStringToPanel("This project is not at your preferred sample rate so it can not be opened, the project rate is " + jProjectTranslator.intProjectSampleRate);
//            return false;
        }
        try {
            strNotes = URLEncoder.encode(strNotes, "UTF-8");
            strTitle = URLEncoder.encode(strTitle, "UTF-8");
        } catch(java.io.UnsupportedEncodingException e) {

        }
        try {
            strSQL = "INSERT INTO PUBLIC.VERSION (strID, strUID, strADLVersion, strCreator, strCreatorVersion) VALUES (" +
                " \' \',\' \',\'" + strADLVersion + "\', \'" + strCreator + "\', \'" + strCreatorVersion + "\') ;";
            int i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }
            strSQL = "INSERT INTO PUBLIC.PROJECT (intIndex, strTitle, strNotes, dtsCreated, strOriginator, strClientData) VALUES (" +
                "1, \'"+strTitle+" \', \'"+strNotes+" \',\'"+strCreated+"\',\' \', \' \') ;";
            i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        }
        Element xmlSources = xmlRoot.element("Sources");
        Element xmlSource;
        // Need to find the source files, they should be in a sub-folder called interchange/<project name>/audiofiles
        File fAudioFolder = new File (fSourceFolder, strAudioSubFolder);
        for (Iterator i = xmlSources.elementIterator("Source");i.hasNext();) {
            xmlSource = (Element)i.next();
            parseSourceData(xmlSource, st);
        }
        // Need to look at the sound files now to get further information
        loadSoundFiles(st, fAudioFolder);
        /** DiskStreams are tracks in the edl, they can have one or more tracks and this corresponds
         * to the ADL track map, e.g. 1~2
         * However they do not exist in Ardour 3, the same information is now stored in the Route element
         */ 
        Element xmlDiskStreams = xmlRoot.element("DiskStreams");
        Element xmlDiskStream;
        Element xmlRoutes;
        Element xmlRoute;
        Element xmlTempoMap;
        if (xmlDiskStreams != null) {
            for (Iterator i = xmlDiskStreams.elementIterator("AudioDiskstream");i.hasNext();) {
                xmlDiskStream = (Element)i.next();
                parseDiskStreamData(xmlDiskStream, st);
            }
        } else {
            // DiskStreams element was not found, try to use the Routes element instead.
            xmlRoutes = xmlRoot.element("Routes");
            for (Iterator i = xmlRoutes.elementIterator("Route");i.hasNext();) {
                xmlRoute = (Element)i.next();
                parseDiskStreamDataFromRoute(xmlRoute, st);
            }
        }
        
        // Playlists are the tracks in the EDL and these contain regions
        Element xmlPlaylists = xmlRoot.element("Playlists");
        Element xmlPlaylist;
        for (Iterator i = xmlPlaylists.elementIterator("Playlist");i.hasNext();) {
            xmlPlaylist = (Element)i.next();
            parsePlaylistData(xmlPlaylist, st);

        }
        // Fix overlapping opaque tracks from ardour.
        int intCounter = 0;
        int intPrunedTracks = 0;
        do {
            intPrunedTracks = pruneLowerTracks(st);
            intCounter++;
            
        } while (intPrunedTracks > 0 && intCounter < 5000);
        // Routes exist within the mixer and might contain gain automation data
        xmlRoutes = xmlRoot.element("Routes");
        boolean bMergeReqd = false;
        for (Iterator i = xmlRoutes.elementIterator("Route");i.hasNext();) {
            xmlRoute = (Element)i.next();
            if (parseRouteData(xmlRoute, st) == 1) {
                bMergeReqd = true;
            }
        }        
        if (bMergeReqd) {
            // Automation data in FADER_LIST_T needs to be merged.
            System.out.println("Automation data in FADER_LIST_T needs to be merged");
            mergeAutomationData(st);
        }
        xmlTempoMap = xmlRoot.element("TempoMap");
        parseTempoData(xmlTempoMap, st);
        return true;
    }
    protected void mergeAutomationData (Statement st) {
        /**
        * We need to merge the track automation data with the region automation data.
        * Since the points in each set of data do not occur at the same time each point will need to be adjusted
        * using the interpolated value from the other data set.
        * So for example a point at t2 in region gain need to have an adjustment.
        * The adjustment data comes from interpolating track gain data from t1 and t3.
        * Since all the gains are in dB we just need to add the values.
        * This might be easier to explain with a diagram, 
        * TODO a drawing.
        */
        strSQL = "INSERT INTO FADER_LIST_R SELECT * FROM FADER_LIST;";
        // (X0,Y0) is the nearest point before the current one.
        // (X1,Y1) is the current point.
        // (X2,Y2) is the nearest point after the current one.
        float fX0, fY0, fX1, fY1, fX2, fY2;
        int intCurrentTrack;
        try {
            int j = st.executeUpdate(strSQL);
            if (j == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }
            strSQL = "SELECT intTrack, intTime, strLevel FROM FADER_LIST_R;";
            ResultSet rs_R = st.executeQuery(strSQL);
            ResultSet rs_T;
            while (rs_R.next()) {
                intCurrentTrack = rs_R.getInt(1);
                // Check if there are any points for this audio track in FADER_LIST_T, if not, skip
                strSQL = "SELECT COUNT(*) FROM FADER_LIST_T WHERE intTrack = " + intCurrentTrack + ";";
                rs_T = st.executeQuery(strSQL);
                rs_T.next();
                if (rs_T.getInt(1) == 0) {
                    continue;
                }
                fX1 = rs_R.getLong(2);
                fY1 = Float.parseFloat(rs_R.getString(3));
                // Get the nearest point in FADER_LIST_T before the current FADER_LIST_R point
                strSQL = "SELECT MAX(intTime) FROM FADER_LIST_T WHERE intTrack = " + intCurrentTrack + " AND "
                        + "intTime <= " + fX1 + ";";
                rs_T = st.executeQuery(strSQL);
                rs_T.next();
                fX0 = rs_T.getLong(1);
//                System.out.println("Seeking X0 time " + rs_T.getLong(1) + " _ " + fX0);
                strSQL = "SELECT strLevel FROM FADER_LIST_T WHERE intTrack = " + intCurrentTrack + " AND "
                        + "intTime = " + fX0 + ";";
                rs_T = st.executeQuery(strSQL);
                if (rs_T.next()) {
                    fY0 = Float.parseFloat(rs_T.getString(1));
                } else {
                    fX0 = -1;
                    fY0 = -1;
                }
                
                // Get the nearest point in FADER_LIST_T after the current FADER_LIST_R point
                strSQL = "SELECT MIN(intTime) FROM FADER_LIST_T WHERE intTrack = " + intCurrentTrack + " AND "
                        + "intTime >= " + fX1 + ";";
                rs_T = st.executeQuery(strSQL);
                rs_T.next();
                fX2 = rs_T.getLong(1);
//                System.out.println("Seeking X2 time " + rs_T.getLong(1) + " _ " + fX2);
                strSQL = "SELECT strLevel FROM FADER_LIST_T WHERE intTrack = " + intCurrentTrack + " AND "
                        + "intTime = " + fX2 + ";";
                rs_T = st.executeQuery(strSQL);
                if (rs_T.next()) {
                    fY2 = Float.parseFloat(rs_T.getString(1));
                } else {
                    fX2 = -1;
                    fY2 = -1;
                }
                if (fX0 == -1) {
                    fX0 = 0;
                    fY0 = fY2;
                }
                if (fX2 == -1) {
                    fX2 = fX1;
                    fY2 = fY0;
                }
                // Apply the correction value
                if (fX0 == fX2) {
                    // Special case wherethe points are co-located
                    fY1 = fY1 + fY2;
                } else {
                    fY1 = fY1 + fY0 + ((fX1-fX0) * (fY2-fY0) / (fX2-fX0));
                }
                // Update the value in FADER_LIST table
                strSQL = "UPDATE FADER_LIST SET strLevel = " + String.format(Locale.UK,"%.2f", fY1) + " WHERE intTrack = " + intCurrentTrack + " AND "
                        + "intTime = " + fX1 + ";";
                j = st.executeUpdate(strSQL);
                if (j == -1) {
                    System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                }
            }
            // The FADER_LIST_R data has been updated
            
            
            strSQL = "SELECT intTrack, intTime, strLevel FROM FADER_LIST_T;";
            rs_T = st.executeQuery(strSQL);
            while (rs_T.next()) {
                intCurrentTrack = rs_T.getInt(1);
                // Check if there are any points for this audio track in FADER_LIST_R, if not, skip
                strSQL = "SELECT COUNT(*) FROM FADER_LIST_R WHERE intTrack = " + intCurrentTrack + ";";
                rs_R = st.executeQuery(strSQL);
                rs_R.next();
                if (rs_R.getInt(1) == 0) {
                    continue;
                }
                fX1 = rs_T.getLong(2);
                fY1 = Float.parseFloat(rs_T.getString(3));
                // Get the nearest point in FADER_LIST_R before the current FADER_LIST_T point
                strSQL = "SELECT MAX(intTime) FROM FADER_LIST_R WHERE intTrack = " + intCurrentTrack + " AND "
                        + "intTime <= " + fX1 + ";";
                rs_R = st.executeQuery(strSQL);
                rs_R.next();
                fX0 = rs_R.getLong(1);
//                System.out.println("Seeking X0 time " + rs_R.getLong(1) + " _ " + fX0);
                strSQL = "SELECT strLevel FROM FADER_LIST_R WHERE intTrack = " + intCurrentTrack + " AND "
                        + "intTime = " + fX0 + ";";
                rs_R = st.executeQuery(strSQL);
                if (rs_R.next()) {
                    fY0 = Float.parseFloat(rs_R.getString(1));
                } else {
                    fX0 = -1;
                    fY0 = -1;
                }
                
                // Get the nearest point in FADER_LIST_R after the current FADER_LIST_T point
                strSQL = "SELECT MIN(intTime) FROM FADER_LIST_R WHERE intTrack = " + intCurrentTrack + " AND "
                        + "intTime >= " + fX1 + ";";
                rs_R = st.executeQuery(strSQL);
                rs_R.next();
                fX2 = rs_R.getLong(1);
//                System.out.println("Seeking X2 time " + rs_R.getLong(1) + " _ " + fX2);
                strSQL = "SELECT strLevel FROM FADER_LIST_R WHERE intTrack = " + intCurrentTrack + " AND "
                        + "intTime = " + fX2 + ";";
                rs_R = st.executeQuery(strSQL);
                if (rs_R.next()) {
                    fY2 = Float.parseFloat(rs_R.getString(1));
                } else {
                    fX2 = -1;
                    fY2 = -1;
                }
                if (fX0 == -1) {
                    fX0 = 0;
                    fY0 = fY2;
                }
                if (fX2 == -1) {
                    fX2 = fX1;
                    fY2 = fY0;
                }
                // Apply the correction value
                if (fX0 == fX2) {
                    // Special case where the points are co-located
                    fY1 = fY1 + fY2;
                } else {
                    fY1 = fY1 + fY0 + ((fX1-fX0) * (fY2-fY0) / (fX2-fX0));
                }
                // Update the value in FADER_LIST table
                strSQL = "INSERT INTO FADER_LIST (intTrack, intTime, strLevel) VALUES (" +
                    intCurrentTrack + ", " + fX1 + ",\'" + String.format(Locale.UK,"%.2f", fY1) + "\') ;";
                j = st.executeUpdate(strSQL);
                if (j == -1) {
                    System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                }
            }
            // The FADER_LIST_T data has been updated
            
            
            
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        }
    }
    // parseDiskStreamDataFromRoute(xmlRoute, st);
    /**
     * This is used to parse disktream data from the route element.
     * In Ardour 2 there was a diskstreamdata element but in Ardour 3 this data 
     * has been merged in to the Route element.
     * @param xmlRoute  The is an xml element containing the route
     * @param st        This allows access to the database
     * @return          Return -1 is something was wrong, 0 if everything is OK
     */
    protected int parseDiskStreamDataFromRoute(Element xmlRoute, Statement st) {
        String strType = "";
        String strDirection = "";
        strType = xmlRoute.attributeValue("default-type");
        if (strType != null && strType.indexOf("midi") > -1) {
            return -1;
        }
        // In Ardour v6 the routes element has changed format and now has a version number
        String strVersion = xmlRoute.attributeValue("version");
        int intVersion = 0;
        if (strVersion != null) {
            intVersion = Integer.parseInt(strVersion);
        }
        // For some reason Ardour 3 sometimes uses the id number from the route and sometimes uses the id from the child element Diskstream so we need to load both.
        int intAudioDiskstreamIndex = Integer.parseInt(xmlRoute.attributeValue("id"));
        int intAltAudioDiskstreamIndex = 0;
        String strName = xmlRoute.attributeValue("name");
        int intChannelOffset = 1;
        int intChannels = 1;
        if (intVersion < 6000 ) {
            if (xmlRoute.element("Diskstream") != null) {
                intChannels = Integer.parseInt(xmlRoute.element("Diskstream").attributeValue("channels"));
            } else {
                return -1;
            }
            if (xmlRoute.element("Diskstream") != null) {
                intAltAudioDiskstreamIndex = Integer.parseInt(xmlRoute.element("Diskstream").attributeValue("id"));
            }            
        } else {
            Element xmlIO;
            Element xmlPort;
            intChannels = 0;
            for (Iterator i = xmlRoute.elementIterator("IO");i.hasNext();) {
                xmlIO = (Element)i.next();
                strDirection = xmlIO.attributeValue("direction");
                if (strDirection != null && strDirection.indexOf("Input") > -1) {
                    for (Iterator j = xmlIO.elementIterator("Port");j.hasNext();) {
                        xmlPort = (Element)j.next();
                        strType = xmlPort.attributeValue("type");
                        if (strType != null && strType.indexOf("audio") > -1) {
                            intChannels++;
                        }
                    }                    
                }

                
            }
            if (xmlRoute.attributeValue("audio-playlist") != null) {
                intAltAudioDiskstreamIndex = Integer.parseInt(xmlRoute.attributeValue("audio-playlist"));
            } else {
                intAltAudioDiskstreamIndex = 0;
            }           
        }

        try {
            strName = URLEncoder.encode(strName, "UTF-8");
            strSQL = "SELECT SUM(intChannels) FROM PUBLIC.TRACKS;";
            ResultSet rs = st.executeQuery(strSQL);
            rs.next();
            if (!(rs.wasNull()) &&  rs.getInt(1) > 0) {
                intChannelOffset = rs.getInt(1) + 1;
            } 
            strSQL = "INSERT INTO PUBLIC.TRACKS (intIndex, intAltIndex, strName, intChannels, intChannelOffset) VALUES (" +
                intAudioDiskstreamIndex + ", " + intAltAudioDiskstreamIndex + ", \'" + strName + "\', " + intChannels + ", " + intChannelOffset + " );";
            int i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }
        } catch(java.io.UnsupportedEncodingException e) {
            System.out.println("Exception " + e.toString());
            return -1;
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
            return -1;
        }        
        return 0;
    }
    /**
     * This method parses tempo and time signature data from the ardour file and puts it in the data base
     * @param xmlTempoMap
     * @param st
     * @return 
     */
    protected int parseTempoData(Element xmlTempoMap, Statement st){
        Element xmlTempo, xmlMeter, xmlTempos, xmlMeters;
        int intNoteType, intDivisionsPerBar, intBeat ;
        long lFrame;
        double dBeatsPerMinute, dEndBeatsPerMinute, dPulse;
        String strBBT = "";
        Pattern pQuarters = Pattern.compile("(\\d+):(\\d+)");
        Matcher mMatcher;
//        if (mMatcher.find()) {
//            lDestIn = jProjectTranslator.intProjectSampleRate * Long.parseLong(mMatcher.group(2)) / lSuperclockTicksPerSecond;
//            lDestOut = jProjectTranslator.intProjectSampleRate * (Long.parseLong(mMatcher.group(1))) / lSuperclockTicksPerSecond + lDestIn;
//        }        
        if (xmlTempoMap != null) {
            xmlTempos = xmlTempoMap.element("Tempos");
            for (Iterator i = xmlTempos.elementIterator("Tempo");i.hasNext();) {
                xmlTempo = (Element)i.next();
                try {
                    // In earlier versions of Ardour pulse was musical time in bars, musical time is now in quarter bars in the project file
                    mMatcher = pQuarters.matcher(xmlTempo.attributeValue("quarters"));
                    if (mMatcher.find()) {
                        dPulse = ( Long.parseLong(mMatcher.group(1)) + (Long.parseLong(mMatcher.group(2)) / 1920));
                    } else {
                        dPulse = 0;
                    }
                    dPulse = dPulse / 4;
                    // In earlier versions of Ardour frame was audio time in samples, audio time is now in superclock in the project file
                    lFrame = jProjectTranslator.intProjectSampleRate * (Long.parseLong(xmlTempo.attributeValue("sclock"))) / lSuperclockTicksPerSecond;
                    dBeatsPerMinute = Double.parseDouble(xmlTempo.attributeValue("npm"));
                    intNoteType = Integer.parseInt(xmlTempo.attributeValue("note-type"));
                    dEndBeatsPerMinute = Double.parseDouble(xmlTempo.attributeValue("enpm"));     
                
                    strSQL = "INSERT INTO PUBLIC.ARDOUR_TEMPO (dPulse, intFrame, dBeatsPerMinute, intNoteType, dEndBeatsPerMinute) VALUES (" +
                        dPulse + ", " + lFrame + ", "+ dBeatsPerMinute + ", " + intNoteType + ", " + dEndBeatsPerMinute + " );";
                    int j = st.executeUpdate(strSQL);
                    if (j == -1) {
                        System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                    }
            
                } catch (java.sql.SQLException e) {
                    System.out.println("Error on SQL " + strSQL + e.toString());
                    return -1;
                } catch (java.lang.NullPointerException e) {
                    System.out.println("Tempo map invalid " + e.toString());
                    return -1;
                }
            }
            xmlMeters = xmlTempoMap.element("Meters");
            for (Iterator i = xmlMeters.elementIterator("Meter");i.hasNext();) {
                xmlMeter = (Element)i.next();
                // In earlier versions of Ardour pulse was musical time in bars, musical time is now in quarter bars in the project file
                mMatcher = pQuarters.matcher(xmlMeter.attributeValue("quarters"));
                if (mMatcher.find()) {
                    dPulse = ( Long.parseLong(mMatcher.group(1)) + (Long.parseLong(mMatcher.group(2)) / 1920));
                } else {
                    dPulse = 0;
                }
                dPulse = dPulse / 4;
                // In earlier versions of Ardour frame was audio time in samples, audio time is now in superclock in the project file
                lFrame = jProjectTranslator.intProjectSampleRate * (Long.parseLong(xmlMeter.attributeValue("sclock"))) / lSuperclockTicksPerSecond;
                strBBT = xmlMeter.attributeValue("bbt");
                try {
                    strBBT = URLEncoder.encode(strBBT, "UTF-8");
                } catch (java.io.UnsupportedEncodingException e) {
                    System.out.println("Error on while trying to encode string" );
                    
                }
                // intBeat = Integer.parseInt(xmlMeter.attributeValue("beat"));
                intBeat = 0;
                intNoteType = Integer.parseInt(xmlMeter.attributeValue("note-value"));
                intDivisionsPerBar = Integer.parseInt(xmlMeter.attributeValue("divisions-per-bar"));     
                try {
                    strSQL = "INSERT INTO PUBLIC.ARDOUR_TIME_SIGNATURE (dPulse, intFrame, strBBT, intBeat, intNoteType, intDivisionsPerBar) VALUES (" +
                        dPulse + ", " + lFrame + ", \'" + strBBT + "\', " + intBeat + ", " + intNoteType + ", " + intDivisionsPerBar + " );";
                    int j = st.executeUpdate(strSQL);
                    if (j == -1) {
                        System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                    }
            
                } catch (java.sql.SQLException e) {
                    System.out.println("Error on SQL " + strSQL + e.toString());
                    return -1;
                }
            }            
        }        
        
        return 1;
    }
    /**
     * 
     * @param xmlRoute  The is an xml element containing the route
     * @param st        This allows access to the database
     * @return          Return -1 is something was wrong, 0 if everything is OK and 1 if automation merge is required.
     */
    protected int parseRouteData(Element xmlRoute, Statement st) {
        // diskstream-id="1710"
        // This indicates if the automation data can be imported directly or if it 
        // needs to be merged with existing data.
        int intMergeReqd = 0;
        int intAudioDiskstreamIndex = 0;
        if (xmlRoute.attributeValue("diskstream-id") != null) {
            intAudioDiskstreamIndex = Integer.parseInt(xmlRoute.attributeValue("diskstream-id"));
            
        } else {
//            if (xmlRoute.element("Diskstream") != null) {
//                intAudioDiskstreamIndex = Integer.parseInt(xmlRoute.element("Diskstream").attributeValue("id"));
//            } else {
//                return -1;
//            }
            if (xmlRoute.attributeValue("id") != null) {
                intAudioDiskstreamIndex = Integer.parseInt(xmlRoute.attributeValue("id"));
            } else {
                return -1;
            }
        }
        
        // We're only interested in gain automation data at the moment
        if (xmlRoute.element("IO") != null && xmlRoute.element("IO").element("Automation") != null
                && xmlRoute.element("IO").element("Automation").element("AutomationList") != null
                && xmlRoute.element("IO").element("Automation").element("AutomationList").element("events") != null) {
            System.out.println("Found automation data on disk stream ID  " + intAudioDiskstreamIndex);
            // Found automation data for this track, need to check that there is not already region gain data
            strSQL = "SELECT COUNT(*) FROM PUBLIC.FADER_LIST,PUBLIC.TRACKS WHERE "
                    + "PUBLIC.FADER_LIST.intTrack = PUBLIC.TRACKS.intChannelOffset AND "
                    + "(PUBLIC.TRACKS.intIndex = " + intAudioDiskstreamIndex + " OR PUBLIC.TRACKS.intAltIndex = " + intAudioDiskstreamIndex + ");";
            try {
                ResultSet rs = st.executeQuery(strSQL);
                rs.next();
                if (!(rs.wasNull()) &&  rs.getInt(1) == 0) {
                    // This data can be imported straight in to the FADER_LIST table
                    importEventData(xmlRoute.element("IO").element("Automation").element("AutomationList").element("events")
                            , st, "PUBLIC.FADER_LIST", intAudioDiskstreamIndex);
                    
                } else {

                    // FADER_LIST_T will have the track automation data
                    importEventData(xmlRoute.element("IO").element("Automation").element("AutomationList").element("events")
                            , st, "PUBLIC.FADER_LIST_T", intAudioDiskstreamIndex);
                    
                    intMergeReqd = 1;
                    System.out.println("Automation data on disk stream ID  " + intAudioDiskstreamIndex + " will need to be merged");
                }
            } catch (java.sql.SQLException e) {
                System.out.println("Error on SQL " + strSQL + e.toString());
            }
            return intMergeReqd;
            
            
            
        }
        if (xmlRoute.element("Processor") != null && xmlRoute.element("Processor").element("Automation") != null
                && xmlRoute.element("Processor").element("Automation").element("AutomationList") != null
                && xmlRoute.element("Processor").element("Automation").element("AutomationList").element("events") != null) {
            System.out.println("Found automation data on disk stream ID  " + intAudioDiskstreamIndex);
            // Found automation data for this track, need to check that there is not already region gain data
            strSQL = "SELECT COUNT(*) FROM PUBLIC.FADER_LIST,PUBLIC.TRACKS WHERE "
                    + "PUBLIC.FADER_LIST.intTrack = PUBLIC.TRACKS.intChannelOffset AND "
                    + "(PUBLIC.TRACKS.intIndex = " + intAudioDiskstreamIndex + " OR PUBLIC.TRACKS.intAltIndex = " + intAudioDiskstreamIndex + ");";
            try {
                ResultSet rs = st.executeQuery(strSQL);
                rs.next();
                if (!(rs.wasNull()) &&  rs.getInt(1) == 0) {
                    // This data can be imported straight in to the FADER_LIST table
                    importEventData(xmlRoute.element("Processor").element("Automation").element("AutomationList").element("events")
                            , st, "PUBLIC.FADER_LIST", intAudioDiskstreamIndex);
                    
                } else {

                    // FADER_LIST_T will have the track automation data
                    importEventData(xmlRoute.element("Processor").element("Automation").element("AutomationList").element("events")
                            , st, "PUBLIC.FADER_LIST_T", intAudioDiskstreamIndex);
                    
                    intMergeReqd = 1;
                    System.out.println("Automation data on disk stream ID  " + intAudioDiskstreamIndex + " will need to be merged");
                }
            } catch (java.sql.SQLException e) {
                System.out.println("Error on SQL " + strSQL + e.toString());
            }
            return intMergeReqd;
            
            
            
        }  
       
        // Still don't have any automation data, it could be an Ardour 6 file where everything can be automated so the file structure changed.
        Element xmlProcessor, xmlAutomationList, xmlEvents;
        String strType, strAutomationID;
        for (Iterator i = xmlRoute.elementIterator("Processor");i.hasNext();) {
            xmlProcessor = (Element)i.next();
            strType = xmlProcessor.attributeValue("type");
            if (strType != null && strType.indexOf("amp") > -1 && xmlProcessor.element("Automation") != null) {
                for (Iterator j = xmlProcessor.element("Automation").elementIterator("AutomationList");j.hasNext();) {
                    xmlAutomationList = (Element)j.next();
                    strAutomationID = xmlAutomationList.attributeValue("automation-id");
                    if (strAutomationID != null && strAutomationID.indexOf("gain") > -1 && xmlAutomationList.element("events") != null) {
                        xmlEvents = xmlAutomationList.element("events");
                        System.out.println("Found Ardour 6 automation data on disk stream ID  " + intAudioDiskstreamIndex);
                        // Found automation data for this track, need to check that there is not already region gain data
                        strSQL = "SELECT COUNT(*) FROM PUBLIC.FADER_LIST,PUBLIC.TRACKS WHERE "
                                + "PUBLIC.FADER_LIST.intTrack = PUBLIC.TRACKS.intChannelOffset AND "
                                + "(PUBLIC.TRACKS.intIndex = " + intAudioDiskstreamIndex + " OR PUBLIC.TRACKS.intAltIndex = " + intAudioDiskstreamIndex + ");";
                        try {
                            ResultSet rs = st.executeQuery(strSQL);
                            rs.next();
                            if (!(rs.wasNull()) &&  rs.getInt(1) == 0) {
                                // This data can be imported straight in to the FADER_LIST table
                                importEventData(xmlEvents, st, "PUBLIC.FADER_LIST", intAudioDiskstreamIndex);

                            } else {
                                // FADER_LIST_T will have the track automation data
                                importEventData(xmlEvents, st, "PUBLIC.FADER_LIST_T", intAudioDiskstreamIndex);

                                intMergeReqd = 1;
                                System.out.println("Automation data on disk stream ID  " + intAudioDiskstreamIndex + " will need to be merged");
                            }
                        } catch (java.sql.SQLException e) {
                            System.out.println("Error on SQL " + strSQL + e.toString());
                        }
                        return intMergeReqd;
            
                        
                    }
                }
            }
        }
        // No automation data found
        return -1;
        
    }
    
    protected void importEventData(Element xmlEvent, Statement st, String strTable, int intAudioDiskstreamIndex) {
        List listGainValues = new ArrayList();
        float fOffset, fValue;
        String strKey, strValue;
        String strEvents = xmlEvent.getText();
        StringTokenizer stTokens = new StringTokenizer(strEvents); 
        while(stTokens.hasMoreTokens()) {
            strKey = stTokens.nextToken();
            fOffset = java.lang.Math.round(Float.parseFloat(strKey));
            strValue = stTokens.nextToken();
            fValue = Float.parseFloat(strValue);
            // The list consists of keys which are the time in samples through the track, and values which are a float between 1 and 0.
            float[] fTemp = {fOffset, fValue};
            listGainValues.add(fTemp);
        } 
        strSQL = "SELECT intChannels, intChannelOffset FROM PUBLIC.TRACKS WHERE intIndex = " + intAudioDiskstreamIndex + " OR intAltIndex = " + intAudioDiskstreamIndex + ";";
        try {
            ResultSet rs = st.executeQuery(strSQL);
            rs.next();
            int intChannelCount = rs.getInt(1);
            int intMapOffset = rs.getInt(2);
            for(int i=1; i<intChannelCount + 1; i++){
                int j;
                if (listGainValues.size() > 0) {
                Iterator itr = listGainValues.iterator();
                float[] fTemp;
                while(itr.hasNext()) {
                    fTemp = (float[])(itr.next());
                    strSQL = "INSERT INTO " + strTable + " (intTrack, intTime, strLevel) VALUES (" +
                    (i + intMapOffset - 1) + ", " + (fTemp[0] ) + ",\'" + String.format(Locale.UK,"%.2f", 20*Math.log10(fTemp[1])) + "\') ;";
                        j = st.executeUpdate(strSQL);
                        if (j == -1) {
                            System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                        }
                    }
                }

            }             
        } catch (java.sql.SQLException e) {
                System.out.println("Error on SQL " + strSQL + e.toString());
            }
       
    }
    
    
    /**
     * Parse the DiskStreams element from the Ardour project file.
     * This contains some information about the tracks in the EDL including the number of channels.
     * @param xmlDiskStream     This is an xml Element containing the DiskStream data
     * @param st                This allows the database to be updated.
     */
    protected void parseDiskStreamData(Element xmlDiskStream, Statement st) {
        int intAudioDiskstreamIndex = Integer.parseInt(xmlDiskStream.attributeValue("id"));
        String strName = xmlDiskStream.attributeValue("name");
        int intChannels = Integer.parseInt(xmlDiskStream.attributeValue("channels"));
        int intChannelOffset = 1;
        try {
            strName = URLEncoder.encode(strName, "UTF-8");
            strSQL = "SELECT SUM(intChannels) FROM PUBLIC.TRACKS;";
            ResultSet rs = st.executeQuery(strSQL);
            rs.next();
            if (!(rs.wasNull()) &&  rs.getInt(1) > 0) {
                intChannelOffset = rs.getInt(1) + 1;
            } 
            strSQL = "INSERT INTO PUBLIC.TRACKS (intIndex, strName, intChannels, intChannelOffset) VALUES (" +
                intAudioDiskstreamIndex + ", \'" + strName + "\', " + intChannels + ", " + intChannelOffset + " );";
            int i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }
        } catch(java.io.UnsupportedEncodingException e) {
            System.out.println("Exception " + e.toString());
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        }
    }
    /**
     * Parse an Ardour playlist, this is a channel or track in the EDL.
     * This loops through all the regions and crossfades and adds the data to the database.
     * @param xmlPlaylist     This is an xml Element containing the playlist data
     * @param st              This allows the database to be updated.
     */
    protected void parsePlaylistData(Element xmlPlaylist, Statement st) {
        String strType = "";
        strType = xmlPlaylist.attributeValue("type");
        if (strType != null && strType.indexOf("midi") > -1) {
            return;
        }
        int intPlaylistIndex = 0;
        if (xmlPlaylist.attributeValue("orig_diskstream_id") != null) {
            intPlaylistIndex = Integer.parseInt(xmlPlaylist.attributeValue("orig_diskstream_id"));
        } else {
            intPlaylistIndex = Integer.parseInt(xmlPlaylist.attributeValue("orig-track-id"));
        }
        
        int intChannelOffset = 1;
        strSQL = "SELECT intChannelOffset FROM PUBLIC.TRACKS WHERE intIndex = " + intPlaylistIndex + " OR intAltIndex = " + intPlaylistIndex + ";";
        try {
            ResultSet rs = st.executeQuery(strSQL);
            rs.next();
            if (!(rs.wasNull()) &&  rs.getInt(1) > 0) {
                intChannelOffset = rs.getInt(1) ;
            }
            Element xmlRegion;
            for (Iterator i = xmlPlaylist.elementIterator("Region");i.hasNext();) {
                xmlRegion = (Element)i.next();
                parseRegionData(xmlRegion, intChannelOffset, st, intPlaylistIndex);

            }
            Element xmlCrossfade;
            for (Iterator i = xmlPlaylist.elementIterator("Crossfade");i.hasNext();) {
                xmlCrossfade = (Element)i.next();
                parseCrossfadeData(xmlCrossfade, st);

            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        }
        
    }
    /**
     * Parse Ardour crossfade data to the database.
     * When regions overlap a crossfade is created automatically.
     * This needs to be split up and converted to a fade out and fade in.
     * @param xmlCrossfade     This is an xml Element containing the crossfade data
     * @param st               This allows the database to be updated.
     */
    protected void parseCrossfadeData(Element xmlCrossfade, Statement st) {
        if (xmlCrossfade.attributeValue("active")!= null && xmlCrossfade.attributeValue("active").equalsIgnoreCase("no") ) {
            return;
        }
        int intCrossfadeOut = Integer.parseInt(xmlCrossfade.attributeValue("out"));
        int intCrossfadeIn = Integer.parseInt(xmlCrossfade.attributeValue("in"));
        long lCrossfade = Long.parseLong(xmlCrossfade.attributeValue("length"));
        long lInCrossFadePosition = Long.parseLong(xmlCrossfade.attributeValue("position"));
        long lOutCrossFadePosition = lInCrossFadePosition + lCrossfade;
        // Get the fade in values
        Element xmlFadeIn = xmlCrossfade.element("FadeIn");
        fade fadeIn = new fade();
        String strInFade = "";
        if (fadeIn.loadArdourFade(xmlFadeIn)) {
            strInFade = fadeIn.getFade();
        }
        // Get the fade in values
        Element xmlFadeOut = xmlCrossfade.element("FadeOut");
        fade fadeOut = new fade();
        String strOutFade = "";
        if (fadeOut.loadArdourFade(xmlFadeOut)) {
            strOutFade = fadeOut.getFade();
        }
        try {
            if (strInFade.length() > 0){
                strSQL = "UPDATE PUBLIC.EVENT_LIST SET strInFade = \'" + strInFade + "\', intInFade = " + lCrossfade + " WHERE intRegionIndex = " + intCrossfadeIn + ""
                        + " AND intDestIn = " + lInCrossFadePosition + ";";
                int j = st.executeUpdate(strSQL);
                if (j == -1) {
                    System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                }
            }
            if (strOutFade.length() > 0){
                strSQL = "UPDATE PUBLIC.EVENT_LIST SET strOutFade = \'" + strOutFade + "\', intOutFade = " + lCrossfade + " WHERE intRegionIndex = " + intCrossfadeOut + ""
                        + " AND intDestOut = " + lOutCrossFadePosition + ";";
                int j = st.executeUpdate(strSQL);
                if (j == -1) {
                    System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                }
            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
            return;
        
        }
        
    }/**
     * Parse an Ardour 'region' in to the EVENT_LIST table.
     * Entries on an EDL are called regions in Ardour. Regions can have any number of audio tracks.
     * For maximum compatibility these will be treated as mono. All editing systems can handle mono tracks, some can handle stereo but few can handle multichannel tracks.
     * The track map in the EVENT_LIST table can contain text like this '1 4' meaning source channel 1 goes to edl channel 4.
     * Stereo mapping looks like this, '1~2 3~4'
     * @param xmlRegion     This is an xml Element containing the region data
     * @param intMapOffset  Each audio track is offset by this number in the database
     * @param st            This allows the database to be updated.
     * @param intTrackIndex 
     */
    protected void parseRegionData(Element xmlRegion, int intMapOffset, Statement st, int intTrackIndex) {
        /** Each region can consist of one or more channels. 
         */
        String strType = "";
        strType = xmlRegion.attributeValue("type");
        if (strType != null && strType.indexOf("midi") > -1) {
            return;
        }
        int intRegionIndex = Integer.parseInt(xmlRegion.attributeValue("id"));
        int intLayer = 0;
        if (xmlRegion.attributeValue("layer") != null) {
            intLayer = Integer.parseInt(xmlRegion.attributeValue("layer"));
        } else {
            intLayer = Integer.parseInt(xmlRegion.attributeValue("layering-index"));
        }
        String strFlags = xmlRegion.attributeValue("flags");
        String strOpaque;
        if (strFlags != null) {
            boolean bOpaque = (strFlags.indexOf("Opaque") > -1);
            if (bOpaque) {
                strOpaque = "Y";
            } else {
                strOpaque = "N";
            }
        } else {
            if (xmlRegion.attributeValue("opaque").indexOf("1") > -1) {
                strOpaque = "Y";
            } else {
                strOpaque = "N";
            }
        }
        
        String strRemark = xmlRegion.attributeValue("name");
        try {
            strRemark = URLEncoder.encode(strRemark, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            System.out.println("Error on while trying to encode string" );
            return;
        }
        long lDestIn;
        long lDestOut;
        long lSourceIn;
        /**
         * Added code to support ardour V7
         * Starting from Ardour v7 the timing information is stored in a new format which is intended to make conversion between musical time and audio time work.
         * The project file uses audio time indicated by the letter a
         */
        Pattern pLength = Pattern.compile("a(\\d+)@a(\\d+)");
        Matcher mMatcher = pLength.matcher(xmlRegion.attributeValue("length"));
        if (mMatcher.find()) {
            lDestIn = jProjectTranslator.intProjectSampleRate * Long.parseLong(mMatcher.group(2)) / lSuperclockTicksPerSecond;
            lDestOut = jProjectTranslator.intProjectSampleRate * (Long.parseLong(mMatcher.group(1))) / lSuperclockTicksPerSecond + lDestIn;
        } else {
            lDestIn = (Long.parseLong(xmlRegion.attributeValue("position")));
            lDestOut = (Long.parseLong(xmlRegion.attributeValue("length"))) + lDestIn;
        }
        Pattern pAudioTime = Pattern.compile("a(\\d+)");
        mMatcher = pAudioTime.matcher(xmlRegion.attributeValue("start"));
        if (mMatcher.find()) {
            lSourceIn = jProjectTranslator.intProjectSampleRate * Long.parseLong(mMatcher.group(1)) / lSuperclockTicksPerSecond;
        } else {
            lSourceIn = (Long.parseLong(xmlRegion.attributeValue("start"))); 
        }
        // In Ardour the gain is a value to use a mutliplication, so 0dB = 1 in Ardour, need to convert this to dB for AES31
        
        String strGain;
        if (xmlRegion.attributeValue("scale-gain") != null) {
            strGain = xmlRegion.attributeValue("scale-gain");
        } else {
            strGain = xmlRegion.attributeValue("scale-amplitude");
        }
        Float fGain = Float.parseFloat(strGain);
        Double dGain = 20*Math.log10(fGain);
        strGain = String.format(Locale.UK,"%.2f", dGain);
        strType = "Cut";
        String strRef = "I";
        String strSourceChannel = "1";
        int intChannelCount = Integer.parseInt(xmlRegion.attributeValue("channels"));
        String strDestChannel ;
        String strTrackMap;
        int intSourceIndex;
        // Get the fade in values
        Element xmlFadeIn = xmlRegion.element("FadeIn");
        fade fadeIn = new fade();
        String strInFade = "";
        long lInFade = 0;
        if (fadeIn.loadArdourElement(xmlFadeIn)) {
            strInFade = fadeIn.getFade();
            lInFade = fadeIn.getLength();
        }
        // Get the fade out values
        Element xmlFadeOut = xmlRegion.element("FadeOut");
        fade fadeOut = new fade();
        String strOutFade = "";
        long lOutFade = 0;
        if (fadeOut.loadArdourElement(xmlFadeOut)) {
            strOutFade = fadeOut.getFade();
            lOutFade = fadeOut.getLength();
        }
        // Get the gain automation data for this region
        Element xmlEnvelope = xmlRegion.element("Envelope");
        List listGainValues = new ArrayList();
        float fOffset, fValue;
        if (xmlEnvelope.element("AutomationList") != null && xmlEnvelope.element("AutomationList").element("events") != null) {
            String strKey, strValue;
            
            String strEvents = xmlEnvelope.element("AutomationList").elementText("events");
            StringTokenizer stTokens = new StringTokenizer(strEvents); 
            while(stTokens.hasMoreTokens()) {
                strKey = stTokens.nextToken();
                mMatcher = pAudioTime.matcher(strKey);
                if (mMatcher.find()) {
                    fOffset = java.lang.Math.round(jProjectTranslator.intProjectSampleRate * Long.parseLong(mMatcher.group(1)) / lSuperclockTicksPerSecond);
                } else {
                    fOffset = java.lang.Math.round(Float.parseFloat(strKey));
                }
                strValue = stTokens.nextToken();
                fValue = Float.parseFloat(strValue);
                // The list consists of keys which are the time in samples across the region, and values which are a float between 1 and 0.
                float[] fTemp = {fOffset, fValue};
                listGainValues.add(fTemp);
            }
        }
        for(int i=1; i<intChannelCount + 1; i++){
            strDestChannel = "" + (i + intMapOffset - 1);
            intSourceIndex = Integer.parseInt(xmlRegion.attributeValue("source-" + (i-1)));
            try {
                strSQL = "SELECT intArdourChannel FROM PUBLIC.SOURCE_INDEX WHERE intIndex = " + intSourceIndex + ";";
                st = conn.createStatement();
                ResultSet rs = st.executeQuery(strSQL);
                rs.next();
                if (!(rs.wasNull()) ) {
                    strSourceChannel = "" + (rs.getInt(1) + 1);
                }
                strTrackMap = strSourceChannel + " " + strDestChannel;
                strSQL = "INSERT INTO PUBLIC.EVENT_LIST (intIndex, strType, strRef, intSourceIndex, strTrackMap, intSourceIn, intDestIn, intDestOut, strRemark"
                        + ", strInFade, intInFade, strOutFade, intOutFade, intRegionIndex, intLayer, intTrackIndex, bOpaque, strGain) VALUES (" +
                    intClipCounter++ + ", \'" + strType + "\',\'" + strRef + "\'," + intSourceIndex + ",\'" + strTrackMap + ""
                        + "\'," + lSourceIn + "," + lDestIn + "," + lDestOut + ",\'" + strRemark + "\', "
                        + "\'" + strInFade + "\', " + lInFade + ", \'" + strOutFade + "\', " + lOutFade + ", " + intRegionIndex + ""
                        + ", " + intLayer + ", " + intTrackIndex + ", \'" + strOpaque + "\', \'" + strGain + "\') ;";
                int j = st.executeUpdate(strSQL);
                if (j == -1) {
                    System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                }
                if (listGainValues.size() > 0) {
                    Iterator itr = listGainValues.iterator();
                    float[] fTemp;
                    while(itr.hasNext()) {
                        fTemp = (float[])(itr.next());
                        strSQL = "INSERT INTO PUBLIC.FADER_LIST (intTrack, intTime, strLevel) VALUES (" +
                    (i + intMapOffset - 1) + ", " + (fTemp[0] + lDestIn) + ",\'" + String.format(Locale.UK,"%.2f", 20*Math.log10(fTemp[1])) + "\') ;";
                        j = st.executeUpdate(strSQL);
                        if (j == -1) {
                            System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                        }
                    }
                }
                
            } catch (java.sql.SQLException e) {
                System.out.println("Error on SQL " + strSQL + e.toString());
                return;
            }
        }
        
    }
    /**
     * Parse source data, this information goes in to the SOURCE_INDEX table.
     * The ardour file does not contain much information about the source files, need to find out more later from the actual files.
     * 
     * @param xmlSource     This is an xml Element containing the source file data
     * @param st            This allows the database to be updated.
     */
    protected void parseSourceData(Element xmlSource, Statement st) {
        String strType = "";
        strType = xmlSource.attributeValue("type");
        if (strType != null && strType.indexOf("midi") > -1) {
            return;
        }
        String strName = xmlSource.attributeValue("name");
        String strSourceFile = strName;
        String strIndex = xmlSource.attributeValue("id");
        int intIndex = Integer.parseInt(strIndex);
        String strNameLowerCase = strName.toLowerCase();
//        int intEnd = strNameLowerCase.lastIndexOf(".");
        int intEnd = strNameLowerCase.indexOf(".");
//        if (intEnd == -1) {
//            intEnd = strNameLowerCase.lastIndexOf(".w64");
//        }
//        if (intEnd == -1) {
//            intEnd = strNameLowerCase.lastIndexOf(".rf64");
//        }
        if (intEnd == -1) {
            intEnd = strName.length();
        }
        strName = strName.substring(0, intEnd);
        strName = strName.replaceAll("[\\/:*?\"<>|%&]","_");

        int intChannel = Integer.parseInt(xmlSource.attributeValue("channel"));
//        String strURI = ".wav";
        try {
            strName = URLEncoder.encode(strName, "UTF-8");
            String strDestFile = strSourceFile;
            strDestFile = strDestFile.replaceAll("[\\/:*?\"<>|%&]","_");
            strDestFile = URLEncoder.encode(strDestFile, "UTF-8");
            strSourceFile = URLEncoder.encode(strSourceFile, "UTF-8");
            strSQL = "INSERT INTO PUBLIC.SOURCE_INDEX (intIndex, strType, strDestFileName, strName, strSourceFile, intCopied, intLength, intFileOffset, intTimeCodeOffset, intArdourChannel, strUMID) VALUES (" +
                intIndex + ", \'F\',\'" + strDestFile + "\', \'" + strName + "\', \'" + strSourceFile + "\', 0, 0, 0, 0," + intChannel + ", \'\') ;";
            System.out.println(strSQL);
            int i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }
        } catch(java.io.UnsupportedEncodingException e) {
            System.out.println("Exception " + e.toString());
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        }
        
    }
    protected int pruneLowerTracks(Statement st) {
        strSQL = "SELECT intIndex, intDestIn, intDestOut, intLayer, intTrackIndex FROM PUBLIC.EVENT_LIST WHERE bOpaque = \'Y\' ORDER BY intLayer DESC;";
        int intPrunedTracks, intIndex, intLayer, intTrackIndex;
        long lDestIn, lDestOut;
        intPrunedTracks = 0;
        try {
            ResultSet rs = st.executeQuery(strSQL);
            while (rs.next()) {
                intIndex = rs.getInt(1);
                lDestIn = rs.getLong(2);
                lDestOut = rs.getLong(3);
                intLayer = rs.getInt(4);
                intTrackIndex = rs.getInt(5);                
                /** Look for any underlying regions which start earlier or equal and end later or equal.
                 *  These underlying regions will be split up
                 */
                strSQL = "SELECT COUNT(*) FROM PUBLIC.EVENT_LIST WHERE "
                        + "intDestIn <= " + lDestIn + "AND "
                        + "intDestOut >= " + lDestOut + "AND "
                        + "intLayer < " + intLayer + "AND "
                        + "intTrackIndex = " + intTrackIndex + ";";
                ResultSet rs2 = st.executeQuery(strSQL);
                rs2.next();
                if (rs2.getInt(1) > 0) {
                    intPrunedTracks++;
                    System.out.println("" + rs2.getInt(1) + " overlapping regions found under region " + intIndex );
                    strSQL = "SELECT intIndex FROM PUBLIC.EVENT_LIST WHERE "
                        + "intDestIn <= " + lDestIn + "AND "
                        + "intDestOut >= " + lDestOut + "AND "
                        + "intLayer < " + intLayer + "AND "
                        + "intTrackIndex = " + intTrackIndex + ";";
                    rs2 = st.executeQuery(strSQL);
                    while (rs2.next()) {
                        splitRegion(rs2.getInt(1), lDestIn, lDestOut, st);
                    }
                }
                /**
                 * Delete underlying regions which are shorter than our current region
                 */
                strSQL = "DELETE FROM PUBLIC.EVENT_LIST WHERE "
                        + "intDestIn >= " + lDestIn + "AND "
                        + "intDestOut <= " + lDestOut + "AND "
                        + "intLayer < " + intLayer + "AND "
                        + "intTrackIndex = " + intTrackIndex + ";";
                int i = st.executeUpdate(strSQL);
                if (i == -1) {
                    System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                } 
                
            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        }
        return intPrunedTracks;
    }
    protected void splitRegion(int intIndex, long lDestIn, long lDestOut, Statement st) {
        int i, intLastIndex;
        try {
            // We need to duplicate the region first, the copy will be the end region.
            strSQL = "SELECT MAX(intIndex) FROM PUBLIC.EVENT_LIST;";
            ResultSet rs = st.executeQuery(strSQL);
            rs.next();
            intLastIndex = rs.getInt(1) + 1;
            strSQL =  "CREATE TABLE PUBLIC.EVENT_LIST2 AS (SELECT * FROM PUBLIC.EVENT_LIST WHERE intIndex = " + intIndex + ") WITH DATA;";
//            System.out.println("SQL is " + strSQL);
            i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }
            // Truncate the newly created end region and give in a valid index number
            // First we need to find the current value of the intDestIn point as the intSourceIn point needs to move too.
            strSQL = "SELECT intDestIn FROM PUBLIC.EVENT_LIST2 WHERE intIndex = " + intIndex + ";";
            rs = st.executeQuery(strSQL);
            rs.next();
            long lOldDestIn = rs.getLong(1);
            // Now we can move the in point of the end region to lDestOut, that's the new in point.
            strSQL = "UPDATE PUBLIC.EVENT_LIST2 SET intIndex = "
                    + "" + intLastIndex + ", intDestIn = " + lDestOut + ", intSourceIn = intSourceIn  + " + (lDestOut - lOldDestIn) + ", " 
                    + "intInFade = 0, strInFade = \'\';";
//            System.out.println("SQL is " + strSQL);
            i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }
            // Now to truncate the start region.
            strSQL = "UPDATE PUBLIC.EVENT_LIST SET intDestOut = " + lDestIn + ", intOutFade = 0, strOutFade = \'\' WHERE intIndex = " + intIndex + ";";
//            System.out.println("SQL is " + strSQL);
            i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }
            strSQL = "INSERT INTO PUBLIC.EVENT_LIST SELECT * FROM PUBLIC.EVENT_LIST2;";
//            System.out.println("SQL is " + strSQL);
            i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }
            // Delete zero length regions
            strSQL = "DELETE FROM PUBLIC.EVENT_LIST WHERE intDestIn = intDestOut;";
//            System.out.println("SQL is " + strSQL);
            i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }
            strSQL = "DROP TABLE PUBLIC.EVENT_LIST2;";
            i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        }
        

    }
        @Override
        public String getInfoText() {
        return "<b>Ardour</b><br>"
                + "This importer can read .ardour files which contain the EDL, it should then be able to find the associated audio. "
                + "The default audio file format in Ardour is 32 bit float which is not always supported by other audio editors so you "
                + "should consider changing this in your ardour project. "
                + "Regions lying under other opaque regions are split into independent event list entries in the AES31 project.<br>"
                + "Ardour 2, 3, 4 and 5 file formats are supported (internal format up to 3002). Midi files, tracks and regions are ignored because they are not supported by AES31.<br>"
                + "This version includes beta support for Ardour 6 files.<br>";
                        
    }
}
