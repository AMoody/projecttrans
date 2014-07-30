package jprojecttranslator;

import java.sql.Statement;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.ResultSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.dom4j.Element;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
/**
 * Project writer for ARDOUR projects.
 * This will write an ARDOUR file and a set subfolders, some of these will contain BWAV files.
 * The audio file format will not be changed.
 * @author arth
 */
public class jProjectWriter_ARDOUR extends jProjectWriter {
    /** This is the xml document object which is used for creating and saving to an xml file.*/
    static Document xmlDocument = DocumentHelper.createDocument();
    /** This is an xml formatter object to make the xml file human readable.*/
    static OutputFormat xmlFormat = OutputFormat.createPrettyPrint();
    /** Every item in an ardour project has to have a unique id number, this int is used to keep count. */
    int intIdCounter = 1;
    // This is the project name set by the file save as dialogue
    String strProjectName;
    String strMasterChannelInputString1 = "";
    String strMasterChannelInputString2 = "";
    // For debuggin we can turn off the writing of InFades and OutFades
    protected boolean bWriteInFades = true;
    protected boolean bWriteOutFades = true;
    
    
        /*
     * This returns a FileFilter which this class can read
     */
    @Override
    public javax.swing.filechooser.FileFilter getFileFilter() {
        javax.swing.filechooser.FileFilter filter = new FileNameExtensionFilter("Ardour (.ardour)", "ardour");
        return filter;
    }
    @Override
    protected boolean processProject() {
        System.out.println("ARDOUR writer thread running");
        /** Ardour requires ID numbers for each element in the file, calculate these numbers first */
        updateDatabaseForArdour();
        /**
         * Need to create a set of folders for the project, the file chooser could have returned a folder or file but ".ardour" will have been added to the end.
         * /projectname/interchange/
         * /projectname/analysis
         * /projectname/dead_sounds
         * /projectname/export
         * /projectname/peaks
         * /projectname/interchange/projectname/audiofiles
         * and the project file goes here
         * /projectname/projectname.ardour
         */
        int intEnd = fDestFile.getName().lastIndexOf(".ardour");
        strProjectName = fDestFile.getName().substring(0, intEnd);
        File fProjectFolder;
        // We test to see if a suitable parent folder already exists
        if (fDestFile.getParentFile().getName().equalsIgnoreCase(strProjectName)) {
            // A suitable parent folder exists
            fProjectFolder = fDestFile.getParentFile();
         } else {
            fProjectFolder = new File (fDestFile.getParent() + "/" + strProjectName);
        }
        System.out.println("fProjectFolder set to " + fProjectFolder);
        if (!fProjectFolder.exists()) {
            fProjectFolder.mkdir();
        }
        File fTempFolder = new File (fProjectFolder + "/analysis");
        if (!fTempFolder.exists()) {
            fTempFolder.mkdir();
        }
        fTempFolder = new File (fProjectFolder + "/dead_sounds");
        if (!fTempFolder.exists()) {
            fTempFolder.mkdir();
        }
        fTempFolder = new File (fProjectFolder + "/export");
        if (!fTempFolder.exists()) {
            fTempFolder.mkdir();
        }
        fTempFolder = new File (fProjectFolder + "/peaks");
        if (!fTempFolder.exists()) {
            fTempFolder.mkdir();
        }
        File fAudioFolder = new File (fProjectFolder + "/interchange");
        if (!fAudioFolder.exists()) {
            fAudioFolder.mkdir();
        }
        fAudioFolder = new File (fProjectFolder + "/interchange/" + strProjectName);
        if (!fAudioFolder.exists()) {
            fAudioFolder.mkdir();
        }
        fAudioFolder = new File (fProjectFolder + "/interchange/" + strProjectName + "/audiofiles");
        if (!fAudioFolder.exists()) {
            fAudioFolder.mkdir();
        }
        fDestFile = new File (fProjectFolder, strProjectName + ".ardour" );
        /**
        * Next step is to create an ardour file and write the output.
        */
        if (writeARDOURFile(fDestFile, st)) {
            oProjectTranslator.writeStringToPanel(java.util.ResourceBundle.getBundle("jprojecttranslator/Bundle").getString("jProjectWriter.ARDOURFileWritten"));
        } else {
            oProjectTranslator.writeStringToPanel(java.util.ResourceBundle.getBundle("jprojecttranslator/Bundle").getString("jProjectWriter.ARDOURFileFailed"));
        }
        if (writeAudioFiles(fAudioFolder)) {
            oProjectTranslator.writeStringToPanel(java.util.ResourceBundle.getBundle("jprojecttranslator/Bundle").getString("jProjectWriter.AudioFinished"));
        } else {
            oProjectTranslator.writeStringToPanel(java.util.ResourceBundle.getBundle("jprojecttranslator/Bundle").getString("jProjectWriter.AudioFileWriteFail"));
        }
        System.out.println("Ardour writer thread finished");
        return true;
    }
    
