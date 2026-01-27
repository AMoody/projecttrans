/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jprojecttranslator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.filechooser.FileNameExtensionFilter;
import static jprojecttranslator.jProjectReader_VCS.lInitialAudioOffset;
import static jprojecttranslator.jProjectTranslator.ourDatabase;
import org.dom4j.Element;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import wavprocessor.WAVProcessor;

/**
 * Project writer for Samplitude EDL projects.
 * This will write an EDL file and a set of BWAV files.
 * If the source files did not have a bext chunk then this will be added.
 * The EDL format is Samplitude EDL File Format Version 1.5 which can be used by Reaper
 * The audio file format will not be changed.
 * @author arth
 */
public class jProjectWriter_EDL1 extends jProjectWriter {
    public static DateTimeFormatter fmtADLXML = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssZZ");
    private int intIdCounter = 0;
    /*
     * This returns a FileFilter which this class can read
     */
    public javax.swing.filechooser.FileFilter getFileFilter() {
        javax.swing.filechooser.FileFilter filter = new FileNameExtensionFilter("Samplitide EDL (.edl)", "edl");
        return filter;
    } 
    protected boolean processProject() {
        System.out.println("EDL1 writer thread running");
        // Clear the TRACKS table
        // The AES31 writer doesn't need the tracks table except to use it to keep of newly created tracks.
        // The Ardour writer deletes and recreates the data in the TRACKS table.
        rebuildTracksTable();
        // Fill in the URI information
        writeURIs ();
        // Move overlapping tracks
        int intCounter = 0;
        int intMovedTracks = 0;
        do {
            intMovedTracks = moveSubordinateClips(st);
            intCounter++;
            
        } while (intMovedTracks > 0 && intCounter < 500);
        /**
        * Next step is to create an EDL file and write the output.
        */
        writeEDLFile(fDestFile, st);
        String strHTMLFile = new String(fDestFile.toString() + ".html");
        oProjectTranslator.writeStringToPanel(java.util.ResourceBundle.getBundle("jprojecttranslator/Bundle").getString("jProjectWriter.ADLFileWritten"));
        writeAudioFiles ();
        oProjectTranslator.writeStringToPanel(java.util.ResourceBundle.getBundle("jprojecttranslator/Bundle").getString("jProjectWriter.Finished"));
        System.out.println("EDL1 writer thread finished");
        HTMLFileWriter ourHTMLFileWriter = new HTMLFileWriter(strHTMLFile, ourDatabase);
        if (ourHTMLFileWriter.bIsValid) {
            ourHTMLFileWriter.writeFile();
        }
        return true;
    }

