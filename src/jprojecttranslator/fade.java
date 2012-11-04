package jprojecttranslator;
import org.dom4j.Element;
import java.util.*;
import net.sourceforge.openforecast.DataSet;
import net.sourceforge.openforecast.DataPoint;
import net.sourceforge.openforecast.Observation;
import net.sourceforge.openforecast.Forecaster;
import net.sourceforge.openforecast.ForecastingModel;
/**
 * This stores information about an audio fade.
 * A fade consists of a duration in samples and some curve data.
 * A fade can be an in fade or an out fade
 *
 * @author scobeam
 */
public class fade {
    // The fade length
    private long lLength; 
    private String strFade;
    /**
     * Load a FadeIn or FadeOut from an Ardour project file.
     * Crossfades in Ardour consist of a FadeIn and FadeOut.
     * Ardour fades consist of a number of fade levels at certain time offsets in samples.
     * In the AES31 adl file we need three fade levels at 25% 50% and 75% across the fade.
     * We will use some numerical analysis from the openforecast package to find suitable values.
     * @param xmlFade   This is an xml Element containing fade values. The FadeIn or FadeOut element should be part of a crossfade.
     * @return          Returns true if the fade was parsed successfully.
     */
    public boolean loadArdourFade(Element xmlFade) {
        lLength = 0;
        long lTempLength = 0;
        strFade = "";
        String strKey, strValue;
        float fValue;
        HashMap map = new HashMap();
        Element xmlPoint;
        for (Iterator i = xmlFade.elementIterator("point");i.hasNext();) {
            xmlPoint = (Element)i.next();
            strKey = xmlPoint.attributeValue("x");
            lTempLength = java.lang.Math.round(Float.parseFloat(strKey));
            if (lTempLength > lLength) {
                lLength = lTempLength;
            }
            strValue = xmlPoint.attributeValue("y");
            fValue = Float.parseFloat(strValue);
            // The map consists of keys which are the time in samples across the fade, and values which are a float between 1 and 0.
            map.put(lTempLength, fValue);
        }
        // Trap a special case of a linear fade
        if (map.size() == 2) {
            strFade = "LIN  _  _  _";
            System.out.println("Parsed fade is " + lLength + " " + strFade);
            return true;
        }
        if (map.size() < 2) {
            // Something has gone wrong
            return false;
        }
        // Use openforecast to predict the wanted fade levels for the ADL file from the given values in the ardour file
        DataSet observedData = new DataSet();
        DataPoint dp;
        Set s = map.entrySet();
        Iterator i = s.iterator();
        Map.Entry me;
        while (i.hasNext()) {
            me = (Map.Entry)i.next();
            lTempLength = Long.parseLong("" + me.getKey());
            fValue = Float.parseFloat("" + me.getValue());
            dp = new Observation( fValue );
            dp.setIndependentValue( "t", lTempLength );
            observedData.add( dp );
            
        }
//        System.out.println("Input data, observed values");
//        System.out.println( observedData );
        // Obtain a good forecasting model given this data set
        ForecastingModel forecaster = Forecaster.getBestForecast( observedData );
//        System.out.println("Forecast model type selected: " + forecaster.getForecastType());
//        System.out.println( forecaster.toString() );
        // Create additional data points for which forecast values are required
        DataSet requiredDataPoints = new DataSet();
        dp = new Observation( 0.0 );
        dp.setIndependentValue( "t", lLength/4 );
        requiredDataPoints.add( dp );
        dp = new Observation( 0.0 );
        dp.setIndependentValue( "t", lLength/2 );
        requiredDataPoints.add( dp );
        dp = new Observation( 0.0 );
        dp.setIndependentValue( "t", 3*lLength/4 );
        requiredDataPoints.add( dp );
        forecaster.forecast( requiredDataPoints );
//        System.out.println("Output data, forecast values");
//        System.out.println( requiredDataPoints );
        i = requiredDataPoints.iterator();
        double dLevel;
        String strLevel;
        strFade = "CURVE  "; 
        while (i.hasNext()) {
            dp = (DataPoint)i.next();
            dLevel = dp.getDependentValue();
            if (dLevel < 0) {
                return false;
            }
            dLevel = 20*Math.log10(dLevel);
            strLevel = String.format("%.2f", dLevel);
            strFade = strFade + strLevel + "  ";
            
        }
        System.out.println("Parsed fade is " + lLength + " " + strFade);
        return true;
        
    }
    /**
     * Load a fade from an Ardour project file.
     * Ardour fades consist of a number of fade levels at certain time offsets in samples.
     * In the AES31 adl file we need three fade levels at 25% 50% and 75% across the fade.
     * We will use some numerical analysis from the openforecast package to find suitable values.
     * @param xmlSource This is an xml Element containing fade values. The FadeIn or FadeOut element should be part of a region.
     * @return          Returns true if the fade was parsed successfully.
     */
    public boolean loadArdourElement(Element xmlSource) {
        lLength = 0;
        long lTempLength = 0;
        strFade = "";
        String strKey, strValue;
        float fValue;
        HashMap map = new HashMap();
        if ((xmlSource.attributeValue("active")!= null && xmlSource.attributeValue("active").equalsIgnoreCase("no") )
                || (xmlSource.attributeValue("default")!= null &&xmlSource.attributeValue("default").equalsIgnoreCase("yes"))) {
            return false;
        }
        Element xmlAutomationList = xmlSource.element("AutomationList");
        String strEvents = xmlAutomationList.elementText("events");
        StringTokenizer st = new StringTokenizer(strEvents); 
        while(st.hasMoreTokens()) {
            strKey = st.nextToken();
            lTempLength = java.lang.Math.round(Float.parseFloat(strKey));
            if (lTempLength > lLength) {
                lLength = lTempLength;
            }
            strValue = st.nextToken();
            fValue = Float.parseFloat(strValue);
            // The map consists of keys which are the time in samples across the fade, and values which are a float between 1 and 0.
            map.put(lTempLength, fValue);
        }
        // Trap a special case of a linear fade
        if (map.size() == 2) {
            strFade = "LIN  _  _  _";
            System.out.println("Parsed fade is " + lLength + " " + strFade);
            return true;
        }
        if (map.size() < 2) {
            // Something has gone wrong
            return false;
        }
        // Use openforecast to predict the wanted fade levels for the ADL file from the given values in the ardour file
        DataSet observedData = new DataSet();
        DataPoint dp;
        Set s = map.entrySet();
        Iterator i = s.iterator();
        Map.Entry me;
        while (i.hasNext()) {
            me = (Map.Entry)i.next();
            lTempLength = Long.parseLong("" + me.getKey());
            fValue = Float.parseFloat("" + me.getValue());
            dp = new Observation( fValue );
            dp.setIndependentValue( "t", lTempLength );
            observedData.add( dp );
            
        }
//        System.out.println("Input data, observed values");
//        System.out.println( observedData );
        // Obtain a good forecasting model given this data set
        ForecastingModel forecaster = Forecaster.getBestForecast( observedData );
//        System.out.println("Forecast model type selected: " + forecaster.getForecastType());
//        System.out.println( forecaster.toString() );
        // Create additional data points for which forecast values are required
        DataSet requiredDataPoints = new DataSet();
        dp = new Observation( 0.0 );
        dp.setIndependentValue( "t", lLength/4 );
        requiredDataPoints.add( dp );
        dp = new Observation( 0.0 );
        dp.setIndependentValue( "t", lLength/2 );
        requiredDataPoints.add( dp );
        dp = new Observation( 0.0 );
        dp.setIndependentValue( "t", 3*lLength/4 );
        requiredDataPoints.add( dp );
        forecaster.forecast( requiredDataPoints );
//        System.out.println("Output data, forecast values");
//        System.out.println( requiredDataPoints );
        i = requiredDataPoints.iterator();
        double dLevel;
        String strLevel;
        strFade = "CURVE  "; 
        while (i.hasNext()) {
            dp = (DataPoint)i.next();
            dLevel = dp.getDependentValue();
            if (dLevel < 0) {
                return false;
            }
            dLevel = 20*Math.log10(dLevel);
            strLevel = String.format("%.2f", dLevel);
            strFade = strFade + strLevel + "  ";
            
        }
        System.out.println("Parsed fade is " + lLength + " " + strFade);
        return true;
        
    }
    /**
     * @return Returns the length of the fade in samples.
     */
    public long getLength() {
        return lLength;
    }
    /**
     * @return Returns a string in ADL format describing the fade shape.
     */
    public String getFade() {
        return strFade;
    }
}
