/*
 * This is used to open and parse an ardour project file and load
 * the data in to the internal database
 */
package jprojecttranslator;

import java.io.StringReader;
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
 *
 * @author arth
 */
public class jProjectReader_ARDOUR extends jProjectReader {
    /** This is the xml document object which is used for loading and saving to an xml file.*/
    static Document xmlDocument = DocumentHelper.createDocument();
    DateTime dtsCreated;
        
    /*
     * This returns a FileFilter which this class can read
     */
    public javax.swing.filechooser.FileFilter getFileFilter() {
        javax.swing.filechooser.FileFilter filter = new FileNameExtensionFilter("Ardour (.ardour)", "ardour");
        return filter;
    }    
    
    protected boolean processProject() {
        dtsCreated = new DateTime(fSourceFile.lastModified(), DateTimeZone.getDefault());
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
    
    /** This method will load the XML data in to a document held in memory.
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
    protected int loadSoundFiles(Statement st, File fAudioFolder) {
        try {
            strSQL = "SELECT intIndex, strName, strSourceFile FROM PUBLIC.SOURCE_INDEX ORDER BY intIndex;";
            st = conn.createStatement();
            ResultSet rs = st.executeQuery(strSQL);
            String strSourceFile, strName, strUMID;
            File fLocalSourceFile;
            long lIndicatedFileSize, lSampleRate, lSourceFileSize, lTimeCodeOffset;
            double dDuration;
            int intSourceIndex;
            while (rs.next()) {
                // Loop through the SOURCE_INDEX table and try to find out more about each file by reading data from the actual sound file (if we can find it)
                intSourceIndex = rs.getInt(1);
                strName = URLDecoder.decode(rs.getString(2), "UTF-8");
                strSourceFile = URLDecoder.decode(rs.getString(3), "UTF-8");
                fLocalSourceFile = new File(fAudioFolder, strSourceFile);
                tempBWFProc = new BWFProcessor();
                tempBWFProc.setSrcFile(fLocalSourceFile);
                tempBWFProc.setMultipart(false);
                if (fLocalSourceFile.exists() && fLocalSourceFile.canRead() && tempBWFProc.readFile(0,fLocalSourceFile.length())) {
                    lIndicatedFileSize = tempBWFProc.getIndicatedFileSize();
                    lSampleRate = tempBWFProc.getSampleRate();
                    dDuration =  tempBWFProc.getDuration();
                    strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET intIndicatedFileSize = " + lIndicatedFileSize + ", intSampleRate =  " + lSampleRate + ", dDuration =  " + dDuration + " "
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
        return 0;
    }
    /*
     * This method parses the xml fields from the VCS project and adds the
     * information to the database
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
        int intSampleRate = Integer.parseInt(strSampleRate);
        if (jProjectTranslator.intSampleRate != intSampleRate) {
            oProjectTranslator.writeStringToPanel("This project is not at your preferred sample rate so it can not be opened, the project rate is " + intSampleRate);
            return false;
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
            parseSourceData(xmlSource, st, fAudioFolder);
        }
        // Need to look at the sound files now to get further information
        loadSoundFiles(st, fAudioFolder);
        // DiskStreams are tracks in the edl, they can have one or more tracks and this corresponds to the ADL track map, e.g. 1~2
        Element xmlDiskStreams = xmlRoot.element("DiskStreams");
        Element xmlDiskStream;
        for (Iterator i = xmlDiskStreams.elementIterator("AudioDiskstream");i.hasNext();) {
            xmlDiskStream = (Element)i.next();
            parseDiskStreamData(xmlDiskStream, st, fSourceFolder);

        }
        // Playlists are the tracks in the EDL and these contain regions
        Element xmlPlaylists = xmlRoot.element("Playlists");
        Element xmlPlaylist;
        for (Iterator i = xmlPlaylists.elementIterator("Playlist");i.hasNext();) {
            xmlPlaylist = (Element)i.next();
            parsePlaylistData(xmlPlaylist, st, fSourceFolder);

        }
        
        return true;
    } 
    protected void parseDiskStreamData(Element xmlDiskStream, Statement st, File fSourceFolder) {
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
    protected void parsePlaylistData(Element xmlPlaylist, Statement st, File fSourceFolder) {
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
                parseRegionData(xmlRegion, intChannelOffset, st);

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
    protected void parseCrossfadeData(Element xmlCrossfade, Statement st) {
        if (xmlCrossfade.attributeValue("active")!= null && xmlCrossfade.attributeValue("active").equalsIgnoreCase("no") ) {
            return;
        }
        int intCrossfadeOut = Integer.parseInt(xmlCrossfade.attributeValue("out"));
        int intCrossfadeIn = Integer.parseInt(xmlCrossfade.attributeValue("in"));
        long lCrossfade = Long.parseLong(xmlCrossfade.attributeValue("length"));
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
                strSQL = "UPDATE PUBLIC.EVENT_LIST SET strInFade = \'" + strInFade + "\', intInFade = " + lCrossfade + " WHERE intRegionIndex = " + intCrossfadeIn + ";";
                int j = st.executeUpdate(strSQL);
                if (j == -1) {
                    System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                }
            }
            if (strOutFade.length() > 0){
                strSQL = "UPDATE PUBLIC.EVENT_LIST SET strOutFade = \'" + strOutFade + "\', intOutFade = " + lCrossfade + " WHERE intRegionIndex = " + intCrossfadeOut + ";";
                int j = st.executeUpdate(strSQL);
                if (j == -1) {
                    System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                }
            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
            return;
        
        }
        
    }
    protected void parseRegionData(Element xmlRegion, int intMapOffset, Statement st) {
        /** Each region can consist of one or more channels. For maximum compatability
         * these will be treated as mono. All editing systems can handle mono tracks, some can handle stereo but few can handle multichannel tracks.
         * The track map in the EVENT_LIST table can contain text like this '1 4' meaning source channel 1 goes to edl channel 4.
         * Stereo mapping looks like this, '1~2 3~4'
         */
        int intRegionIndex = Integer.parseInt(xmlRegion.attributeValue("id"));
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
        // Get the fade in values
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
            strTrackMap = strSourceChannel + " " + strDestChannel;
            intSourceIndex = Integer.parseInt(xmlRegion.attributeValue("source-" + (i-1)));
            
            try {
                strSQL = "INSERT INTO PUBLIC.EVENT_LIST (intIndex, strType, strRef, intSourceIndex, strTrackMap, intSourceIn, intDestIn, intDestOut, strRemark"
                        + ", strInFade, intInFade, strOutFade, intOutFade, intRegionIndex) VALUES (" +
                    intClipCounter++ + ", \'" + strType + "\',\'" + strRef + "\'," + intSourceIndex + ",\'" + strTrackMap + ""
                        + "\'," + lSourceIn + "," + lDestIn + "," + lDestOut + ",\'" + strRemark + "\', "
                        + "\'" + strInFade + "\', " + lInFade + ", \'" + strOutFade + "\', " + lOutFade + ", " + intRegionIndex + ") ;";
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
    protected void parseSourceData(Element xmlSource, Statement st, File fAudioFolder) {
        // The ardour file does not contain much information about the source files, need to find out more later from the actual file
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
        strName = strName.replaceAll("[\\/:*?\"<>|]","_");
        String strURI = strName + ".wav";
        try {
            strName = URLEncoder.encode(strName, "UTF-8");
            strURI = URLEncoder.encode(strURI, "UTF-8");
            strFileName = URLEncoder.encode(strFileName, "UTF-8");
            strSQL = "INSERT INTO PUBLIC.SOURCE_INDEX (intIndex, strType, strDestFileName, strName, strSourceFile, intCopied, intLength, intFileOffset, intTimeCodeOffset) VALUES (" +
                intIndex + ", \'F\',\'" + strURI + "\', \'" + strName + "\', \'" + strFileName + "\', 0, 0, 0, 0) ;";
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
}
