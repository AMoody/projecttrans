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
import org.dom4j.Element;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

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
            oProjectTranslator.writeStringToPanel("Project loaded.");
        } else {
            System.out.println("Failed to parse XML data");
            oProjectTranslator.writeStringToPanel("Failed to parse XML source data correctly");
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
            strSQL = "SELECT intIndex, strName, strSourceFile FROM PUBLIC.SOURCE_INDEX ORDER BY intIndex;";
            st = conn.createStatement();
            ResultSet rs = st.executeQuery(strSQL);
            String strSourceFile, strName, strUMID;
            File fLocalSourceFile;
            long lIndicatedFileSize, lSampleRate, lTimeCodeOffset;
            double dDuration, dSourceSamples;
            int intSourceIndex, intChannels;
            while (rs.next()) {
                // Loop through the SOURCE_INDEX table and try to find out more about each file by reading data from the actual sound file (if we can find it)
                intSourceIndex = rs.getInt(1);
                strName = URLDecoder.decode(rs.getString(2), "UTF-8");
                strSourceFile = URLDecoder.decode(rs.getString(3), "UTF-8");
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
                tempBWFProc = new BWFProcessor();
                tempBWFProc.setSrcFile(fLocalSourceFile);
                tempBWFProc.setMultipart(false);
                if (fLocalSourceFile.exists() && fLocalSourceFile.canRead() && tempBWFProc.readFile(0,fLocalSourceFile.length())) {
                    lIndicatedFileSize = tempBWFProc.getIndicatedFileSize();
                    lSampleRate = tempBWFProc.getSampleRate();
                    dDuration =  tempBWFProc.getDuration();
                    intChannels = tempBWFProc.getNoOfChannels();
                    dSourceSamples = tempBWFProc.getNoOfSamples()/intChannels;
                    strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET intIndicatedFileSize = " + lIndicatedFileSize + ", intSampleRate =  " + lSampleRate + ", dDuration =  " + dDuration + ", "
                            + "intChannels = " + intChannels + ", intLength = " + dSourceSamples + " "
                            + "WHERE intIndex = " + intSourceIndex + ";";
                    int i = st.executeUpdate(strSQL);
                    if (i == -1) {
                        System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                    }
                    if (tempBWFProc.getBextTitle().length() == 0) {
                        tempBWFProc.setBextTitle(strName);
                    }
                    if (tempBWFProc.getBextOriginatorRef().length() > 0) {
                        strUMID = URLEncoder.encode(tempBWFProc.getBextOriginatorRef(), "UTF-8");
                        strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET strUMID = \'" + strUMID + "\' WHERE intIndex = " + intSourceIndex + ";";
                        i = st.executeUpdate(strSQL);
                        if (i == -1) {
                            System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                        }
                    } else {
                        strUMID = jProjectTranslator.getNewUSID();
                        tempBWFProc.setBextOriginatorRef(strUMID);
                        strUMID = URLEncoder.encode(strUMID, "UTF-8");
                        strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET strUMID = \'" + strUMID + "\' WHERE intIndex = " + intSourceIndex + ";";
                        i = st.executeUpdate(strSQL);
                        if (i == -1) {
                            System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                        }
                    }
                    if (tempBWFProc.getBextTimeCodeOffset() > 0) {
                        lTimeCodeOffset = tempBWFProc.getBextTimeCodeOffset();
                        System.out.println("Timecode ref from source file is " +  lTimeCodeOffset);
                        strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET intTimeCodeOffset = " + lTimeCodeOffset + " WHERE intIndex = " + intSourceIndex + ";";
                        i = st.executeUpdate(strSQL);
                        if (i == -1) {
                            System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                        }
                    }
                    
                    lBWFProcessors.add(tempBWFProc);
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
        // DiskStreams are tracks in the edl, they can have one or more tracks and this corresponds to the ADL track map, e.g. 1~2
        Element xmlDiskStreams = xmlRoot.element("DiskStreams");
        Element xmlDiskStream;
        for (Iterator i = xmlDiskStreams.elementIterator("AudioDiskstream");i.hasNext();) {
            xmlDiskStream = (Element)i.next();
            parseDiskStreamData(xmlDiskStream, st);

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
        Element xmlRoutes = xmlRoot.element("Routes");
        Element xmlRoute;
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
                strSQL = "UPDATE FADER_LIST SET strLevel = " + String.format("%.2f", fY1) + " WHERE intTrack = " + intCurrentTrack + " AND "
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
                    intCurrentTrack + ", " + fX1 + ",\'" + String.format("%.2f", fY1) + "\') ;";
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
        if (xmlRoute.attributeValue("diskstream-id") == null) {
            return -1;
        }
        int intAudioDiskstreamIndex = Integer.parseInt(xmlRoute.attributeValue("diskstream-id"));
        // We're only interested in gain automation data at the moment
        if (xmlRoute.element("IO") != null && xmlRoute.element("IO").element("Automation") != null
                && xmlRoute.element("IO").element("Automation").element("AutomationList") != null
                && xmlRoute.element("IO").element("Automation").element("AutomationList").element("events") != null) {
            System.out.println("Found automation data on disk stream ID  " + intAudioDiskstreamIndex);
            // Found automation data for this track, need to check that there is not already region gain data
            strSQL = "SELECT COUNT(*) FROM PUBLIC.FADER_LIST,PUBLIC.TRACKS WHERE "
                    + "PUBLIC.FADER_LIST.intTrack = PUBLIC.TRACKS.intChannelOffset AND "
                    + "PUBLIC.TRACKS.intIndex = " + intAudioDiskstreamIndex + ";";
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
        strSQL = "SELECT intChannels, intChannelOffset FROM PUBLIC.TRACKS WHERE intIndex = " + intAudioDiskstreamIndex + ";";
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
                    (i + intMapOffset - 1) + ", " + (fTemp[0] ) + ",\'" + String.format("%.2f", 20*Math.log10(fTemp[1])) + "\') ;";
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
        int intPlaylistIndex = Integer.parseInt(xmlPlaylist.attributeValue("orig_diskstream_id"));
        int intChannelOffset = 1;
        strSQL = "SELECT intChannelOffset FROM PUBLIC.TRACKS WHERE intIndex = " + intPlaylistIndex + ";";
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
     * For maximum compatability these will be treated as mono. All editing systems can handle mono tracks, some can handle stereo but few can handle multichannel tracks.
     * The track map in the EVENT_LIST table can contain text like this '1 4' meaning source channel 1 goes to edl channel 4.
     * Stereo mapping looks like this, '1~2 3~4'
     * @param xmlRegion     This is an xml Element containing the region data
     * @param intMapOffset  Each audio track is offset by this number in the database
     * @param st            This allows the database to be updated.
     */
    protected void parseRegionData(Element xmlRegion, int intMapOffset, Statement st, int intTrackIndex) {
        /** Each region can consist of one or more channels. 
         */
        int intRegionIndex = Integer.parseInt(xmlRegion.attributeValue("id"));
        int intLayer = Integer.parseInt(xmlRegion.attributeValue("layer"));
        String strFlags = xmlRegion.attributeValue("flags");
        boolean bOpaque = (strFlags.indexOf("Opaque") > -1);
        String strOpaque;
        if (bOpaque) {
            strOpaque = "Y";
        } else {
            strOpaque = "N";
        }
        String strRemark = xmlRegion.attributeValue("name");
        try {
            strRemark = URLEncoder.encode(strRemark, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            System.out.println("Error on while trying to encode string" );
            return;
        }
        long lDestIn = (Long.parseLong(xmlRegion.attributeValue("position")));
        long lDestOut = (Long.parseLong(xmlRegion.attributeValue("length"))) + lDestIn;
        long lSourceIn = (Long.parseLong(xmlRegion.attributeValue("start")));
        String strType = "Cut";
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
                fOffset = java.lang.Math.round(Float.parseFloat(strKey));
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
                        + ", strInFade, intInFade, strOutFade, intOutFade, intRegionIndex, intLayer, intTrackIndex, bOpaque) VALUES (" +
                    intClipCounter++ + ", \'" + strType + "\',\'" + strRef + "\'," + intSourceIndex + ",\'" + strTrackMap + ""
                        + "\'," + lSourceIn + "," + lDestIn + "," + lDestOut + ",\'" + strRemark + "\', "
                        + "\'" + strInFade + "\', " + lInFade + ", \'" + strOutFade + "\', " + lOutFade + ", " + intRegionIndex + ""
                        + ", " + intLayer + ", " + intTrackIndex + ", \'" + strOpaque + "\') ;";
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
                    (i + intMapOffset - 1) + ", " + (fTemp[0] + lDestIn) + ",\'" + String.format("%.2f", 20*Math.log10(fTemp[1])) + "\') ;";
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
        String strName = xmlSource.attributeValue("name");
        String strFileName = strName;
        String strIndex = xmlSource.attributeValue("id");
        int intIndex = Integer.parseInt(strIndex);
        String strNameLowerCase = strName.toLowerCase();
        int intEnd = strNameLowerCase.lastIndexOf(".wav");
        if (intEnd == -1) {
            intEnd = strName.length();
        }
        strName = strName.substring(0, intEnd);
        strName = strName.replaceAll("[\\/:*?\"<>|%]","_");
        String strURI = strName + ".wav";
        int intChannel = Integer.parseInt(xmlSource.attributeValue("channel"));
//        String strURI = ".wav";
        try {
            strName = URLEncoder.encode(strName, "UTF-8");
//            strURI = URLEncoder.encode(strURI, "UTF-8");
            strFileName = URLEncoder.encode(strFileName, "UTF-8");
            strSQL = "INSERT INTO PUBLIC.SOURCE_INDEX (intIndex, strType, strDestFileName, strName, strSourceFile, intCopied, intLength, intFileOffset, intTimeCodeOffset, intArdourChannel) VALUES (" +
                intIndex + ", \'F\',\'" + strURI + "\', \'" + strName + "\', \'" + strFileName + "\', 0, 0, 0, 0," + intChannel + ") ;";
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
                + "The default audio file format is 32 bit float which is not always supported by other audio editors so you "
                + "should consider changing this in your ardour project. "
                + "Regions lying under other opaque regions are split into independent event list entries in the AES31 project.<br>";
    }
}