    private boolean writeEDLFile(File setDestFile, Statement st) {
        String strEDLText = "Samplitude EDL File Format Version 1.5\n";
        String str12Space = "            ";
        String str8Space = "        ";
        String str4Space = "    ";
        String str3Space = "   ";
        List listOldSourceIndexes = new ArrayList();
        List listOldTrackIndexes = new ArrayList();
        int intNewIndex = 1;
        try {
            
            // Fill in the VERSION tags
//            strSQL = "SELECT strADLVersion, strCreator, strCreatorVersion FROM PUBLIC.VERSION;";
            st = conn.createStatement();
            ResultSet rs = st.executeQuery(strSQL);
//            rs.next();
//            if (!(rs.wasNull()) ) {
//                strEDLText = strEDLText + str4Space + "<VERSION>\n";
//                strEDLText = strEDLText + str8Space + "(VER_ADL_VERSION)  " + (URLDecoder.decode(rs.getString(1), "UTF-8")) + "\n";
//                strEDLText = strEDLText + str8Space + "(VER_CREATOR)      \"" + (URLDecoder.decode(rs.getString(2), "UTF-8")) + "\"\n";
//                strEDLText = strEDLText + str8Space + "(VER_CRTR)         " + (URLDecoder.decode(rs.getString(3), "UTF-8")) + "\n";
//                strEDLText = strEDLText + str4Space + "</VERSION>\n";
//            }
            // Fill in the Title: tag
            strSQL = "SELECT strTitle, strNotes, dtsCreated FROM PUBLIC.PROJECT;";
            st = conn.createStatement();
            rs = st.executeQuery(strSQL);
            rs.next();
            if (!(rs.wasNull()) ) {
                strEDLText = strEDLText +  "Title:" + " \"" + (URLDecoder.decode(rs.getString(1), "UTF-8")) + "\"\n";
//                strEDLText = strEDLText + str8Space + "(PROJ_TITLE)       \"" + (URLDecoder.decode(rs.getString(1), "UTF-8")) + "\"\n";
//                strEDLText = strEDLText + str8Space + "(PROJ_NOTES)       \"" + (URLDecoder.decode(rs.getString(2), "UTF-8")) + "\"\n";
//                DateTime dtCreated = fmtSQL.parseDateTime(rs.getString(3).substring(0, 19)).withZone(DateTimeZone.UTC);
//                strEDLText = strEDLText + str8Space + "(PROJ_CREATE_DATE) " + fmtADLXML.print(dtCreated) + "\n";
//                strEDLText = strEDLText + str4Space + "</PROJECT>\n";

            }
            
            // Write the Sample Rate: tag
//            strEDLText = strEDLText + str4Space + "<SYSTEM>\n";
//            strEDLText = strEDLText + str8Space + "(SYS_XFADE_LEN)    " + String.format("%04d", jProjectTranslator.intPreferredXfadeLength) + "\n";
//            strEDLText = strEDLText + str4Space + "</SYSTEM>\n";
//            // Fill in the SEQUENCE tags
//            strEDLText = strEDLText + str4Space + "<SEQUENCE>\n";
            strEDLText = strEDLText + "Sample Rate: " + jProjectTranslator.intPreferredSampleRate +  "\n";
            // Write the Output Channels:tag
//            String strFrameRate;
//            if (jProjectTranslator.dPreferredFrameRate%5 == 0) {
//                strFrameRate = "" + (java.lang.Math.round(jProjectTranslator.dPreferredFrameRate));
//            } else {
//                strFrameRate = "" + jProjectTranslator.dPreferredFrameRate;
//            }
//            strEDLText = strEDLText + str8Space + "(SEQ_FRAME_RATE)   " + strFrameRate + "\n";
//            strEDLText = strEDLText + str8Space + "(SEQ_DEST_START)   00.00.00.00/0000\n";
//            strEDLText = strEDLText + str4Space + "</SEQUENCE>\n";
            // Fill in the SOURCE_INDEX tags
            strEDLText = strEDLText + "Output Channels: 2\n";
            
            // Write Source Table Entries
            strSQL = "SELECT COUNT(*) FROM PUBLIC.SOURCE_INDEX;";
            st = conn.createStatement();
            rs = st.executeQuery(strSQL);
            rs.next();
            if (!(rs.wasNull()) ) {
                strEDLText = strEDLText +  "Source Table Entries: " + (rs.getString(1)) + "\n";
            }
            strSQL = "SELECT intIndex, strURI FROM PUBLIC.SOURCE_INDEX ORDER BY intIndex;";
            String strIndex;
            String strURI;
            st = conn.createStatement();
            rs = st.executeQuery(strSQL);
            while (rs.next()) {
                // We're going to create new indexes starting at 1 but we need to keep a list of the old indexes to use later.
                strIndex = String.format("%3s", intNewIndex++);
                listOldSourceIndexes.add(rs.getString(1));
                strURI = URLDecoder.decode(rs.getString(2), "UTF-8");
                // It has been URL encoded to trap nasty characters from the database
                strURI = URLDecoder.decode(strURI, "UTF-8");
                strURI = strURI.replaceAll(".mp3", ".wav");
                strURI = strURI.replaceAll(".m4a", ".wav");
                // Strip off the leading URL: if it exists
                if (strURI.startsWith("URL:")) {
                    strURI = strURI.substring(4, strURI.length());
                }
                if (strURI.startsWith("file://localhost")) {
                    strURI = strURI.substring(16, strURI.length());
                }                
                // The URI field in an AES31 adl file is not URL encoded so we might not be able to decode it with URI unless we URI encode it first, we want to use the getPath() method.
//                strURI = URLEncoder.encode(strURI, "UTF-8");
//                // Make it in to a URI
//                URI uriTemp = new URI(strURI);
//                // Use the getPath() method
//                strURI = uriTemp.getPath();
//                // Decode the path and make it in to a file
//                File fTemp = new File(URLDecoder.decode(strURI, "UTF-8"));
//                // The source file name is read from the URI
//                strURI = fTemp.getAbsolutePath();                
               
                
//                strUMID = URLDecoder.decode(rs.getString(3), "UTF-8");
//                strTimeCodeOffset = getADLTimeString(rs.getLong(6), jProjectTranslator.intPreferredSampleRate, jProjectTranslator.dPreferredFrameRate);
//                strFileDuration = "_";
//                if (rs.getLong(4) > 2) {
//                    strFileDuration = getADLTimeString(rs.getLong(4), jProjectTranslator.intPreferredSampleRate, jProjectTranslator.dPreferredFrameRate);
//                }
//                strName = URLDecoder.decode(rs.getString(5), "UTF-8");
                strEDLText = strEDLText + strIndex + " \"" + strURI + "\"\n";
//                strEDLText = strEDLText + str12Space + " (F) \"" + strURI + "\" \"" + strUMID + "\" " +  strTimeCodeOffset + " " + strFileDuration + " \"" + strName + "\" N\n";
            }
//            strEDLText = strEDLText + str4Space + "</SOURCE_INDEX>\n";
            // Fill in the Track n: tags
            // Track 1: "Drums" Solo: 0 Mute: 0
            strSQL = "SELECT intIndex, strName FROM PUBLIC.TRACKS ORDER BY intIndex;";
            st = conn.createStatement();
            rs = st.executeQuery(strSQL);
            intNewIndex = 1;
            String strTrackName = "";
            int intOldTrackIndex;
            ResultSet rs2;
            int intSource;
            long lPlayIn;
            long lPlayOut;
            long lRecordIn;
            long lRecordOut;
            float fVol = 0;
            int intMT = 0;
            int intLK = 0;
            long lFadeIn;
            String strPercentIn = "0";
            String strCurveTypeIn = "\"*default\"";
            long lFadeOut;
            String strPercentOut = "0";
            String strCurveTypeOut = "\"*default\"";
            String strClipName;
                    
            while (rs.next()) {
                intOldTrackIndex = rs.getInt(1);
                listOldTrackIndexes.add(rs.getString(1));
                strTrackName = URLDecoder.decode(rs.getString(2), "UTF-8");
                strEDLText = strEDLText + "Track " + intNewIndex++ + ": \"" + strTrackName + "\" Solo: 0 Mute: 0\n";
                strEDLText = strEDLText + "#Source Track Play-In     Play-Out    Record-In   Record-Out  Vol(dB)  MT LK FadeIn       %     CurveType                          FadeOut      %     CurveType                          Name\n";
                strEDLText = strEDLText + "#------ ----- ----------- ----------- ----------- ----------- -------- -- -- ------------ ----- ---------------------------------- ------------ ----- ---------------------------------- -----\n";
                strSQL = "SELECT EVENT_LIST.intIndex, intSourceIndex, strTrackMap, intSourceIn, intDestIn, intDestOut, "
                    + "strRemark, intTimeCodeOffset, strInFade, intInFade, strOutFade, intOutFade, strGain FROM PUBLIC.EVENT_LIST, PUBLIC.SOURCE_INDEX "
                    + "WHERE EVENT_LIST.intSourceIndex = SOURCE_INDEX.intIndex AND EVENT_LIST.intTrackIndex = " + intOldTrackIndex + " ORDER BY EVENT_LIST.intIndex;";
                rs2 = st.executeQuery(strSQL);
                while (rs2.next()) {
                    intSource = listOldSourceIndexes.indexOf(rs2.getString(2)) + 1;
                    // intSource = rs2.getInt(2);
                    lPlayIn = rs2.getLong(4);
                    lRecordIn = rs2.getLong(5);
                    lRecordOut = rs2.getLong(6);
                    lPlayOut = lPlayIn + lRecordOut - lRecordIn;
                    lFadeIn = rs2.getLong(10);
                    lFadeOut = rs2.getLong(12);
                    strClipName = URLDecoder.decode(rs2.getString(7), "UTF-8");
                    // strIndex = String.format("%3s", intNewIndex++);
                    strEDLText = strEDLText + "#" + String.format("%6s", intSource);
                    strEDLText = strEDLText + " " + String.format("%5s", intOldTrackIndex + 1);
                    strEDLText = strEDLText + " " + String.format("%11s", lPlayIn);
                    strEDLText = strEDLText + " " + String.format("%11s", lPlayOut);
                    strEDLText = strEDLText + " " + String.format("%11s", lRecordIn);
                    strEDLText = strEDLText + " " + String.format("%11s", lRecordOut);
                    strEDLText = strEDLText + " " + String.format("%8s", fVol);
                    strEDLText = strEDLText + " " + String.format("%2s", intMT);
                    strEDLText = strEDLText + " " + String.format("%2s", intLK);
                    strEDLText = strEDLText + " " + String.format("%12s", lFadeIn);
                    strEDLText = strEDLText + " " + String.format("%5s", strPercentIn);
                    strEDLText = strEDLText + " " + String.format("%-34s", strCurveTypeIn);
                    strEDLText = strEDLText + " " + String.format("%12s", lFadeOut);
                    strEDLText = strEDLText + " " + String.format("%5s", strPercentOut);
                    strEDLText = strEDLText + " " + String.format("%-34s", strCurveTypeOut);
                    strEDLText = strEDLText + " " + "\"" + strClipName + "\"\n";
                    
                    
                    
                    
                }
                
                        
                strEDLText = strEDLText + "\n";
                strEDLText = strEDLText + "\n";
            }            
//       4    14           0     1948774      271360     2220134     0.00  0  0          480     0 "*default"                                11756     0 "*default"                         "14-LV 2-260118_1725.wav"            
            //listOldTrackIndexes
            
            // Fill in the EVENT_LIST tags
//            strEDLText = strEDLText + str4Space + "<EVENT_LIST>\n";
//            strSQL = "SELECT EVENT_LIST.intIndex, intSourceIndex, strTrackMap, intSourceIn, intDestIn, intDestOut, "
//                    + "strRemark, intTimeCodeOffset, strInFade, intInFade, strOutFade, intOutFade, strGain FROM PUBLIC.EVENT_LIST, PUBLIC.SOURCE_INDEX "
//                    + "WHERE EVENT_LIST.intSourceIndex = SOURCE_INDEX.intIndex ORDER BY EVENT_LIST.intIndex;";
//            st = conn.createStatement();
//            rs = st.executeQuery(strSQL);
//            while (rs.next()) {
//                strIndex = String.format("%04d", rs.getInt(1));
//                strSourceIndex = String.format("%04d", rs.getInt(2));
//                strSourceIn = getADLTimeString(rs.getLong(4) + rs.getLong(8), jProjectTranslator.intPreferredSampleRate, jProjectTranslator.dPreferredFrameRate);
//                strDestIn = getADLTimeString(rs.getLong(5), jProjectTranslator.intPreferredSampleRate, jProjectTranslator.dPreferredFrameRate);
//                strDestOut = getADLTimeString(rs.getLong(6), jProjectTranslator.intPreferredSampleRate, jProjectTranslator.dPreferredFrameRate);
//                strRemark = URLDecoder.decode(rs.getString(7), "UTF-8");
//                strGain = rs.getString(13);
//                strInFade = rs.getString(9);
//                strOutFade = rs.getString(11);
//                strEDLText = strEDLText + str8Space + "(Entry) " + strIndex + "\n";
//                strEDLText = strEDLText + str12Space + "(Cut) I " + strSourceIndex + "  " + rs.getString(3) + "  " + strSourceIn + "  " + strDestIn + "  " + strDestOut + "  R\n";
//                if (strInFade != null && strInFade.length() > 0) {
//                    strEDLText = strEDLText + str12Space + "(Infade) " + getADLTimeString(rs.getInt(10), jProjectTranslator.intPreferredSampleRate, jProjectTranslator.dPreferredFrameRate) + "  " + strInFade + "\n";
//                }
//                if (strOutFade != null && strOutFade.length() > 0) {
//                    strEDLText = strEDLText + str12Space + "(Outfade) " + getADLTimeString(rs.getInt(12), jProjectTranslator.intPreferredSampleRate, jProjectTranslator.dPreferredFrameRate) + "  " + strOutFade + "\n";
//                }                
//                if (strRemark != null && strRemark.length() > 0) {
//                    strEDLText = strEDLText + str12Space + "(Rem) NAME \"" + strRemark + "\"\n";
//                }
//                if (strGain != null && strGain.length() > 1) {
//                    strEDLText = strEDLText + str12Space + "(Gain) _ " + strGain + "\n";
//                }
//            }
//
//            strEDLText = strEDLText + str4Space + "</EVENT_LIST>\n";
//
//            // Fill in the FADER_LIST tags
//            strEDLText = strEDLText + str4Space + "<FADER_LIST>\n";
//            strSQL = "SELECT intTrack, intTime, strLevel FROM PUBLIC.FADER_LIST ORDER BY intTrack, intTime;";
//            st = conn.createStatement();
//            rs = st.executeQuery(strSQL);
//            while (rs.next()) {
//                strDestIn = getADLTimeString(rs.getInt(2), jProjectTranslator.intPreferredSampleRate, jProjectTranslator.dPreferredFrameRate);
//                strEDLText = strEDLText + str8Space + "(FP)  " + rs.getString(1) + "  " + strDestIn + "  " + rs.getString(3) + "\n";
//            }
//            strEDLText = strEDLText + str4Space + "</FADER_LIST>\n";
//            strEDLText = strEDLText + "\n</ADL>";
            BufferedWriter bOutbuf;
            bOutbuf = new BufferedWriter(new FileWriter(setDestFile, false));
            bOutbuf.write(strEDLText);
            bOutbuf.close();
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
            return false;
        } catch (java.io.UnsupportedEncodingException e) {
            System.out.println("Error on URL decode " + e.toString());
        } catch (java.io.IOException e) {
            System.out.println("File io error " + e.toString());
        } 
        System.out.println("File written " + setDestFile.toString());
        return true;

    } 
    /**
     * This method converts a long in samples in to a formatted string in hour, minutes,
     * seconds, frames and samples in the correct format for an AES31 ADL file.
     * If an invalid sample rate is supplied it defaults to 48000
     * If an invalid frame rate is supplied it defaults to 25
     * These are standard across most of the world.
     * @param lSamples This is a long with the sample count to be converted.
     * @param intSampleRate This is the sample rate, only 44100 and 48000 are valid, 48000 is the default.
     * @param dFrameRate This is a double with the frame rate, only 24, 25, 30 and 29.97 are valid numbers, 25 is the default.
     * @return Returns a formatted string representing the time in ADL format. See AES31 specs for more information.
     */
    public static String getADLTimeString (long lSamples, int intSampleRate, double dFrameRate) {
        String strSep1;
        String strSep2;
        if (intSampleRate == 44100) {
            strSep2 = "|";
        } else {
            strSep2 = "/";
        }
        if (dFrameRate%5 == 0 || dFrameRate == 24) {
            int intFrameRate;
            // Frame rate 24, 25 or 30, simple maths
            strSep1 = ".";
            intFrameRate = 25;
            if (dFrameRate == 30) {
                strSep1 = "|";
                intFrameRate = 30;
            } 
            if (dFrameRate == 24) {
                strSep1 = "=";
                intFrameRate = 24;
            }
            long lHours = lSamples/(60*60*intSampleRate);
            lSamples = lSamples%(60*60*intSampleRate);
            long lMinutes = lSamples/(60*intSampleRate);
            lSamples = lSamples%(60*intSampleRate);
            long lSeconds = lSamples/intSampleRate;
            lSamples = lSamples%intSampleRate;
            long lFrames = lSamples/(intSampleRate/intFrameRate);
            lSamples = lSamples%(intSampleRate/intFrameRate);
            return String.format("%02d", lHours) + strSep1 + String.format("%02d", lMinutes) + strSep1 + String.format("%02d", lSeconds) + strSep1 +
                    String.format("%02d", lFrames) + strSep2 + String.format("%04d", lSamples);
        }
        if (dFrameRate == 29.97) {
            strSep1 = ":";
            String strSep3 = ",";
            // Frame rate 29.97, hard maths, first of all calculate the frame number and the remainder of samples
            long lFrameNumber = (long)java.lang.Math.floor((lSamples/(intSampleRate/dFrameRate)));
            lSamples = (long)java.lang.Math.floor((lSamples%(intSampleRate/dFrameRate)));
            /**CONVERT A FRAME NUMBER TO DROP FRAME TIMECODE
            * Code by David Heidelberger, adapted from Andrew Duncan
            * Given an int called framenumber and a float called framerate
            * Framerate should be 29.97, 59.94, or 23.976, otherwise the calculations will be off.
            */
            int d;
            int m;

            int intDropFrames = (int)java.lang.Math.round(dFrameRate * .066666); //Number of frames to drop on the minute marks is the nearest integer to 6% of the framerate
            int intFramesPerHour = (int)java.lang.Math.round(dFrameRate*60*60); //Number of frames in an hour
            int intFramesPer24Hours = intFramesPerHour*24; //Number of frames in a day - timecode rolls over after 24 hours
            int intFramesPer10Minutes = (int)java.lang.Math.round(dFrameRate * 60 * 10); //Number of frames per ten minutes
            int intFramesPerMinute = (int)java.lang.Math.round((dFrameRate*60) -  intDropFrames); //Number of frames per minute is the round of the framerate * 60 minus the number of dropped frames
            //Negative time. Add 24 hours.
            if (lFrameNumber<0) {
                lFrameNumber=intFramesPer24Hours+lFrameNumber;
            }

            //If framenumber is greater than 24 hrs, next operation will rollover clock
            lFrameNumber = lFrameNumber % intFramesPer24Hours; //% is the modulus operator, which returns a remainder. a % b = the remainder of a/b

            d = (int)java.lang.Math.floor(lFrameNumber/intFramesPer10Minutes); //
            m = (int)(lFrameNumber % intFramesPer10Minutes);

            //In the original post, the next line read m>1, which only worked for 29.97. Jean-Baptiste Mardelle correctly pointed out that m should be compared to dropFrames.
            if (m>intDropFrames) {
                lFrameNumber=(long)(lFrameNumber + (intDropFrames*9*d) + intDropFrames*java.lang.Math.floor(((m-intDropFrames)/intFramesPerMinute)));
            } else {
                lFrameNumber = lFrameNumber + intDropFrames*9*d;
            }

            int frRound = (int)java.lang.Math.round(dFrameRate);
            int intFrames = (int)(lFrameNumber % frRound);
            int intSeconds = (int)(java.lang.Math.floor(lFrameNumber / frRound) % 60);
            int intMinutes = (int)(java.lang.Math.floor(java.lang.Math.floor(lFrameNumber / frRound) / 60) % 60);
            int intHours = (int)(java.lang.Math.floor(java.lang.Math.floor(java.lang.Math.floor(lFrameNumber / frRound) / 60) / 60));
            return String.format("%02d", intHours) + strSep1 + String.format("%02d", intMinutes) + strSep1 + String.format("%02d", intSeconds) + strSep3 +
                    String.format("%02d", intFrames) + strSep2 + String.format("%04d", lSamples);

        }
        return "";
    } 
    /**
     * This method will look for audio clips (regions) which lie completely within another clip.
     * As some audio editors cannot import an ADL file where there is gain automation and clips
     * which lie completely within another we will move the shorter clips to a new track which has no gain 
     * automation. The user will need to tidy these moved clips up after import to their editor.
     * @param st For database access
     * @return Returns the number of clips which have been moved.
     */
    protected int moveSubordinateClips(Statement st) {
        ResultSet rs;
        int intMovedClips, intIndex;
        long lDestIn, lDestOut;
        String strDestChannels, strTrackMap, strSourceChannels;
        intMovedClips = 0;
        Matcher mMatcher;
        Pattern pPatternChannelMap, pPatternChannels;
        pPatternChannelMap = Pattern.compile("(\\d*~\\d*|\\d*)\\s*(\\d*~\\d*|\\d*)"); // This should match the track map string, e.g. 1~2 3~4 etc
        // Look through every entry in the EVENT_LIST table
        strSQL = "SELECT intIndex, intDestIn, intDestOut, strTrackMap "
                + "FROM PUBLIC.EVENT_LIST;";
        try {
            st = conn.createStatement();
            rs = st.executeQuery(strSQL);
            while (rs.next()) {
                intIndex = rs.getInt(1);
                lDestIn = rs.getLong(2);
                lDestOut = rs.getLong(3);
                strTrackMap = rs.getString(4);
                // We need to get the destination channels from the strTrackMap string.
                mMatcher = pPatternChannelMap.matcher(strTrackMap);
                if (mMatcher.find()) {
                    strSourceChannels = mMatcher.group(1);
                    // The matcher should have the destination channels, e.g. 3~4 or just 4
                    strDestChannels = mMatcher.group(2);
                    strSQL = "SELECT COUNT(*) FROM PUBLIC.EVENT_LIST WHERE "
                        + "intDestIn >= " + lDestIn + " AND "
                        + "intDestOut <= " + lDestOut + " AND "
                        + "strTrackMap LIKE \'% " + strDestChannels + "\' AND "
                        + "intIndex != " + intIndex + ";";
                    ResultSet rs2 = st.executeQuery(strSQL);
                    rs2.next();
                    if (rs2.getInt(1) > 0) {
                        System.out.println("Searched for clip with " + strSQL + " found " + rs2.getInt(1));
                        intMovedClips++;
                        strSQL = "SELECT intIndex, intDestIn, intDestOut, strTrackMap FROM PUBLIC.EVENT_LIST WHERE "
                        + "intDestIn >= " + lDestIn + " AND "
                        + "intDestOut <= " + lDestOut + " AND "
                        + "strTrackMap LIKE \'% " + strDestChannels + "\' AND "
                        + "intIndex != " + intIndex + ";";
                        rs2 = st.executeQuery(strSQL);
                        rs2.next();
                        strTrackMap = rs2.getString(4);
                        mMatcher = pPatternChannelMap.matcher(strTrackMap);
                        if (mMatcher.find()) {
                            strSourceChannels = mMatcher.group(1);
                        }
                        // We need to find a substitute destination for each of these clips
                        System.out.println("Clip " + rs2.getInt(1) + " had to be moved from under clip " + intIndex );
                        moveClip(st, rs2.getInt(1), rs2.getLong(2), rs2.getLong(3), strSourceChannels, strDestChannels);
                        return intMovedClips;
                    }
                }
            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        } 
        
    
        
        return intMovedClips;
    }
    /**
     * This method updates the database so the clip is on a different track where there is no overlap.
     * An alternative destination string needs to be created for each channel count.
     * For example if the original string was 6 then this is a mono channel, we need to look through the EVENT_LIST
     * and TRACKS tables and find an unused destination number and use this instead, saving in case of more mono clips
     * which need to be moved later.
     * Same for stereo clips etc. We will store out newly created destinations in the TRACKS table.
     * @param st
     * @param intIndex The index number of the clip to be moved in the EVENT_LIST table
     * @param lDestIn The in point of the clip to be moved
     * @param lDestOut The out point of the clip to be moved
     * @param strSourceChannels The source channels for the clip in ADL format 
     * @param strDestChannels the current destination channels for the clip in ADL format, this is the thing we're going to change
     * @return Returns true if the clip data was changed in the EVENT_LIST table so the clip is now on a different track in the EDL
     */
    protected boolean moveClip (Statement st, int intIndex, long lDestIn, long lDestOut, String strSourceChannels, String strDestChannels) {
        Matcher mMatcher;
        Pattern pPatternChannels;
        int intChannels;
        pPatternChannels = Pattern.compile("(\\d*)~(\\d*)"); // This should match the track map string, e.g. 1~2 etc
        // Start by finding how many channels are required on the track
        mMatcher = pPatternChannels.matcher(strDestChannels);
        if (mMatcher.find()) {
            intChannels = Integer.parseInt(mMatcher.group(2)) -  Integer.parseInt(mMatcher.group(1)) + 1;
        } else {
            intChannels = 1;
        }
        
        try {
            // See if there are any suitable tracks already
            strSQL = "SELECT COUNT(*) FROM PUBLIC.TRACKS WHERE "
                + "intChannels = " + intChannels + ";";
            ResultSet rs = st.executeQuery(strSQL);
            rs.next();
            if (rs.getInt(1) > 0) {
                // One or more potential tracks already exists, need to test each to see if there are already clips which would overlap with ours
                strSQL = "SELECT intIndex, strChannelMap FROM PUBLIC.TRACKS WHERE intChannels = " + intChannels + ";";
                rs = st.executeQuery(strSQL);
                while (rs.next()) {
                    strSQL = "SELECT COUNT(*) FROM PUBLIC.EVENT_LIST WHERE intTrackIndex = " + rs.getInt(1) + " AND "
                            + "(intDestIn < " + lDestOut + " AND intDestOut > " + lDestIn + ")  "
                            + ";";
                    System.out.println("Checking for overlapping tracks with  " + strSQL + "");
                    ResultSet rs2 = st.executeQuery(strSQL);
                    rs2.next();
                    if (rs2.getInt(1) == 0) {
                        // We can reuse this track for our clip
                        strSQL = "UPDATE PUBLIC.EVENT_LIST SET intTrackIndex = " + rs.getInt(1) + ", "
                                + "strTrackMap = \'" + strSourceChannels + " " + rs.getString(2) + "\' "
                                + "WHERE intIndex = " + intIndex + ";";
                        int j = st.executeUpdate(strSQL);
                        if (j == -1) {
                            System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                            return false;
                        }
                        System.out.println("Clip moved with " + strSQL + " to existing track");
                        return true;
                    }
                }
            } 
            // If we got here then there were no suitable tracks
            int intTrackIndex = createTrack(st, intChannels);
            if (intTrackIndex > 0) {
                // Get the dest string
                strSQL = "SELECT strChannelMap FROM PUBLIC.TRACKS WHERE intIndex = " + intTrackIndex + ";";
                rs = st.executeQuery(strSQL);
                rs.next();
                strSQL = "UPDATE PUBLIC.EVENT_LIST SET intTrackIndex = " + intTrackIndex + ", "
                        + "strTrackMap = \'" + strSourceChannels + " " + rs.getString(1) + "\' "
                        + "WHERE intIndex = " + intIndex + ";";
                int j = st.executeUpdate(strSQL);
                if (j == -1) {
                    System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                    return false;
                }
                System.out.println("Clip moved with " + strSQL + " to new track");
                return true;
            }
            
            
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        } 
        
        return true;
    }
    /**
     * This method creates a new entry in the TRACK table.
     * The dest map is calculated by finding the highest dest number which has already been used.
     * @param st
     * @param intChannels Sets the number of audio channels in the track which is created.
     * @return Returns the index number from the TRACKS table where the new track was entered.
     */
    protected int createTrack(Statement st, int intChannels) {
        int intMaxChannels = 0;
        int intCurrentChannel;
        ResultSet rs;
        Matcher mMatcher;
        Pattern pPatternLastNumber;
        pPatternLastNumber = Pattern.compile("(.*?)(\\d+)$"); // This should match the last digit in the string, e.g. 1~2 3~4 etc
        String strNewMap;
        try {
            // Get the maximum count so far
            strSQL = "SELECT DISTINCT strTrackMap FROM PUBLIC.EVENT_LIST;";
            st = conn.createStatement();
            rs = st.executeQuery(strSQL);
            while (rs.next()) {
                mMatcher = pPatternLastNumber.matcher(rs.getString(1));
                if (mMatcher.find()) {
                    intCurrentChannel = Integer.parseInt(mMatcher.group(2));
                    // System.out.println("Searching for max channel no, found " + intCurrentChannel + "\n");
                    if (intCurrentChannel > intMaxChannels) {
                        intMaxChannels = intCurrentChannel;
                    }
                    
                }
                
            }
            // Create a new entry in the TRACKS table
            int intStartChannel = intMaxChannels + 1;
            int intEndChannel = intStartChannel + intChannels - 1; 
            if (intChannels == 1) {
                strNewMap = "" + intStartChannel;
            } else {
                strNewMap = "" + intStartChannel + "~" + intEndChannel;
            }
            intIdCounter++;
            strSQL = "INSERT INTO PUBLIC.TRACKS (intIndex, intChannels, strChannelMap, strName) VALUES "
                    + "(" + intIdCounter + ", " + intChannels + ", \'" + strNewMap + "\', \'Audio " + intIdCounter + "\');";
            int j = st.executeUpdate(strSQL);
            if (j == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                return 0;
            }
            
                    
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
            return 0;
        }
        
        return intIdCounter;
    }
    protected boolean writeAudioFiles () {
        /** Now to write out the sound files
         * 
         */
        File fWrongAudioFolder = new File (fDestFolder,"WRONG_FORMAT");
        String strUMID;
        String strURI;
        File fDestFile;
        int intSourceIndex;
        WAVProcessor tempWAVProcessor;
        ResultSet rs;
         try {
             strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET intCopied = 0;";
            int i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }
            Iterator itr = lWAVProcessors.iterator();
             while(itr.hasNext()) {
                 tempWAVProcessor = (WAVProcessor)itr.next();
                 strUMID = URLEncoder.encode(tempWAVProcessor.getBextOriginatorRef(), "UTF-8");
                 strSQL = "SELECT strURI FROM PUBLIC.SOURCE_INDEX WHERE strUMID = \'" + strUMID + "\';";
                 st = conn.createStatement();
                 rs = st.executeQuery(strSQL);
                 rs.next();
                // Get the raw URI string
                strURI = rs.getString(1);
                // It has been URL encoded to trap nasty characters from the database
                strURI = URLDecoder.decode(strURI, "UTF-8");
                System.out.println("First URI decode " + strURI);
                // Strip off the leading URL: if it exists
                if (strURI.startsWith("URL:")) {
                    strURI = strURI.substring(4, strURI.length());
                }
                // Make it in to a URI
                URI uriTemp = new URI(strURI);
                // Use the getPath() method
                strURI = URLDecoder.decode(uriTemp.getPath(), "UTF-8");
                System.out.println("Second URI decode " + strURI);
                // Decode the path and make it in to a file
                fDestFile = new File(strURI);
                // Check that the sample rate of the file is the same as the current project sample rate, if not write the file to a subfolder.
                if (tempWAVProcessor.getSampleRate() != jProjectTranslator.intProjectSampleRate) {
                    String strFileName = fDestFile.getName();
                    if (fWrongAudioFolder.exists()) {
                        fDestFile = new File(fWrongAudioFolder,strFileName);
                    } else {
                        if (fWrongAudioFolder.mkdir()) {
                            fDestFile = new File(fWrongAudioFolder,strFileName);
                        } else {
                            System.out.println("Failed to create subfolder for file with wrong sample rate " + fWrongAudioFolder);
                        }
                    }
                    
                    
                }
                if (fDestFile.exists()) {
                   strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET intCopied = intIndicatedFileSize WHERE strUMID = \'" + strUMID + "\';";
                   //                    System.out.println("SQL " + strSQL);
                   i = st.executeUpdate(strSQL);
                   if (i == -1) {
                       System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                   }
                   setChanged();
                   notifyObservers();
                   continue;
                }
                System.out.println("Starting audio file write on dest file " + strURI);
                oProjectTranslator.writeStringToPanel(java.util.ResourceBundle.getBundle("jprojecttranslator/Bundle").getString("jProjectWriter.WritingAudioFile") + strURI);
                if (jProjectTranslator.bNoBextChunkInWav && tempWAVProcessor.getFileType().equalsIgnoreCase("WAV")){
                    tempWAVProcessor.setSkipBextChunkOnWrite(true);
                }
                if (jProjectTranslator.bNoBextChunkInW64 && (jProjectTranslator.intSave64BitFilesAs == 2 && tempWAVProcessor.getFileType().equalsIgnoreCase("RF64") || 
                        jProjectTranslator.intSave64BitFilesAs != 1 && tempWAVProcessor.getFileType().equalsIgnoreCase("W64"))){
                    tempWAVProcessor.setSkipBextChunkOnWrite(true);
                }
                if (jProjectTranslator.bNoBextChunkInRF64 && (jProjectTranslator.intSave64BitFilesAs == 1 && tempWAVProcessor.getFileType().equalsIgnoreCase("W64") || 
                        jProjectTranslator.intSave64BitFilesAs != 2 && tempWAVProcessor.getFileType().equalsIgnoreCase("RF64"))){
                    tempWAVProcessor.setSkipBextChunkOnWrite(true);
                }
                String strConversion = "";
                if (jProjectTranslator.intSave64BitFilesAs == 1 && tempWAVProcessor.getFileType().equalsIgnoreCase("W64")) {
                    strConversion = "RF64";
                }
                if (jProjectTranslator.intSave64BitFilesAs == 2 && tempWAVProcessor.getFileType().equalsIgnoreCase("RF64")) {
                    strConversion = "W64";
                }
                tempWAVProcessor.addObserver(this);
                if (strConversion.length() > 0) {
                    tempWAVProcessor.writeFile(fDestFile, strConversion);
                } else {
                    tempWAVProcessor.writeFile(fDestFile);
                }
                tempWAVProcessor.deleteObserver(this);
             }
             // All sound files which were found and could be read have been written , now look for compressed e.g. mp3 files.
             extractMP3Files (fWrongAudioFolder);
             extractM4AFiles (fWrongAudioFolder);
             
             
//             strSQL = "SELECT intIndex, strURI FROM PUBLIC.SOURCE_INDEX ;";
//            st = conn.createStatement();
//            ResultSet rs = st.executeQuery(strSQL);
//            while (rs.next()) {
//                intSourceIndex = rs.getInt(1);
//                strURI = URLDecoder.decode(rs.getString(2), "UTF-8");
//                strURI = strURI.substring(21, strURI.length());
//                fDestFile = new File(strURI);
//                if (fDestFile.exists()) {
//                    continue;
//                }
//                System.out.println("Starting audio file write on  " + intSourceIndex + " dest file " + strURI);
//                ((WAVProcessor)lWAVProcessors.get(intSourceIndex-1)).writeFile(fDestFile);
//            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
            return false;
        } catch (java.io.UnsupportedEncodingException e) {
            System.out.println("Error on decoding at " + strSQL + e.toString());
            return false;
        } catch (java.net.URISyntaxException e) {
            System.out.println("URI encoding exception  " + e.toString());
        }       
        return true;
    }
        /**
     * This is used to get text information which is shown in the Help/About dialogue box.
     * @return The information text.
     */
    public String getInfoText() {
        return "<b>EDL</b><br>"
                + "This exporter will write an .edl (Edit Decision List) file which contains the EDL in Samplitude EDL File Format Version 1.5 which can be used by Reaper.<br>"
                + "If the required audio files have been found these will also be copied to the same folder as the .edl file.<br>"
                + "If the audio files are not BWAVs then a bext chunk will be added though this can be disabled in the preferences.<br>"
                + "<br>";
    }
    /**
     * This fills both the diskstreams and playlists element.
     * It's easier to fill these together as they contain closely related information.
     * @param xmlDiskStreams
     * @param xmlPlaylists 
     */
    private void rebuildTracksTable(){
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
                        }
                    }
                    // intAudioChannelID++;
                }
            }
            // The TRACK table is filled 
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
            
        } 
        
        
    }    
}
