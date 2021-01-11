/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package jprojecttranslator;
import java.sql.Connection;
import java.sql.Statement;
import java.io.*;
import java.net.URLDecoder;
import java.sql.ResultSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import static jprojecttranslator.jProjectTranslator.fmtDisplay;
import static jprojecttranslator.jProjectWriter.fmtSQL;
import static jprojecttranslator.jProjectWriter_AES31.getADLTimeString;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 *
 * @author moodya71
 */
public class HTMLFileWriter {
    protected database ourDatabase;
    protected String strSQL, strFileName;
    protected Statement st;
    protected Connection conn;
    protected boolean bIsValid = false;
    File fFileName;
    String strOutput;
    
    
    public HTMLFileWriter(String strFilename, database setDatabase) {
        strFileName = strFilename;
        ourDatabase = setDatabase;
        conn = ourDatabase.getConnection();
        fFileName = new File(strFilename);
        if (fFileName.getParentFile().exists() && fFileName.getParentFile().canWrite()) {
            bIsValid = true;
        }
        
        
        
    }
    public boolean getIsValid() {
        return bIsValid;
    }
    public boolean writeFile() {
        strOutput = HTTPHeader;
        strOutput = strOutput + "<body>\n<div id=\"heading\"><h3>jProjectTranslator Project report</h3></div>\n";
        strOutput = strOutput + "<h4>Summary</h4>\n";
        strOutput = strOutput + getSummaryTable();
        strOutput = strOutput + "<h4>Audio file information</h4>\n";
        strOutput = strOutput + getFilesTable();
        strOutput = strOutput + "</body></html>";
        BufferedWriter bOutbuf;
        try {
            bOutbuf = new BufferedWriter(new FileWriter(fFileName, false));
            bOutbuf.write(strOutput);
            bOutbuf.close();
        } catch (IOException ex) {
            System.out.println("HTMLFileWriter writeFile " + ex.toString());
        }
        
        
        
        
        return true;
    }
    private String getSummaryTable() {
        String strReturn = "<div id=\"table\">\n<table><tr>"
                + "<th>Project name</th>\n" +
                "<th>Created date</th>\n" +
                "<th>ADL Version</th>\n" +
                "<th>Created by software</th>\n" +
                "<th>Software version</th>\n" +
                "<th>Notes</th>\n" +
                "</tr>";
        try {
            st = conn.createStatement();  
            strSQL = "SELECT strTitle, dtsCreated, strADLVersion, strCreator, strCreatorVersion, strNotes FROM PUBLIC.VERSION, PUBLIC.PROJECT;";
            ResultSet rs = st.executeQuery(strSQL);
            if (rs.next()) {
                String strTitle = URLDecoder.decode(rs.getString(1), "UTF-8");
                DateTime dtCreated = fmtSQL.parseDateTime(rs.getString(2).substring(0, 19)).withZone(DateTimeZone.UTC);
                String strCreated  = fmtDisplay.withZone(DateTimeZone.getDefault()).print(dtCreated);
                String strADLVersion = URLDecoder.decode(rs.getString(3), "UTF-8");
                String strCreator = URLDecoder.decode(rs.getString(4), "UTF-8");
                String strCreatorVersion = URLDecoder.decode(rs.getString(5), "UTF-8");
                String strNotes = URLDecoder.decode(rs.getString(6), "UTF-8");
                strReturn = strReturn + "<tr><td>" + strTitle + "</td>" + "<td>" + strCreated + "</td>" + "<td>" + strADLVersion + "</td>" + "<td>" + 
                        strCreator + "</td>" + "<td>" + strCreatorVersion + "</td>" + "<td>" + strNotes + "</td></tr></table></div>";
            }
            
            
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + e.toString());
         
        } catch (UnsupportedEncodingException ex) {
            System.out.println("Error in getSummaryTable " + ex.toString());
            
        }        
        return strReturn;
    }
    private String getFilesTable() {
        String strReturn = "<div id=\"table\">\n<table><tr>"
                + "<th>File name</th>\n" +
                "<th>Duration (hours:minutes:seconds)</th>\n" +
                "<th>UMID</th>\n" +
                "<th>Status</th>\n" +
                "</tr>";
        try {
            st = conn.createStatement();  
            strSQL = "SELECT strDestFileName, intLength, strUMID, intSampleRate, strType FROM PUBLIC.SOURCE_INDEX;";
            ResultSet rs = st.executeQuery(strSQL);
            while (rs.next()) {
                String strDestFileName = URLDecoder.decode(rs.getString(1), "UTF-8");
                String strDuration = "?";
                if (rs.getLong(2) > 2) {
                    // Guess that the sample rate of the file is the project default, might update this later if the actuial sample rate for the file is known
                    strDuration = jProjectTranslator.getTimeString(rs.getLong(2)/jProjectTranslator.intPreferredSampleRate);
                }
                String strUMID = URLDecoder.decode(rs.getString(3), "UTF-8");
                int intSampleRate = rs.getInt(4);
                String strStatus;
                if (intSampleRate > 0 || rs.getString(5).equalsIgnoreCase("mp3")) {
                    strStatus = "Found " + rs.getString(5);
                    if (rs.getLong(2) > 2 && intSampleRate > 0) {
                        // Update the duration using the actual sample rate for the file
                        strDuration = jProjectTranslator.getTimeString(rs.getLong(2)/intSampleRate);
                        // strDuration = getADLTimeString(rs.getLong(2), intSampleRate, jProjectTranslator.dPreferredFrameRate);
                    }
                    
                } else {
                    strStatus = "<font color=\"FF4500\">NOT Found</font>";
                }
                strReturn = strReturn + "<tr><td>" + strDestFileName + "</td>" + "<td>" + strDuration + "</td>" + "<td>" + strUMID + "</td>" + "<td>" + 
                        strStatus + "</td></tr>";
            }
            
            
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + e.toString());
         
        } catch (UnsupportedEncodingException ex) {
            System.out.println("Error in getSummaryTable " + ex.toString());
            
        }        
        return strReturn + "</table></div>";
    }    
    /**
     * This is a string which appears at the top of all the webpages served with all the
     * css stuff in it.
     */
    private static final String HTTPHeader =
		"<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Strict//EN\">\n" +
                "<html><head>" +
                "<style type=\"text/css\">\n" +
                "body {background-color: rgb(230,230,255);}\n" +
		"div {font-family: Arial,Helvetica,sans-serif;" +
                "text-align: center;}\n" +
                "table {\n" +
                "    width: 100%;" +
                "    border: 1px solid rgb(0, 0, 0);" +
                "    margin: 2px;" +
                "}" +
                "td {" +
                "    padding: 2px;" +
                "    font-family: Arial,Helvetica,sans-serif;" +
                "    background-color: rgb(200,200,255);" +
                "    width: 50%;" +
                "    font-size: 9px;" +
                "}\n" +
                "th {" +
                "    padding: 2px;" +
                "    background-color: rgb(131,131,167);" +
                "    white-space:nowrap;" +
                "    font-size: 9px;" +
                "}\n" +
                "#navigation li {" +
                "    display: inline;" +
                "    list-style-type: none;" +
                "    padding-right: 20px;" +
                "}\n" +
                "</style>\n" +
                "<title>jProjectTranslator</title></head>\n";
    
}
