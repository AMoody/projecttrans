/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jprojecttranslator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * Project writer for AES31 projects.
 * This will write an ADL file and a set of BWAV files.
 * If the source files did not have a bext chunk then this will be added as this is mandatory for AES31.
 * The audio file format will not be changed.
 * @author arth
 */
public class jProjectWriter_AES31 extends jProjectWriter {
    public static DateTimeFormatter fmtADLXML = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssZZ");
    private int intIdCounter = 0;
    /*
     * This returns a FileFilter which this class can read
     */
    public javax.swing.filechooser.FileFilter getFileFilter() {
        javax.swing.filechooser.FileFilter filter = new FileNameExtensionFilter("AES31 (.adl)", "adl");
        return filter;
    } 
    protected boolean processProject() {
        System.out.println("AES31 writer thread running");
        // Clear the TRACKS table
        try {
            strSQL = "DELETE FROM PUBLIC.TRACKS;";
            int j = st.executeUpdate(strSQL);
            if (j == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        }
        // Fill in the URI information
        writeURIs ();
        // Move overlapping tracks
        int intCounter = 0;
        int intMovedTracks = 0;
        do {
            intMovedTracks = moveSubordinateClips(st);
            intCounter++;
            
        } while (intMovedTracks > 0 && intCounter < 5);
        /**
        * Next step is to create an ADL file and write the output.
        */
        writeADLFile(fDestFile, st);
        oProjectTranslator.writeStringToPanel("ADL file written");
        writeAudioFiles ();
        oProjectTranslator.writeStringToPanel("Finished");
        System.out.println("AES31 writer thread finished");
        return true;
    }
    /* This method fills in the URI column in the SOURCE_INDEX table
     * 
     */
    protected boolean writeURIs () {

        int intSourceIndex;
        String strName;
        int i;
        try {
            strSQL = "SELECT intIndex, strDestFileName FROM PUBLIC.SOURCE_INDEX ORDER BY intIndex;";
            st = conn.createStatement();
            ResultSet rs = st.executeQuery(strSQL);
            while (rs.next()) {
                intSourceIndex = rs.getInt(1);
                strName = URLDecoder.decode(rs.getString(2), "UTF-8");
                File fTemp = new File(fDestFolder.toString(),strName);
                String strTemp = fTemp.toString();
                strTemp = strTemp.replaceAll("\\\\", "/");
                if (!strTemp.startsWith("/")) {
                    strTemp = "/" + strTemp;
                }
                URI uriTemp = new URI("file","localhost",strTemp,null);
                // The new URI function will URL encode the string.
                String strURI = "URL:" + uriTemp.toString();
                // URL encode this string to keep bad characters from the database
                strURI = URLEncoder.encode(strURI, "UTF-8");
                strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET strURI = \'" + strURI + "\' WHERE intIndex = " + intSourceIndex + ";";
                i = st.executeUpdate(strSQL);
                if (i == -1) {
                    System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                }
            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        } catch (java.io.UnsupportedEncodingException e) {
            System.out.println("Error on decoding at " + strSQL + e.toString());
        } catch (java.net.URISyntaxException e) {
                    System.out.println("URI encoding exception  " + e.toString());
                }
        
        return true;
    }
    private boolean writeADLFile(File setDestFile, Statement st) {
        String strADLText = "<ADL>\n";
        String str12Space = "            ";
        String str8Space = "        ";
        String str4Space = "    ";
        try {
            // Fill in the VERSION tags
            strSQL = "SELECT strADLVersion, strCreator, strCreatorVersion FROM PUBLIC.VERSION;";
            st = conn.createStatement();
            ResultSet rs = st.executeQuery(strSQL);
            rs.next();
            if (!(rs.wasNull()) ) {
                strADLText = strADLText + str4Space + "<VERSION>\n";
                strADLText = strADLText + str8Space + "(VER_ADL_VERSION)  " + (URLDecoder.decode(rs.getString(1), "UTF-8")) + "\n";
                strADLText = strADLText + str8Space + "(VER_CREATOR)      \"" + (URLDecoder.decode(rs.getString(2), "UTF-8")) + "\"\n";
                strADLText = strADLText + str8Space + "(VER_CRTR)         " + (URLDecoder.decode(rs.getString(3), "UTF-8")) + "\n";
                strADLText = strADLText + str4Space + "</VERSION>\n";
            }
            // Fill in the PROJECT tags
            strSQL = "SELECT strTitle, strNotes, dtsCreated FROM PUBLIC.PROJECT;";
            st = conn.createStatement();
            rs = st.executeQuery(strSQL);
            rs.next();
            if (!(rs.wasNull()) ) {
                strADLText = strADLText + str4Space + "<PROJECT>\n";
                strADLText = strADLText + str8Space + "(PROJ_TITLE)       \"" + (URLDecoder.decode(rs.getString(1), "UTF-8")) + "\"\n";
                strADLText = strADLText + str8Space + "(PROJ_NOTES)       \"" + (URLDecoder.decode(rs.getString(2), "UTF-8")) + "\"\n";
                DateTime dtCreated = fmtSQL.parseDateTime(rs.getString(3).substring(0, 19)).withZone(DateTimeZone.UTC);
                strADLText = strADLText + str8Space + "(PROJ_CREATE_DATE) " + fmtADLXML.print(dtCreated) + "\n";
                strADLText = strADLText + str4Space + "</PROJECT>\n";

            }
            // Write the SYSTEM section
            strADLText = strADLText + str4Space + "<SYSTEM>\n";
            strADLText = strADLText + str8Space + "(SYS_XFADE_LEN)    " + String.format("%04d", jProjectTranslator.intPreferredXfadeLength) + "\n";
            strADLText = strADLText + str4Space + "</SYSTEM>\n";
            // Fill in the SEQUENCE tags
            strADLText = strADLText + str4Space + "<SEQUENCE>\n";
            strADLText = strADLText + str8Space + "(SEQ_SAMPLE_RATE)  S" + jProjectTranslator.intPreferredSampleRate +  "\n";
            String strFrameRate;
            if (jProjectTranslator.dPreferredFrameRate%5 == 0) {
                strFrameRate = "" + (java.lang.Math.round(jProjectTranslator.dPreferredFrameRate));
            } else {
                strFrameRate = "" + jProjectTranslator.dPreferredFrameRate;
            }
            strADLText = strADLText + str8Space + "(SEQ_FRAME_RATE)   " + strFrameRate + "\n";
            strADLText = strADLText + str8Space + "(SEQ_DEST_START)   00.00.00.00/0000\n";
            strADLText = strADLText + str4Space + "</SEQUENCE>\n";
            // Fill in the SOURCE_INDEX tags
            strADLText = strADLText + str4Space + "<SOURCE_INDEX>\n";
            strSQL = "SELECT intIndex, strURI, strUMID, intLength, strName, intTimeCodeOffset FROM PUBLIC.SOURCE_INDEX ORDER BY intIndex;";
            String strIndex;
            String strSourceIndex;
            String strUMID;
            String strURI;
            String strName;
            String strSourceIn;
            String strDestIn;
            String strDestOut;
            String strRemark;
            String strTimeCodeOffset;
            String strInFade = "";
            String strOutFade = "";
            st = conn.createStatement();
            rs = st.executeQuery(strSQL);
            // The URI field should not be URL encoded in an AES31 adl file.
            while (rs.next()) {
                strIndex = String.format("%04d", rs.getInt(1));
                strURI = URLDecoder.decode(rs.getString(2), "UTF-8");
                // It has been URL encoded to trap nasty characters from the database
                strURI = URLDecoder.decode(strURI, "UTF-8");
                strUMID = URLDecoder.decode(rs.getString(3), "UTF-8");
                strTimeCodeOffset = getADLTimeString(rs.getLong(6), jProjectTranslator.intPreferredSampleRate, jProjectTranslator.dPreferredFrameRate);
                strName = URLDecoder.decode(rs.getString(5), "UTF-8");
                strADLText = strADLText + str8Space + "(Index) " + strIndex + "\n";
                strADLText = strADLText + str12Space + " (F) \"" + strURI + "\" \"" + strUMID + "\" " +  strTimeCodeOffset + " _ \"" + strName + "\" N\n";
            }
            strADLText = strADLText + str4Space + "</SOURCE_INDEX>\n";
            // Fill in the EVENT_LIST tags
            strADLText = strADLText + str4Space + "<EVENT_LIST>\n";
            strSQL = "SELECT EVENT_LIST.intIndex, intSourceIndex, strTrackMap, intSourceIn, intDestIn, intDestOut, "
                    + "strRemark, intTimeCodeOffset, strInFade, intInFade, strOutFade, intOutFade FROM PUBLIC.EVENT_LIST, PUBLIC.SOURCE_INDEX "
                    + "WHERE EVENT_LIST.intSourceIndex = SOURCE_INDEX.intIndex ORDER BY EVENT_LIST.intIndex;";
            st = conn.createStatement();
            rs = st.executeQuery(strSQL);
            while (rs.next()) {
                strIndex = String.format("%04d", rs.getInt(1));
                strSourceIndex = String.format("%04d", rs.getInt(2));
                strSourceIn = getADLTimeString(rs.getInt(4) + rs.getInt(8), jProjectTranslator.intPreferredSampleRate, jProjectTranslator.dPreferredFrameRate);
                strDestIn = getADLTimeString(rs.getInt(5), jProjectTranslator.intPreferredSampleRate, jProjectTranslator.dPreferredFrameRate);
                strDestOut = getADLTimeString(rs.getInt(6), jProjectTranslator.intPreferredSampleRate, jProjectTranslator.dPreferredFrameRate);
                strRemark = URLDecoder.decode(rs.getString(7), "UTF-8");
                strInFade = rs.getString(9);
                strOutFade = rs.getString(11);
                strADLText = strADLText + str8Space + "(Entry) " + strIndex + "\n";
                strADLText = strADLText + str12Space + "(Cut) I " + strSourceIndex + "  " + rs.getString(3) + "  " + strSourceIn + "  " + strDestIn + "  " + strDestOut + "  R\n";
                if (strInFade != null && strInFade.length() > 0) {
                    strADLText = strADLText + str12Space + "(Infade) " + getADLTimeString(rs.getInt(10), jProjectTranslator.intPreferredSampleRate, jProjectTranslator.dPreferredFrameRate) + "  " + strInFade + "\n";
                }
                if (strOutFade != null && strOutFade.length() > 0) {
                    strADLText = strADLText + str12Space + "(Outfade) " + getADLTimeString(rs.getInt(12), jProjectTranslator.intPreferredSampleRate, jProjectTranslator.dPreferredFrameRate) + "  " + strOutFade + "\n";
                }                
                if (strRemark != null && strRemark.length() > 0) {
                    strADLText = strADLText + str12Space + "(Rem) NAME \"" + strRemark + "\"\n";
                }
            }

            strADLText = strADLText + str4Space + "</EVENT_LIST>\n";

            // Fill in the FADER_LIST tags
            strADLText = strADLText + str4Space + "<FADER_LIST>\n";
            strSQL = "SELECT intTrack, intTime, strLevel FROM PUBLIC.FADER_LIST ORDER BY intTrack, intTime;";
            st = conn.createStatement();
            rs = st.executeQuery(strSQL);
            while (rs.next()) {
                strDestIn = getADLTimeString(rs.getInt(2), jProjectTranslator.intPreferredSampleRate, jProjectTranslator.dPreferredFrameRate);
                strADLText = strADLText + str8Space + "(FP)  " + rs.getString(1) + "  " + strDestIn + "  " + rs.getString(3) + "\n";
            }
            strADLText = strADLText + str4Space + "</FADER_LIST>\n";
            strADLText = strADLText + "\n</ADL>";
            BufferedWriter bOutbuf = new BufferedWriter(new FileWriter(setDestFile, false));
            bOutbuf.write(strADLText);
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
                        System.out.println("Clip " + rs2.getInt(1) + " had to be moved from under clip " + intIndex );
                        moveClip(st, rs2.getInt(1), rs2.getLong(2), rs2.getLong(3), strSourceChannels, strDestChannels);
                        return intMovedClips;
                        // We need to find a substitute destination for each of these clips
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
     * and TRACKS tables and find an unused destination number and return this instead, saving in case of more mono clips
     * which need to be moved later.
     * Same for stereo clips etc. We will store out newly created destinations in the TRACKS table.
     * @param st
     * @param intIndex
     * @param lDestIn
     * @param lDestOut
     * @param strTrackMap
     * @param strDestChannels
     * @return
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
        
        String strUMID;
        String strURI;
        File fDestFile;
        int intSourceIndex;
        BWFProcessor tempBWFProcessor;
        ResultSet rs;
         try {
             Iterator itr = lBWFProcessors.iterator();
             while(itr.hasNext()) {
                 tempBWFProcessor = (BWFProcessor)itr.next();
                 strUMID = URLEncoder.encode(tempBWFProcessor.getBextOriginatorRef(), "UTF-8");
                 strSQL = "SELECT strURI FROM PUBLIC.SOURCE_INDEX WHERE strUMID = \'" + strUMID + "\';";
                 st = conn.createStatement();
                 rs = st.executeQuery(strSQL);
                 rs.next();
                // Get the raw URI string
                strURI = rs.getString(1);
                // It has been URL encoded to trap nasty characters from the database
                strURI = URLDecoder.decode(strURI, "UTF-8");
                // Strip off the leading URL: if it exists
                if (strURI.startsWith("URL:")) {
                    strURI = strURI.substring(4, strURI.length());
                }
                // Make it in to a URI
                URI uriTemp = new URI(strURI);
                // Use the getPath() method
                strURI = URLDecoder.decode(uriTemp.getPath(), "UTF-8");
                // Decode the path and make it in to a file
                fDestFile = new File(strURI);
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
                 System.out.println("Starting audio file write on dest file " + strURI);
                 oProjectTranslator.writeStringToPanel("Writing audio file " + strURI);
        
                 tempBWFProcessor.addObserver(this);
                 tempBWFProcessor.writeFile(fDestFile);
                 tempBWFProcessor.deleteObserver(this);
             }
             
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
//                ((BWFProcessor)lBWFProcessors.get(intSourceIndex-1)).writeFile(fDestFile);
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
        return "<b>AES31</b><br>"
                + "This exporter will write an .adl (Audio Decision List) file which contains the EDL.<br>"
                + "If the required audio files have been found these will also be copied to the same folder as the .adl file.<br>"
                + "If the audio files are not BWAVs then a bext chunk will be added, this is mandatory for an AES31 project.<br>"
                + "<br>";
    }
}