    /**
     * This creates the XML document and fills in all the elements. 
     * Then it saved the MXL file.
     * @param setDestFile The file and path where the file should be saved.
     * @param st
     * @return 
     */
    private boolean writeARDOURFile(File setDestFile, Statement st) {
        ResultSet rs;
        xmlDocument.clearContent();
        xmlDocument.addElement("Session");
        Element xmlRoot = xmlDocument.getRootElement();
        xmlRoot.addAttribute("version", "2.0.0");
        xmlRoot.addAttribute("sample-rate", "" + jProjectTranslator.intProjectSampleRate);
        Element xmlConfig = xmlRoot.addElement("Config");
        fillConfigElement(xmlConfig);
        Element xmlRegions = xmlRoot.addElement("Regions");
        Element xmlSources = xmlRoot.addElement("Sources");
        fillRegionsandSourcesElement(xmlRegions, xmlSources);
        
        Element xmlDiskStreams = xmlRoot.addElement("DiskStreams");
        Element xmlPlaylists = xmlRoot.addElement("Playlists");
        fillDiskStreamsandPlaylistsElement(xmlDiskStreams, xmlPlaylists);
        Element xmlLocations = xmlRoot.addElement("Locations");
        fillLocationsElement(xmlLocations);
        Element xmlConnections = xmlRoot.addElement("Connections");
        Element xmlRoutes = xmlRoot.addElement("Routes");
        fillRoutesElement(xmlRoutes);
        
        Element xmlEditGroups = xmlRoot.addElement("EditGroups");
        
        Element xmlMixGroups = xmlRoot.addElement("MixGroups");
        
        Element xmlUnusedPlaylists = xmlRoot.addElement("UnusedPlaylists");
        
        Element xmlTempoMap = xmlRoot.addElement("TempoMap");
        fillTempoMapElement(xmlTempoMap);
        
        
        Element xmlClick = xmlRoot.addElement("Click");
        xmlClick.add(getClickIOElement());
        
        
        xmlRoot.addAttribute("name", strProjectName);
        xmlRoot.addAttribute("id-counter","" + intIdCounter);
        return saveXMLFile(setDestFile);
    }
    /**
     * This saves the ardour XML file.
     * @param setDestFile This sets the path and name for the file.
     * @return true if the file is saved without errors.
     */
    private boolean saveXMLFile(File setDestFile) {
        try {
            xmlFormat.setNewlines(true);
            xmlFormat.setTrimText(false);
            XMLWriter writer = new XMLWriter(new FileWriter( setDestFile ), xmlFormat);
            writer.write( xmlDocument );
            writer.close();
        } catch ( IOException ioe ) {
            return false;
        }  
        return true;    //OK if we got this far
    } 
    /**
     * This fills the config element with default values.
     * @param xmlConfig 
     */
    private void fillConfigElement(Element xmlConfig) {
        xmlConfig.addElement("Option").addAttribute("name", "output-auto-connect").addAttribute("value", "2");
        xmlConfig.addElement("Option").addAttribute("name", "input-auto-connect").addAttribute("value", "1");
        xmlConfig.addElement("Option").addAttribute("name", "meter-falloff").addAttribute("value", "32");
        xmlConfig.addElement("Option").addAttribute("name", "native-file-header-format").addAttribute("value", "0");
        xmlConfig.addElement("end-marker-is-free").addAttribute("val", "yes");
    }
    /**
     * This fills the regions and sources elements.
     * These regions are in the region list, they might also be in the playlist (edl)
     * but we will create new regions entries for this.
     * We need to know how many audio tracks each region's sound file has.
     * @param xmlRegions
     * @param xmlSources 
     */
    private void fillRegionsandSourcesElement(Element xmlRegions, Element xmlSources){
        try {
            strSQL = "SELECT intIndex FROM PUBLIC.SOURCE_INDEX ORDER BY intIndex;";
            st = conn.createStatement();
            ResultSet rs = st.executeQuery(strSQL);
            while (rs.next()) {
                xmlRegions.add(getRegionElement(rs.getInt(1)));
            }
            strSQL = "SELECT intIndex FROM PUBLIC.ARDOUR_SOURCES ORDER BY intIndex;";
            st = conn.createStatement();
            rs = st.executeQuery(strSQL);
            while (rs.next()) {
                xmlSources.add(getSourceElement(rs.getInt(1)));
            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        } 
    }
    /**
     * This fills both the diskstreams and playlists element.
     * It's easier to fill these together as they contain closely related information.
     * @param xmlDiskStreams
     * @param xmlPlaylists 
     */
    private void fillDiskStreamsandPlaylistsElement(Element xmlDiskStreams, Element xmlPlaylists){
        // First we need to know how many tracks there are in the 
        // playlist by looking at the entries on the edl, these are in the EVENT_LIST table.
        ResultSet rs, rs2;
        Matcher mMatcher;
        Pattern pPatternChannelMap, pPatternChannels;
        pPatternChannelMap = Pattern.compile("(\\d*~\\d*|\\d*)\\s*(\\d*~\\d*|\\d*)"); // This should match the track map string, e.g. 1~2 3~4 etc
        pPatternChannels = Pattern.compile("(\\d*)~(\\d*)"); // This should match the track map string, e.g. 1~2 etc
        String strDestChannels;
        int intChannels = 1;
        int intChannelOffset = 0;
        int intExistingIndex;
        try {
            strSQL = "DELETE FROM PUBLIC.TRACKS;";
            int j = st.executeUpdate(strSQL);
            if (j == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }
            strSQL = "SELECT strTrackMap FROM PUBLIC.EVENT_LIST GROUP BY strTrackMap;";
            // strSQL = "SELECT strTrackMap FROM PUBLIC.EVENT_LIST GROUP BY strTrackMap ORDER BY MAX(intIndex);";
            // strSQL = "SELECT DISTINCT strTrackMap FROM PUBLIC.EVENT_LIST;";
            st = conn.createStatement();
            rs = st.executeQuery(strSQL);
            while (rs.next()) {
                mMatcher = pPatternChannelMap.matcher(rs.getString(1));
                if (mMatcher.find()) {
                    // The matcher should have the destination channels, e.g. 3~4 or just 4
                    strDestChannels = mMatcher.group(2);
                    // Try to find out how many channels there are
                    mMatcher = pPatternChannels.matcher(strDestChannels);
                    if (mMatcher.find()) {
                        intChannels = Integer.parseInt(mMatcher.group(2)) -  Integer.parseInt(mMatcher.group(1)) + 1;
                        intChannelOffset = Integer.parseInt(mMatcher.group(2));
                    } else {
                        intChannels = 1;
                        intChannelOffset = Integer.parseInt(strDestChannels);
                    }
                    // Before creating an entry in the tracks table we need to be sure it doesn't exist already.
                    strSQL = "SELECT COUNT(*) FROM PUBLIC.TRACKS WHERE strChannelMap = \'" + strDestChannels + "\';";
                    st = conn.createStatement();
                    rs2 = st.executeQuery(strSQL);
                    rs2.next();
                    if (!(rs2.wasNull()) ) {
                        if (rs2.getInt(1) == 0) {
                            // Need to calculate the channel offset so we can sort on it later to preserve the channel order.
                            strSQL = "INSERT INTO PUBLIC.TRACKS (intIndex, intChannels, strChannelMap, strName, intChannelOffset) VALUES (" + intIdCounter + ", " + intChannels + ", \'" + strDestChannels + "\', \'Audio " + strDestChannels + "\', " + intChannelOffset + ");";
                            j = st.executeUpdate(strSQL);
                            if (j == -1) {
                                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                            }

                            strSQL = "UPDATE PUBLIC.EVENT_LIST SET intTrackIndex = " + intIdCounter + " WHERE strTrackMap LIKE \'% " + strDestChannels + "\';";
                            j = st.executeUpdate(strSQL);
                            if (j == -1) {
                                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                            }
                            xmlDiskStreams.addElement("AudioDiskstream").addAttribute("flags", "Recordable").addAttribute("channels", "" + intChannels).addAttribute("playlist", "Audio " + strDestChannels + ".1").addAttribute("speed", "1").addAttribute("name", "Audio " + strDestChannels).addAttribute("id", "" + intIdCounter);
                            intIdCounter++;
                        } else {
                            // This track already exists
                            strSQL = "SELECT intIndex FROM PUBLIC.TRACKS WHERE strChannelMap = \'" + strDestChannels + "\';";
                            st = conn.createStatement();
                            rs2 = st.executeQuery(strSQL);
                            rs2.next();
                            intExistingIndex = rs2.getInt(1);
                            strSQL = "UPDATE PUBLIC.EVENT_LIST SET intTrackIndex = " + intExistingIndex + " WHERE strTrackMap LIKE \'% " + strDestChannels + "\';";
                            j = st.executeUpdate(strSQL);
                            if (j == -1) {
                                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                            }
                            xmlDiskStreams.addElement("AudioDiskstream").addAttribute("flags", "Recordable").addAttribute("channels", "" + intChannels).addAttribute("playlist", "Audio " + strDestChannels + ".1").addAttribute("speed", "1").addAttribute("name", "Audio " + strDestChannels).addAttribute("id", "" + intExistingIndex);
                            
                                    
                        }
                    }
                    // intAudioChannelID++;
                }
            }
            // The TRACK table is filled in and the AudioDiskstreams created
            strSQL = "SELECT intIndex, strName FROM PUBLIC.TRACKS ORDER BY intChannelOffset;";
            rs = st.executeQuery(strSQL);
            int intTrackIndex;
            String strTrackName;
            Element xmlPlaylist, xmlRegion;
            while (rs.next()) {
                intTrackIndex = rs.getInt(1);
                strTrackName = rs.getString(2);
                xmlPlaylist = xmlPlaylists.addElement("Playlist").addAttribute("name",strTrackName+ ".1").addAttribute("orig_diskstream_id","" + intTrackIndex).addAttribute("frozen", "no");
                strSQL = "SELECT intRegionIndex, strRemark, intSourceIndex, strTrackMap, intSourceIn, "
                        + "intDestIn, intDestOut, strInFade, intInFade, strOutFade, intOutFade, strGain FROM PUBLIC.EVENT_LIST WHERE intTrackIndex = " + intTrackIndex + ";";
                rs2 = st.executeQuery(strSQL);
                while (rs2.next()) {
                    // Get a region from the SOURCE_INDEX table which we can then modify
                    xmlRegion = getRegionElement(rs2.getInt(3));
                    // Need to find out how many audio tracks are in the region
                    mMatcher = pPatternChannelMap.matcher(rs2.getString(4));
                    intChannels = 1;
                    if (mMatcher.find()) {
                        // The matcher should have the destination channels, e.g. 3~4 or just 4
                        strDestChannels = mMatcher.group(2);
                        // Try to find out how many channels there are
                        mMatcher = pPatternChannels.matcher(strDestChannels);
                        if (mMatcher.find()) {
                            intChannels = Integer.parseInt(mMatcher.group(2)) -  Integer.parseInt(mMatcher.group(1)) + 1;
                        }
                    }
                    xmlRegion.attribute("channels").setValue("" + intChannels);
                    // Update the FadeIn element and flags if required.
                    // Opaque,DefaultFadeIn,DefaultFadeOut,Automatic,WholeFile,FadeIn,FadeOut
                    String strFlags = "";
                    fade fadeIn = new fade();
                    if (bWriteInFades && rs2.getString(8) != null && fadeIn.loadAES31Fade(rs2.getInt(9), rs2.getString(8), "in")) {
                        System.out.println("Parsed in fade" + fadeIn.getArdourFade(10));
                        xmlRegion.element("FadeIn").detach();
                        xmlRegion.add(fadeIn.getArdourFade(intIdCounter++));
                        strFlags = strFlags + "FadeIn,";
                    } else {
                        strFlags = strFlags + "DefaultFadeIn,FadeIn,";
                    }
                    fade fadeOut = new fade();
                    if (bWriteOutFades && rs2.getString(10) != null && fadeOut.loadAES31Fade(rs2.getInt(11), rs2.getString(10), "out")) {
                        System.out.println("Parsed out fade" + fadeOut.getArdourFade(10));
                        xmlRegion.element("FadeOut").detach();
                        xmlRegion.add(fadeOut.getArdourFade(intIdCounter++));
                        strFlags = strFlags + "FadeOut";
                    } else {
                        strFlags = strFlags + "DefaultFadeOut,FadeOut";
                    }
                    Float fGain;
                    if (rs2.getString(12) != null && rs2.getString(12).length() > 1) {
                        // Get the region gain and convert it to Ardour format
                        fGain = Float.parseFloat(rs2.getString(12));
                        fGain = (float)Math.pow(10,fGain/20);
                    } else {
                        fGain = (float)1;
                    }
                    
                    
                    xmlRegion.addAttribute("flags",strFlags);
                    xmlRegion.addAttribute("id","" + rs2.getInt(1)).addAttribute("start","" + rs2.getInt(5)).addAttribute("length","" + (rs2.getInt(7) - rs2.getInt(6))).addAttribute("position","" + rs2.getInt(6)).addAttribute("scale-gain","" + fGain);
                    xmlRegion.addElement("extra").addElement("GUI").addAttribute("waveform-visible","yes")
                            .addAttribute("envelope-visible","no").addAttribute("waveform-rectified","no").addAttribute("waveform-logscaled","no");
                    xmlPlaylist.add(xmlRegion);
//                    xmlPlaylist.addElement("Region").addAttribute("id","" + rs2.getInt(1)).addAttribute("name",URLDecoder.decode(rs2.getString(2), "UTF-8"))
//                            .addAttribute("start","" + rs2.getInt(5)).addAttribute("length","" + (rs2.getInt(7) - rs2.getInt(6))).addAttribute("position","" + rs2.getInt(6))
//                            .addAttribute("ancestral-start","0").addAttribute("ancestral-length","0").addAttribute("stretch","1")
//                            .addAttribute("shift","1").addAttribute("first_edit","nothing").addAttribute("layer","1").addAttribute("sync-position","0")
//                            .addAttribute("flags","Opaque,DefaultFadeIn,DefaultFadeOut").addAttribute("scale-gain","1")
//                            .addAttribute("source-0","" + rs2.getInt(3)).addAttribute("master-source-0","" + rs2.getInt(3)).addAttribute("channels","" + intChannels);
                }
                
            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
            
        } 
        
        
    }
    /**
     * This is used to fill the tempo map element with default values.
     * @param xmlTempoMap 
     */
    private void fillTempoMapElement(Element xmlTempoMap) {
        xmlTempoMap.addElement("Tempo").addAttribute("start","1|1|0").addAttribute("beats-per-minute","120.000000").addAttribute("note-type","4.000000").addAttribute("movable","no");
        xmlTempoMap.addElement("Meter").addAttribute("start","1|1|0").addAttribute("beats-per-bar","4.000000").addAttribute("note-type","4.000000").addAttribute("movable","no");
}
    /**
     * This fills the 'locations' XML element.
     * It tries to create the start and end markers in the right places by checking the first in and last out in the EDL.
     * @param xmlLocations This is the locations element to be filled.
     */
    private void fillLocationsElement(Element xmlLocations) {
        long lStart = 0;
        long lEnd = 14400000;
        try {
            strSQL = "SELECT MIN(intDestIn), MAX(intDestOut) FROM PUBLIC.EVENT_LIST;";
            ResultSet rs = st.executeQuery(strSQL);
            rs.next();
            if (!(rs.wasNull()) ) {
                lStart = rs.getLong(1);
                lEnd = rs.getLong(2);
            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        }    
        xmlLocations.addElement("Location").addAttribute("id","" + intIdCounter++).addAttribute("name","start").addAttribute("start","" + lStart).addAttribute("end","" + lStart).addAttribute("flags","IsMark,IsStart").addAttribute("locked","no");
        xmlLocations.addElement("Location").addAttribute("id","" + intIdCounter++).addAttribute("name","end").addAttribute("start","" + lEnd).addAttribute("end","" + lEnd).addAttribute("flags","IsMark,IsEnd").addAttribute("locked","no");
        xmlLocations.addElement("Location").addAttribute("id","" + intIdCounter++).addAttribute("name","Loop").addAttribute("start","" + lStart).addAttribute("end","" + lEnd).addAttribute("flags","IsAutoLoop,IsHidden").addAttribute("locked","no");
        xmlLocations.addElement("Location").addAttribute("id","" + intIdCounter++).addAttribute("name","Punch").addAttribute("start","" + lStart).addAttribute("end","" + lEnd).addAttribute("flags","IsAutoPunch,IsHidden").addAttribute("locked","no");
    }
    /**
     * This is used to update all of the index numbers for the various items in the Ardour file.
     * These must be unique with in the file.
     * It also creates the entries in the ARDOUR.SOURCES table using data from the SOURCE_INDEX table.
     */
    private void updateDatabaseForArdour() {
        // Start by getting the max ID number from the SOURCE_INDEX table.
        ResultSet rs;
        try {
            strSQL = "SELECT MAX(intIndex) FROM PUBLIC.SOURCE_INDEX;";
            st = conn.createStatement();
            rs = st.executeQuery(strSQL);
            rs.next();
            if (!(rs.wasNull()) ) {
                intIdCounter = rs.getInt(1) + 10;
            }
            // Create the intRegionIndex values in the EVENT_LIST table to avoid an overlap
            strSQL = "UPDATE PUBLIC.EVENT_LIST SET intRegionIndex = intIndex + " + intIdCounter + ";";
            int j = st.executeUpdate(strSQL);
            if (j == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }
            // Find the new max value from EVENT_LIST
            strSQL = "SELECT MAX(intRegionIndex) FROM PUBLIC.EVENT_LIST;";
            rs = st.executeQuery(strSQL);
            rs.next();
            if (!(rs.wasNull()) ) {
                intIdCounter = rs.getInt(1) + 10;
            }
            // Create the entries in the ARDOUR_SOURCES table
            strSQL = "SELECT intIndex, intChannels FROM PUBLIC.SOURCE_INDEX ORDER BY intIndex;";
            rs = st.executeQuery(strSQL);
            int intParentIndex, intChannels;
            while (rs.next()) {
                intParentIndex = rs.getInt(1);
                intChannels = rs.getInt(2);
                for(int i=1; i<intChannels + 1; i++){
                    strSQL = "INSERT INTO PUBLIC.ARDOUR_SOURCES (intIndex, intParentIndex, intChannel) VALUES (" + intIdCounter++ + ", " + intParentIndex + ", " + (i-1) + ");";
                    j = st.executeUpdate(strSQL);
                    if (j == -1) {
                        System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                    }
                }
            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        } 
    }
    
    /**
     * This creates an XML region element.
     * @param intRegionID This is the region ID used in the SQL query.
     * @return An XML element.
     */
    protected Element getRegionElement(int intRegionID) {
        Element xmlRegion = DocumentHelper.createElement("Region");
        try {
            strSQL = "SELECT intIndex, strName, intChannels, intLength, strDestFileName FROM PUBLIC.SOURCE_INDEX WHERE intIndex = " + intRegionID + ";";
            st = conn.createStatement();
            ResultSet rs = st.executeQuery(strSQL);
            String strName, strFileName;
            int intSourceIndex, intChannels;
            long lLength;
            while (rs.next()) {
                intSourceIndex = rs.getInt(1);
                strName = URLDecoder.decode(rs.getString(2), "UTF-8");
                strFileName = URLDecoder.decode(rs.getString(5), "UTF-8");
                intChannels = rs.getInt(3);
                lLength = rs.getLong(4);
                xmlRegion.addAttribute("id", "" + intSourceIndex).addAttribute("name", strName);
                xmlRegion.addAttribute("start", "0").addAttribute("length", "" + lLength);
                xmlRegion.addAttribute("position", "0").addAttribute("ancestral-start", "0");
                xmlRegion.addAttribute("ancestral-length", "0").addAttribute("stretch", "1");
                xmlRegion.addAttribute("shift", "1").addAttribute("first_edit", "nothing");
                xmlRegion.addAttribute("layer", "0").addAttribute("sync-position", "0");
                xmlRegion.addAttribute("flags", "Opaque,DefaultFadeIn,DefaultFadeOut,Automatic,WholeFile,FadeIn,FadeOut").addAttribute("scale-gain", "1");
                // Add the sources next
                strSQL = "SELECT intIndex, intParentIndex, intChannel FROM PUBLIC.ARDOUR_SOURCES WHERE intParentIndex = " + intRegionID + ";";
                ResultSet rs2 = st.executeQuery(strSQL);
                while (rs2.next()) {
                    xmlRegion.addAttribute("source-" + rs2.getInt(3), "" + rs2.getInt(1));
                    xmlRegion.addAttribute("mastersource-" + rs2.getInt(3), "" + rs2.getInt(1));
                }
                xmlRegion.addAttribute("channels", "" + intChannels);
                xmlRegion.addElement("FadeIn").addAttribute("default", "yes").addAttribute("active", "yes");
                xmlRegion.addElement("FadeOut").addAttribute("default", "yes").addAttribute("active", "yes");
                xmlRegion.addElement("Envelope").addAttribute("default", "yes");
                
            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        } catch (java.io.UnsupportedEncodingException e) {
            System.out.println("Error on URL decode " + e.toString());
        }
        return xmlRegion;
        
    }
    /**
     * This creates an XML source element from the database.
     * @param intSourceID This sets the source ID which is used in the SQL query.
     * @return An XML source element.
     */
    protected Element getSourceElement(int intSourceID) {
        Element xmlSource = DocumentHelper.createElement("Source");
        try {
            strSQL = "SELECT intChannel, strDestFileName FROM PUBLIC.ARDOUR_SOURCES, PUBLIC.SOURCE_INDEX WHERE PUBLIC.ARDOUR_SOURCES.intIndex = " + intSourceID + ""
                    + " AND PUBLIC.SOURCE_INDEX.intIndex = intParentIndex;";
            st = conn.createStatement();
            ResultSet rs = st.executeQuery(strSQL);
            String strFileName;
            int intChannel;
            while (rs.next()) {
                intChannel = rs.getInt(1);
                strFileName = URLDecoder.decode(rs.getString(2), "UTF-8");
                xmlSource.addAttribute("name", strFileName).addAttribute("id", "" + intSourceID).addAttribute("flags", "Broadcast").addAttribute("channel", "" + intChannel);
            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        } catch (java.io.UnsupportedEncodingException e) {
            System.out.println("Error on URL decode " + e.toString());
        }
        return xmlSource;
    }
    /**
     * This writes all the audio files which have been found in to the folder specified.
     * @param setAudioFolder This is the destination folder for the files.
     * @return true if all the files are written without any errors.
     */
    protected boolean writeAudioFiles(File setAudioFolder) {
        File fAudioFolder = setAudioFolder;
        File fDestFile;
        BWFProcessor tempBWFProcessor;
        ResultSet rs;
        String strUMID;
        String strDestFileName;
        try {
            Iterator itr = lBWFProcessors.iterator();
             while(itr.hasNext()) {
                 tempBWFProcessor = (BWFProcessor)itr.next();
                 strUMID = URLEncoder.encode(tempBWFProcessor.getBextOriginatorRef(), "UTF-8");
                 strSQL = "SELECT strDestFileName FROM PUBLIC.SOURCE_INDEX WHERE strUMID = \'" + strUMID + "\';";
                 st = conn.createStatement();
                 rs = st.executeQuery(strSQL);
                 rs.next();
                 // Get the raw file name string
                 strDestFileName = rs.getString(1);
                 // It has been URL encoded to trap nasty characters from the database
                 strDestFileName = URLDecoder.decode(strDestFileName, "UTF-8");
                 fDestFile = new File (fAudioFolder,strDestFileName);
                 
                 // Check that the sample rate of the file is the same as the current project sample rate, if not write the file to a subfolder.
                if (tempBWFProcessor.getSampleRate() != jProjectTranslator.intProjectSampleRate) {
                    String strNewFolder = fDestFile.getParent() + "/WRONG_FORMAT";
                    String strFileName = fDestFile.getName();
                    File fNewFolder = new File(strNewFolder);
                    if (fNewFolder.exists()) {
                        fDestFile = new File(strNewFolder,strFileName);
                    } else {
                        if (fNewFolder.mkdir()) {
                            fDestFile = new File(strNewFolder,strFileName);
                        } else {
                            System.out.println("Failed to create subfolder for file with wrong sample rate " + fNewFolder);
                        }
                    }
                    
                    
                }
                if (fDestFile.exists()) {
                    strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET intCopied = intIndicatedFileSize WHERE strUMID = \'" + strUMID + "\';";
                    //                    System.out.println("SQL " + strSQL);
                    int i = st.executeUpdate(strSQL);
                    if (i == -1) {
                        System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                    }
                    setChanged();
                    notifyObservers();
                    continue;
                 }
                 System.out.println("Starting audio file write on dest file " + strDestFileName);
                 oProjectTranslator.writeStringToPanel(java.util.ResourceBundle.getBundle("jprojecttranslator/Bundle").getString("jProjectWriter.WritingAudioFile") + strDestFileName);
        
                 tempBWFProcessor.addObserver(this);
                 tempBWFProcessor.writeFile(fDestFile);
                 tempBWFProcessor.deleteObserver(this);
             }
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
            return false;
        } catch (java.io.UnsupportedEncodingException e) {
            System.out.println("Error on decoding at " + strSQL + e.toString());
            return false;
        }
        return true;
    }
    /**
     * Fills the routes element which contains the settings for the mixer.
     * @param xmlRoutes The is the (empty) routes element which will be filled.
     */        
    protected void fillRoutesElement(Element xmlRoutes) {
        /**
         * We need to build a default mixer with all tracks routed to the output
         */ 
        strMasterChannelInputString1 = "";
        strMasterChannelInputString2 = "";
        Element xmlRoute;
        String strName;
        int intChannels;
        int intEditor = 1;
        try {
            strSQL = "SELECT intIndex, intChannels, strChannelMap, strName FROM PUBLIC.TRACKS ORDER BY intChannelOffset;";
            ResultSet rs = st.executeQuery(strSQL);
            while (rs.next()) {
                xmlRoute = xmlRoutes.addElement("Route");
                xmlRoute.addAttribute("diskstream-id", "" + rs.getInt(1)).addAttribute("default-type", "audio");
                xmlRoute.addAttribute("muted", "no").addAttribute("soloed", "no").addAttribute("phase-invert", "no").addAttribute("denormal-protection", "no")
                        .addAttribute("mute-affects-pre-fader", "yes").addAttribute("mute-affects-post-fader", "yes")
                        .addAttribute("mute-affects-control-outs", "yes").addAttribute("mute-affects-main-outs", "yes")
                        .addAttribute("meter-point", "MeterPostFader").addAttribute("order-keys", "editor=" + intEditor + ":signal=" + intEditor++).addAttribute("mode", "Normal");
                strName = URLDecoder.decode(rs.getString(4), "UTF-8");
                intChannels = rs.getInt(2);
                xmlRoute.add(getIOElement(strName, intChannels, rs.getString(3)));
                xmlRoute.addElement("controllable").addAttribute("name", "solo").addAttribute("id","" + intIdCounter++);
                xmlRoute.addElement("controllable").addAttribute("name", "mute").addAttribute("id","" + intIdCounter++);
                xmlRoute.addElement("remote_control").addAttribute("id","" + intIdCounter++);
//                Element xmlGUI = xmlRoute.addElement("extra").addElement("GUI");
//                xmlGUI.addAttribute("color","9164:18363:2620").addAttribute("shown_mixer","yes").addAttribute("height","66").addAttribute("shown_editor","yes");
//                xmlGUI.addElement("gain").addAttribute("shown","no").addAttribute("height","66");
//                xmlGUI.addElement("pan").addAttribute("shown","no").addAttribute("height","66");
            }
            // Mixer strips for the tracks have been added, need to add a master channel too
            // Save the strMasterChannelInputString string before using getIOElement again
            String strTempMasterChannelInputString1 = strMasterChannelInputString1;
            int intEnd = strTempMasterChannelInputString1.lastIndexOf(",");
            if (intEnd > 0) {
                strTempMasterChannelInputString1 = "{" + strTempMasterChannelInputString1.substring(0, intEnd) + "}";
            } else {
                strTempMasterChannelInputString1 = "{}";
            }
            String strTempMasterChannelInputString2 = strMasterChannelInputString2;
            intEnd = strTempMasterChannelInputString2.lastIndexOf(",");
            if (intEnd > 0) {
                strTempMasterChannelInputString2 = "{" + strTempMasterChannelInputString2.substring(0, intEnd) + "}";
            } else {
                strTempMasterChannelInputString2 = "{}";
            }
            
            Element xmlMasterRoute = xmlRoutes.addElement("Route");
            xmlMasterRoute.addAttribute("flags", "MasterOut").addAttribute("default-type", "audio");
            xmlMasterRoute.addAttribute("muted", "no").addAttribute("soloed", "no").addAttribute("phase-invert", "no").addAttribute("denormal-protection", "no")
                        .addAttribute("mute-affects-pre-fader", "yes").addAttribute("mute-affects-post-fader", "yes")
                        .addAttribute("mute-affects-control-outs", "yes").addAttribute("mute-affects-main-outs", "yes")
                        .addAttribute("meter-point", "MeterPostFader").addAttribute("order-keys", "editor=0:signal=0");
            Element xmlMasterIO = getIOElement("master", 2, "0");
            xmlMasterIO.addAttribute("inputs", strTempMasterChannelInputString1 + strTempMasterChannelInputString2);
            xmlMasterIO.addAttribute("outputs", "{system:playback_1}{system:playback_2}").addAttribute("iolimits","-1,2,-1,2");
            xmlMasterRoute.add(xmlMasterIO);
            xmlMasterRoute.addElement("controllable").addAttribute("name", "solo").addAttribute("id","" + intIdCounter++);
            xmlMasterRoute.addElement("controllable").addAttribute("name", "mute").addAttribute("id","" + intIdCounter++);
            xmlMasterRoute.addElement("remote_control").addAttribute("id","" + intIdCounter++);
//            Element xmlGUI = xmlMasterRoute.addElement("extra").addElement("GUI");
//                xmlGUI.addAttribute("color","9164:18363:2620").addAttribute("shown_mixer","yes").addAttribute("height","66").addAttribute("shown_editor","yes");
//                xmlGUI.addElement("gain").addAttribute("shown","no").addAttribute("height","66");
//                xmlGUI.addElement("pan").addAttribute("shown","no").addAttribute("height","66");
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        } catch (java.io.UnsupportedEncodingException e) {
            System.out.println("Error on URL decode " + e.toString());
        }
        
            
        
    }
    /**
     * Creates an IO element.
     * @param strName Sets the name of the IO element.
     * @param intChannels Sets the number of channels in the element.
     * @param strTrackMap Sets the track map so the gain automation for this channel can be found.
     * @return 
     */
    protected Element getIOElement(String strName, int intChannels, String strTrackMap) {
        // strMasterChannelInputString
        Element xmlIO = DocumentHelper.createElement("IO");
        String strChannelOutputString = "";
        String strChannelInputString = "";
        strMasterChannelInputString1 = strMasterChannelInputString1 + strName + "/out 1,";
        strMasterChannelInputString2 = strMasterChannelInputString2 + strName + "/out 2," ;
        for(int i=1; i<intChannels + 1; i++){
            strChannelOutputString = strChannelOutputString + "{master/in " + i + "}";
            strChannelInputString = strChannelInputString + "{}";
            
        }
        // Always have a minimum of two outputs from a channel even if it's mono.
        if (intChannels == 1) {
            strChannelOutputString = strChannelOutputString + "{master/in 2}";
        }
        xmlIO.addAttribute("name", strName).addAttribute("id","" + intIdCounter++).addAttribute("active","yes").addAttribute("inputs",strChannelInputString)
                .addAttribute("outputs",strChannelOutputString).addAttribute("gain","1.000000000000").addAttribute("iolimits","1,-1,-1,-1");
        
        Element xmlPanner = xmlIO.addElement("Panner").addAttribute("linked","no").addAttribute("link_direction","SameDirection").addAttribute("bypassed","no");
        xmlPanner.addElement("Output").addAttribute("x","0").addAttribute("y","0");
        xmlPanner.addElement("Output").addAttribute("x","1").addAttribute("y","0");
//        if (intChannels > 1) {
//            for(int i=1; i<intChannels + 1; i++){
//                xmlPanner.add(getStreamPanner((i-1)/(intChannels-1)));
//            }
//        } else {
//            xmlPanner.add(getStreamPanner((float)0.5));
//        }  
        Element xmlStreamPanner0 = xmlPanner.addElement("StreamPanner").addAttribute("x","0").addAttribute("type","Equal Power Stereo").addAttribute("muted","no");
        xmlStreamPanner0.addElement("Automation").addElement("AutomationList").addAttribute("id","" + intIdCounter++)
                .addAttribute("default","0").addAttribute("min_yval","0").addAttribute("max_yval","1").addAttribute("max_xval","0").addAttribute("state","Off").addAttribute("style","Absolute");
        xmlStreamPanner0.addElement("controllable").addAttribute("name","panner").addAttribute("id","" + intIdCounter++);
        
        Element xmlStreamPanner1 = xmlPanner.addElement("StreamPanner").addAttribute("x","1").addAttribute("type","Equal Power Stereo").addAttribute("muted","no");
        xmlStreamPanner1.addElement("Automation").addElement("AutomationList").addAttribute("id","" + intIdCounter++)
                .addAttribute("default","1").addAttribute("min_yval","0").addAttribute("max_yval","1").addAttribute("max_xval","0").addAttribute("state","Off").addAttribute("style","Absolute");
        xmlStreamPanner1.addElement("controllable").addAttribute("name","panner").addAttribute("id","" + intIdCounter++);
        
        xmlIO.addElement("controllable").addAttribute("name","gaincontrol").addAttribute("id","" + intIdCounter++);
        
//        xmlIO.addElement("Automation").addElement("AutomationList").addAttribute("id","" + intIdCounter++)
//                .addAttribute("default","1").addAttribute("min_yval","0").addAttribute("max_yval","2").addAttribute("max_xval","0").addAttribute("state","Off").addAttribute("style","Absolute");
        xmlIO.addElement("Automation").add(getAutomationListForIO(strTrackMap));
        return xmlIO;
    }
    /**
     * Creates an IO element in the format used by the 'click' element.
     * @return an XML IO element.
     */
    protected Element getClickIOElement() {
        String strName = "click";
        Element xmlIO = DocumentHelper.createElement("IO");
        String strChannelOutputString = "";
        String strChannelInputString = "{system:playback_1}{system:playback_2}";
        xmlIO.addAttribute("name", strName).addAttribute("id","" + intIdCounter++).addAttribute("active","yes").addAttribute("inputs",strChannelInputString)
                .addAttribute("outputs",strChannelOutputString).addAttribute("gain","1.000000000000").addAttribute("iolimits","0,0,-1,-1");
        
        Element xmlPanner = xmlIO.addElement("Panner").addAttribute("linked","no").addAttribute("link_direction","SameDirection").addAttribute("bypassed","no");
        xmlPanner.addElement("Output").addAttribute("x","0").addAttribute("y","0");
        xmlPanner.addElement("Output").addAttribute("x","1").addAttribute("y","0");
        Element xmlStreamPanner0 = xmlPanner.addElement("StreamPanner").addAttribute("x","0.5").addAttribute("type","Equal Power Stereo").addAttribute("muted","no");
        xmlStreamPanner0.addElement("Automation").addElement("AutomationList").addAttribute("id","" + intIdCounter++)
                .addAttribute("default","0.5").addAttribute("min_yval","0").addAttribute("max_yval","1").addAttribute("max_xval","0").addAttribute("state","Off").addAttribute("style","Absolute");
        xmlStreamPanner0.addElement("controllable").addAttribute("name","panner").addAttribute("id","" + intIdCounter++);
        
        Element xmlStreamPanner1 = xmlPanner.addElement("StreamPanner").addAttribute("x","0.5").addAttribute("type","Equal Power Stereo").addAttribute("muted","no");
        xmlStreamPanner1.addElement("Automation").addElement("AutomationList").addAttribute("id","" + intIdCounter++)
                .addAttribute("default","0.5").addAttribute("min_yval","0").addAttribute("max_yval","1").addAttribute("max_xval","0").addAttribute("state","Off").addAttribute("style","Absolute");
        xmlStreamPanner1.addElement("controllable").addAttribute("name","panner").addAttribute("id","" + intIdCounter++);
        
        xmlIO.addElement("controllable").addAttribute("name","gaincontrol").addAttribute("id","" + intIdCounter++);
        
        xmlIO.addElement("Automation").addElement("AutomationList").addAttribute("id","" + intIdCounter++)
                .addAttribute("default","1").addAttribute("min_yval","0").addAttribute("max_yval","2").addAttribute("max_xval","0").addAttribute("state","Off").addAttribute("style","Absolute");
        return xmlIO;
    }
    /**
     * Creates a 'Stream Panner' element
     * @param fDefaultValue This sets the default value for the panner
     * @return An XML element with a StreamPanner.
     */
    protected Element getStreamPanner(float fDefaultValue) {
        Element xmlStreamPanner = DocumentHelper.createElement("StreamPanner");
        String strDefaultValue = String.format(Locale.UK,"%.1f", fDefaultValue);
        xmlStreamPanner.addAttribute("x",strDefaultValue).addAttribute("type","Equal Power Stereo").addAttribute("muted","no");
        xmlStreamPanner.addElement("Automation").addElement("AutomationList").addAttribute("id","" + intIdCounter++)
                .addAttribute("default",strDefaultValue).addAttribute("min_yval","0").addAttribute("max_yval","1").addAttribute("max_xval","0").addAttribute("state","Off").addAttribute("style","Absolute");
        xmlStreamPanner.addElement("controllable").addAttribute("name","panner").addAttribute("id","" + intIdCounter++);
        return xmlStreamPanner;
        
    }
    /**
     * This creates an automation list in the format used in the IO element.
     * @param strChannelMap The channel map is used to find which audio channel in the FADER.LIST table is used.
     * @return An XML element containing the gain automation for this channel.
     */
    protected Element getAutomationListForIO (String strChannelMap) {
        Element xmlAutomationList = DocumentHelper.createElement("AutomationList");
        Element xmlEvents = DocumentHelper.createElement("events");
        String strEvents = "";
        xmlAutomationList.addAttribute("id","" + intIdCounter++)
                .addAttribute("default","1").addAttribute("min_yval","0").addAttribute("max_yval","2")
                .addAttribute("max_xval","0").addAttribute("style","Absolute").addAttribute("state","Off");
        // Find the first track from the track map, use this for automation
        int intFirstChannel;
        long lTime;
        float fLevel;
        Matcher mMatcher;
        Pattern pPatternChannels;
        pPatternChannels = Pattern.compile("(\\d*)"); // This should match the first digit of the track map string, e.g. 1~2 etc
        mMatcher = pPatternChannels.matcher(strChannelMap);
        if (mMatcher.find() && Integer.parseInt(mMatcher.group(1)) > 0) {
            intFirstChannel = Integer.parseInt(mMatcher.group(1));
            try {
                strSQL = "SELECT intTime, strLevel FROM PUBLIC.FADER_LIST WHERE intTrack = " + intFirstChannel + " ORDER BY intTime;";
                ResultSet rs = st.executeQuery(strSQL);
                while (rs.next()) {
                    lTime = rs.getLong(1);
                    fLevel = Float.parseFloat(rs.getString(2));
                    // fLevel = (float)Math.exp(fLevel/20);
                    fLevel = (float)Math.pow(10,fLevel/20);
                    strEvents = strEvents + "" + lTime + " " + fLevel + "\n";
                }
            } catch (java.sql.SQLException e) {
                System.out.println("Error on SQL " + strSQL + e.toString());
            }
            if (strEvents.length() > 0) {
                xmlEvents.addText(strEvents);
                xmlAutomationList.addAttribute("state","Play");
                xmlAutomationList.add(xmlEvents);
            }
        } 
        return xmlAutomationList;
        
        
    }
    
    /** This is used to get text information which is shown in the Help/About dialogue box.
     * @return The information text.
     */
    public String getInfoText() {
        return "<b>Ardour</b><br>"
                + "This exporter will write an Ardour project with all the required folders.<br>"
                + "If the chosen folder does not have the same name as the name chosen for the Ardour project then a new subfolder will be created.<br>"
                + "If the required audio files have been found these will also be copied to the correct subfolder.<br>"
                + "If the audio files are not BWAVs then a bext chunk will be added.<br>"
                + "Fades are written as region fades, gain automation will be written as mixer gain automation.<br>"
                + "<br>";
    }
}
