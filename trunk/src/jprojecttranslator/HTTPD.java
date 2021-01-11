package jprojecttranslator;
import java.io.*;
import java.util.*;


/**
 * Subclass of the NanoHTTPD code so I can override the serve method
 * Also  add some private methods to generate customised pages.
 * @author scobeam
 */
public class HTTPD extends NanoHTTPD {

    public HTTPD(int setPort) throws IOException {
        super (setPort,new File(""));
    }
    /**
     * @param uri	Percent-decoded URI without parameters, for example "/index.cgi"
     * @param method	"GET", "POST" etc.
     * @param parms	Parsed, percent decoded parameters from URI and, in case of POST, data.
     * @param header	Header entries, percent decoded
     * @return HTTP response, see class Response for details
     * public Response serve( String uri, String method, Properties header, Properties parms, Properties files )
     */
    @Override
    public Response serve( String uri, String method, Properties header, Properties parms, Properties files) {
	Enumeration e = header.propertyNames();
        while ( e.hasMoreElements()) {
            String value = (String)e.nextElement();
        }
        e = parms.propertyNames();
        while ( e.hasMoreElements()) {
            String value = (String)e.nextElement();
        }
        String msg;
        if (uri.equalsIgnoreCase("/debug.html")) {
            msg = getDebugPage(parms);
        } else {
            msg = "<html>" +
                "<head>" +
                "<title>jProjectTranslator</title>" +
                "</head><body>" +
                "<h1>jProjectTranslator</h1>" +
                "</body></html>";
        }
        return new Response( HTTP_OK, MIME_HTML, msg );
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
        /**
         * This generates the html when the browser requests debug.html
         * @param parms This is a list of paramaters supplied from the browser
         * in the form of ?Table=maps for example.
         * @return Returns the html for the webpage
         */
        private String getDebugPage(Properties parms){

            String strTable = parms.getProperty( "Table" );
            String msg = HTTPHeader;
            String strTableData = "";
            if (strTable != null && strTable.length() > 0 && !(strTable.equalsIgnoreCase("null"))) {
                strTableData = "<p>Table '" + strTable + "' selected.</p><br>";
                strTableData =  strTableData + jProjectTranslator.ourDatabase.getTableData(strTable);
            }
            msg = msg + "<body>\n<div id=\"heading\"><h3>Debug data</h3></div>\n" +
                    "<div id=\"navigation\">" +
                    "<ul>" +
                    "<li><a href=\"debug.html?Table=SOURCE_INDEX\" name=\"SOURCE_INDEX\">SOURCE_INDEX</a></li>\n" +
                    "<li><a href=\"debug.html?Table=EVENT_LIST\" name=\"EVENT_LIST\">EVENT_LIST</a></li>\n" +
                    "<li><a href=\"debug.html?Table=EVENT_LIST2\" name=\"EVENT_LIST2\">EVENT_LIST2</a></li>\n" +
                    "<li><a href=\"debug.html?Table=FADER_LIST\" name=\"FADER_LIST\">FADER_LIST</a></li>\n" +
                    "<li><a href=\"debug.html?Table=FADER_LIST_R\" name=\"FADER_LIST_R\">FADER_LIST_R</a></li>\n" +
                    "<li><a href=\"debug.html?Table=FADER_LIST_T\" name=\"FADER_LIST_T\">FADER_LIST_T</a></li>\n" +
                    "<li><a href=\"debug.html?Table=PROJECT\" name=\"PROJECT\">PROJECT</a></li>\n" +
                    "<li><a href=\"debug.html?Table=VERSION\" name=\"VERSION\">VERSION</a></li>\n" +
                    "<li><a href=\"debug.html?Table=SYSTEM\" name=\"SYSTEM\">SYSTEM</a></li>\n" +
                    "<li><a href=\"debug.html?Table=TRACKS\" name=\"TRACKS\">TRACKS</a></li>\n" +
                    "<li><a href=\"debug.html?Table=ARDOUR_SOURCES\" name=\"ARDOUR_SOURCES\">ARDOUR_SOURCES</a></li>\n" +
                    "<li><a href=\"debug.html?Table=ARDOUR_TEMPO\" name=\"ARDOUR_TEMPO\">ARDOUR_TEMPO</a></li>\n" +
                    "<li><a href=\"debug.html?Table=ARDOUR_TIME_SIGNATURE\" name=\"ARDOUR_TIME_SIGNATURE\">ARDOUR_TIME_SIGNATURE</a></li>\n" +
                    "</ul></div>\n" +
                    "<div id=\"table\">" + strTableData +
                    "</div>\n</body></html>";
            
            return msg;

        }
}
