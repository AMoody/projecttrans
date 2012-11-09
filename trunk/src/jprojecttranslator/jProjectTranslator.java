package jprojecttranslator;
import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.io.*;
import java.util.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.dom4j.io.SAXReader;
import org.dom4j.DocumentException;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.nio.charset.CodingErrorAction;
import org.joda.time.*;
import org.joda.time.format.*;
import java.lang.Math.*;
import javax.swing.JOptionPane;
import javax.swing.table.AbstractTableModel;
/**
 * Project Translator is used to convert audio projects from various formats to AES31 (adl) format.
 * The source file is read in to a database which closely matches the structure of an AES31 adl file.
 * The projectreader and projectwriter classes can be extended to support additional formats.
 * This was written initially to convert Ardour projects to AES31 but an importer for VCS Startrack was added for the BBC.
 * @author arth
 */
public class jProjectTranslator extends javax.swing.JFrame implements Observer {
    /** We need to retain a reference to the window when we create it so we can
     *access the objects it contains. This is it.*/
    static jProjectTranslator ourWindow;
    /** This is a static random number generator for this class, all objects share the generator.
     * As this is public, other objects can also use it.
     */
    public static Random randomNumber = new Random();
    /** This is a source file to be loaded. */
    private static String strSourceProjectFile;
    /** This is the ini file which stores the users own settings in their home folder.*/
    static JIniFile usersIniFile;
    static database ourDatabase;
    protected static List listReaders = new ArrayList();
    protected static List listWriters = new ArrayList();
    private static List listBWFProcessors = new ArrayList();
    private static javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
    // These preferred values are set by the user
    public static int intPreferredSampleRate = 48000;
    public static double dPreferredFrameRate = 25;
    public static int intPreferredXfadeLength = 200;
    // These project values are those of the currently loaded project
    public static int intProjectSampleRate = intPreferredSampleRate;
    public static double dProjectFrameRate = dPreferredFrameRate;
    public static int intProjectXfadeLength = intPreferredXfadeLength;
//    public static double dPreferredFrameRate = 29.97;
    /* This stores the last used folder. */
    private static File fPath;
    public static DateTimeFormatter fmtSQL = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
    public static DateTimeFormatter fmtDisplay = DateTimeFormat.forPattern("dd/MM/yyyy HH:mm:ss");
    public static DateTimeFormatter fmtHHMMSS = DateTimeFormat.forPattern("HHmmss");
    // This sets the number of messages displayed
    private int intMessageQueueLength = 3;
    // This stores the last intMessageQueueLength messages
    private List listRecentActivityStrings = new ArrayList(intMessageQueueLength);
    private soundFilesTableModel ourTableModel = new soundFilesTableModel();
    private static String strLastFileOpenFilter = "";
    private static String strLastFileSaveAsFilter = "";
    final static ResourceBundle rbProject = ResourceBundle.getBundle("jprojecttranslator.version"); 
    protected static String strBuild;
    protected static final String strVersion = "0.1";
    /**
     * Creates new form jProjectTranslator
     */
    public jProjectTranslator() {
        initComponents();
        jTable1.setModel(ourTableModel);
    }
    public void writeStringToPanel(String setString) {
        listRecentActivityStrings.add(setString);
        if (listRecentActivityStrings.size() > intMessageQueueLength) {
            listRecentActivityStrings.remove(0);
        }
        jTextArea1.setText(getActivityString());
    }
    /** This returns a string which contains the recent activity. Each line is terminated
    * in a new line character.*/
    public String getActivityString(){
        Iterator itStrings = listRecentActivityStrings.iterator();
        String tempString = "";
        while (itStrings.hasNext()) {
            tempString = tempString + (String)itStrings.next() + "\n";
        }
        return tempString;
    }    
    public void update(Observable o, Object arg) {
//        System.out.println("jProjectTranslator notified of change o " + o.getClass().toString() + " arg " + arg.getClass().toString());
        // One of the observed objects has changed
        String strSQL = "";
        if (o instanceof jProjectReader) {
            int intPercentProgress = ((jProjectReader)o).getPercentProgress();
            
            try {
                Connection conn = ourDatabase.getConnection();
                Statement st = conn.createStatement();
                strSQL = "SELECT strTitle, dtsCreated FROM PUBLIC.PROJECT;";
                st = conn.createStatement();
                ResultSet rs = st.executeQuery(strSQL);
                rs.next();
                String strTitle = URLDecoder.decode(rs.getString(1), "UTF-8");
                DateTime dtCreated = fmtSQL.withZone(DateTimeZone.UTC).parseDateTime(rs.getString(2).substring(0, 19));
                String strCreated  = fmtDisplay.withZone(DateTimeZone.getDefault()).print(dtCreated);
                jTextField1.setText(strTitle);
                jTextField2.setText(strCreated);

            } catch (java.sql.SQLException e) {
                System.out.println("Error on SQL " + strSQL + e.toString());
            } catch (java.io.UnsupportedEncodingException e) {
                System.out.println("Error on decoding at " + strSQL + e.toString());
            }
            
            jProgressBar1.setValue(intPercentProgress);
            // ourTableModel.updateData(ourDatabase);
        }
        if (o instanceof jProjectWriter) {
            try {
                Connection conn = ourDatabase.getConnection();
                Statement st = conn.createStatement();
                if (arg instanceof BWFProcessor) {
                    // A file is being written, update the progress bar
                    BWFProcessor tempBWFProcessor = (BWFProcessor)arg;
                    String strUMID = URLEncoder.encode(tempBWFProcessor.getBextOriginatorRef(), "UTF-8");
                    strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET intCopied = " + tempBWFProcessor.getLByteWriteCounter() +  " WHERE strUMID = \'" + strUMID + "\';";
//                    System.out.println("SQL " + strSQL);
                    int i = st.executeUpdate(strSQL);
                    if (i == -1) {
                        System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                    }
                    

                }
                // Update the display
                strSQL = "SELECT SUM(intCopied), SUM(intIndicatedFileSize) FROM PUBLIC.SOURCE_INDEX;";
                st = conn.createStatement();
                ResultSet rs = st.executeQuery(strSQL);
                rs.next();
                long lPercent, lCopied, lIndicated;
                lCopied = rs.getLong(1);
                lIndicated = rs.getLong(2);
                if (lCopied < lIndicated && lIndicated > 0) {
                    lPercent =  100*lCopied/lIndicated;

                } else {
                    lPercent =  100;
                }
                jProgressBar1.setValue((int)lPercent);
                // ourTableModel.updateData(ourDatabase);
                System.out.println("Bytes written " + lCopied + " from a total target of " + lIndicated + " " + lPercent + " %");

            } catch (java.sql.SQLException e) {
                System.out.println("Error on SQL " + strSQL + e.toString());

            } catch (java.io.UnsupportedEncodingException e) {
                System.out.println("Error on decoding at " + strSQL + e.toString());

            }
        }
    }
    /**
     * This method is called from within the constructor to initialise the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jToolBar1 = new javax.swing.JToolBar();
        jButton1 = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();
        jButton3 = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jTextField1 = new javax.swing.JTextField();
        jTextField2 = new javax.swing.JTextField();
        jProgressBar1 = new javax.swing.JProgressBar();
        jPanel2 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTextArea1 = new javax.swing.JTextArea();
        jScrollPane2 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();
        jMenuBar1 = new javax.swing.JMenuBar();
        jMenu1 = new javax.swing.JMenu();
        jMenuItem1 = new javax.swing.JMenuItem();
        jMenuItem2 = new javax.swing.JMenuItem();
        jMenuItem4 = new javax.swing.JMenuItem();
        jMenuItem3 = new javax.swing.JMenuItem();
        jMenu2 = new javax.swing.JMenu();
        jMenuItem5 = new javax.swing.JMenuItem();
        jMenuItem6 = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Project Translator");
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        jToolBar1.setRollover(true);
        jToolBar1.setMinimumSize(new java.awt.Dimension(139, 32));
        jToolBar1.setPreferredSize(new java.awt.Dimension(139, 32));

        jButton1.setIcon(new javax.swing.ImageIcon(getClass().getResource("/jprojecttranslator/Gnome-document-open.png"))); // NOI18N
        jButton1.setToolTipText("Open");
        jButton1.setFocusable(false);
        jButton1.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jButton1.setMaximumSize(new java.awt.Dimension(48, 32));
        jButton1.setMinimumSize(new java.awt.Dimension(48, 32));
        jButton1.setPreferredSize(new java.awt.Dimension(48, 32));
        jButton1.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuOpen(evt);
            }
        });
        jToolBar1.add(jButton1);

        jButton2.setIcon(new javax.swing.ImageIcon(getClass().getResource("/jprojecttranslator/Gnome-document-save-as.png"))); // NOI18N
        jButton2.setToolTipText("Save As");
        jButton2.setFocusable(false);
        jButton2.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jButton2.setMaximumSize(new java.awt.Dimension(48, 32));
        jButton2.setMinimumSize(new java.awt.Dimension(48, 32));
        jButton2.setPreferredSize(new java.awt.Dimension(48, 32));
        jButton2.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuSave(evt);
            }
        });
        jToolBar1.add(jButton2);

        jButton3.setIcon(new javax.swing.ImageIcon(getClass().getResource("/jprojecttranslator/Gnome-application-exit.png"))); // NOI18N
        jButton3.setToolTipText("Exit");
        jButton3.setFocusable(false);
        jButton3.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jButton3.setMaximumSize(new java.awt.Dimension(48, 32));
        jButton3.setMinimumSize(new java.awt.Dimension(48, 32));
        jButton3.setPreferredSize(new java.awt.Dimension(48, 32));
        jButton3.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        jButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitForm(evt);
            }
        });
        jToolBar1.add(jButton3);

        jLabel1.setText("Project name");

        jLabel2.setText("Project creation date");

        jTextField1.setEditable(false);
        jTextField1.setMinimumSize(new java.awt.Dimension(200, 31));
        jTextField1.setPreferredSize(new java.awt.Dimension(200, 31));

        jTextField2.setEditable(false);
        jTextField2.setMinimumSize(new java.awt.Dimension(200, 31));
        jTextField2.setPreferredSize(new java.awt.Dimension(200, 31));

        jProgressBar1.setStringPainted(true);

        jPanel2.setPreferredSize(new java.awt.Dimension(600, 223));

        jScrollPane1.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        jScrollPane1.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);

        jTextArea1.setColumns(20);
        jTextArea1.setRows(5);
        jScrollPane1.setViewportView(jTextArea1);

        jTable1.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Name", "Source file", "Status", "Size"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false, false, false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        jScrollPane2.setViewportView(jTable1);

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
            .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 937, Short.MAX_VALUE))
            .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 937, Short.MAX_VALUE))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 230, Short.MAX_VALUE)
            .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel2Layout.createSequentialGroup()
                    .addGap(14, 14, 14)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 71, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addContainerGap(145, Short.MAX_VALUE)))
            .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel2Layout.createSequentialGroup()
                    .addGap(92, 92, 92)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 126, Short.MAX_VALUE)
                    .addContainerGap()))
        );

        jMenu1.setMnemonic('f');
        jMenu1.setText("File");

        jMenuItem1.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItem1.setMnemonic('o');
        jMenuItem1.setText("Open");
        jMenuItem1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuOpen(evt);
            }
        });
        jMenu1.add(jMenuItem1);

        jMenuItem2.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItem2.setMnemonic('v');
        jMenuItem2.setText("Save As");
        jMenuItem2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuSave(evt);
            }
        });
        jMenu1.add(jMenuItem2);

        jMenuItem4.setMnemonic('p');
        jMenuItem4.setText("Preferences");
        jMenuItem4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuPreferences(evt);
            }
        });
        jMenu1.add(jMenuItem4);

        jMenuItem3.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Q, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItem3.setMnemonic('x');
        jMenuItem3.setText("Exit");
        jMenuItem3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitForm(evt);
            }
        });
        jMenu1.add(jMenuItem3);

        jMenuBar1.add(jMenu1);

        jMenu2.setMnemonic('h');
        jMenu2.setText("Help");

        jMenuItem5.setMnemonic('a');
        jMenuItem5.setText("About");
        jMenuItem5.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuAbout(evt);
            }
        });
        jMenu2.add(jMenuItem5);

        jMenuItem6.setMnemonic('l');
        jMenuItem6.setText("Licence");
        jMenuItem6.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuLicence(evt);
            }
        });
        jMenu2.add(jMenuItem6);

        jMenuBar1.add(jMenu2);

        setJMenuBar(jMenuBar1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jToolBar1, javax.swing.GroupLayout.PREFERRED_SIZE, 607, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(layout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabel1)
                                    .addComponent(jLabel2))
                                .addGap(56, 56, 56)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jTextField2, javax.swing.GroupLayout.PREFERRED_SIZE, 188, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, 402, javax.swing.GroupLayout.PREFERRED_SIZE))))
                        .addGap(0, 330, Short.MAX_VALUE))
                    .addComponent(jPanel2, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 937, Short.MAX_VALUE)
                    .addComponent(jProgressBar1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jToolBar1, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(jTextField2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jProgressBar1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, 230, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void exitForm(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitForm
        exitForm();
    }//GEN-LAST:event_exitForm
    private void exitForm() {
        usersIniFile.WriteInteger("General","WindowWidth",ourWindow.getWidth());
        usersIniFile.WriteInteger("General","WindowHeight",ourWindow.getHeight());
        if (ourWindow.getLocationOnScreen().x > 0)
        usersIniFile.WriteInteger("General","WindowX",ourWindow.getLocationOnScreen().x);
        if (ourWindow.getLocationOnScreen().y > 0)
        usersIniFile.WriteInteger("General","WindowY",ourWindow.getLocationOnScreen().y);
        usersIniFile.WriteInteger("General", "SampleRate", intPreferredSampleRate);
        usersIniFile.WriteInteger("General", "XfadeLength", intPreferredXfadeLength);
        usersIniFile.WriteString("General", "FrameRate", String.format("%.2f", dPreferredFrameRate));
        usersIniFile.WriteString("General", "CurrentPath", fPath.toString());
        usersIniFile.WriteString("General", "LastFileOpenFilter", strLastFileOpenFilter);
        usersIniFile.WriteString("General", "LastFileSaveAsFilter", strLastFileSaveAsFilter);
        usersIniFile.UpdateFile();
        System.exit(0);        
    }
    private void menuOpen(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuOpen
        // File open 
        fc.setAcceptAllFileFilterUsed(false);
        fc.resetChoosableFileFilters();
        fc.setSelectedFile(new File(fPath,"Project"));
        Iterator itr = listReaders.iterator(); 
        jProjectReader tempProjectReader;
        javax.swing.filechooser.FileFilter tempFileFilter = null;
        while(itr.hasNext()) {
            tempProjectReader = (jProjectReader)(itr.next());
//            fc.addChoosableFileFilter( tempProjectReader.getFileFilter() );
            if (tempProjectReader.getFileFilter().getDescription().equalsIgnoreCase(strLastFileOpenFilter)) {
                tempFileFilter = tempProjectReader.getFileFilter();
            } else {
                fc.addChoosableFileFilter( tempProjectReader.getFileFilter() );
            }
            
        }
        if (tempFileFilter instanceof javax.swing.filechooser.FileFilter) {
            fc.setFileFilter(tempFileFilter);
        }
        if (!(fc.showOpenDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION)) {
            return;
        }
        if (fc.getSelectedFile().exists()) {
            fPath = fc.getSelectedFile().getParentFile();
        }
        System.out.println("The chosen file was " + fc.getSelectedFile());
        strLastFileOpenFilter = fc.getFileFilter().getDescription();
        System.out.println("The chosen file filter was " + strLastFileOpenFilter);
        itr = listReaders.iterator();
        while(itr.hasNext()) {
            tempProjectReader = (jProjectReader)(itr.next());
            if (fc.getFileFilter().getDescription().equalsIgnoreCase(tempProjectReader.getFileFilter().getDescription())) {
                System.out.println("A suitable file reader was found");
                tempProjectReader.addObserver(this);
                tempProjectReader.load(ourDatabase, listBWFProcessors, fc.getSelectedFile(),this);
            }
        }
    }//GEN-LAST:event_menuOpen

    private void menuSave(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuSave
        // Save the project
        fc.setAcceptAllFileFilterUsed(false);
        fc.resetChoosableFileFilters();
        String strOpenedFileName;
        try {
            strOpenedFileName = fc.getSelectedFile().toString();
        } catch (java.lang.NullPointerException e) {
            strOpenedFileName = "";
        }
        int intDot = strOpenedFileName.lastIndexOf(".");
        if (intDot > 0) {
            String strSaveFilename = strOpenedFileName.substring(0, intDot);
            fc.setSelectedFile(new File(strSaveFilename));
        }
        Iterator itr = listWriters.iterator(); 
        jProjectWriter tempProjectWriter;
        
        javax.swing.filechooser.FileFilter tempFileFilter = null;
        while(itr.hasNext()) {
            tempProjectWriter = (jProjectWriter)(itr.next());
//            fc.addChoosableFileFilter( tempProjectReader.getFileFilter() );
            if (tempProjectWriter.getFileFilter().getDescription().equalsIgnoreCase(strLastFileSaveAsFilter)) {
                tempFileFilter = tempProjectWriter.getFileFilter();
            } else {
                fc.addChoosableFileFilter( tempProjectWriter.getFileFilter() );
            }
            
        }
        if (tempFileFilter instanceof javax.swing.filechooser.FileFilter) {
            fc.setFileFilter(tempFileFilter);
        }
        
        
//        while(itr.hasNext()) {
//            // fc.addChoosableFileFilter( ((javax.swing.filechooser.FileFilter)(itr.next())) );
//            tempProjectWriter = (jProjectWriter)(itr.next());
//            fc.addChoosableFileFilter( tempProjectWriter.getFileFilter() );
//        }
        if (!(fc.showSaveDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION)) {
            return;
        }
        File file = fc.getSelectedFile();
        String path = file.getAbsolutePath();
        String extension = ((javax.swing.filechooser.FileNameExtensionFilter)fc.getFileFilter()).getExtensions()[0];
        if(!path.endsWith(extension)) {
            file = new File(path + "." + extension);
        }
        if ( file.exists() ) {  
            String msg = "The file " + file.getName().toString() + " already exists!\nDo you want to replace it?";  
            msg = java.text.MessageFormat.format( msg, new Object[] { file.getName() } );  
            String title = "Warning";  
            int option = JOptionPane.showConfirmDialog( this, msg, title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE );  
            if ( option == JOptionPane.NO_OPTION ) {  
                return; 
            }
        }
        System.out.println("The chosen file was " + file);
        strLastFileSaveAsFilter = fc.getFileFilter().getDescription();
        System.out.println("The chosen file filter was " + strLastFileSaveAsFilter);
        itr = listWriters.iterator();
        while(itr.hasNext()) {
            tempProjectWriter = (jProjectWriter)(itr.next());
            if (fc.getFileFilter().getDescription().equalsIgnoreCase(tempProjectWriter.getFileFilter().getDescription())) {
                System.out.println("A suitable file writer was found");
                if (fc.getSelectedFile().getParentFile().exists()) {
                    fPath = fc.getSelectedFile().getParentFile();
                }
                tempProjectWriter.addObserver(this);
                tempProjectWriter.save(ourDatabase, listBWFProcessors, file, this);
                // tempProjectWriter.deleteObserver(this);
            }
        }
    }//GEN-LAST:event_menuSave

    private void menuPreferences(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuPreferences
        jPreferences dlgPref = new jPreferences(this,true);
        dlgPref.setDropDowns(Integer.toString(intPreferredSampleRate) , String.format("%.2f", dPreferredFrameRate), intPreferredXfadeLength);
        dlgPref.setLocationRelativeTo(null);
        dlgPref.setVisible(true);
        
    }//GEN-LAST:event_menuPreferences

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        exitForm();
    }//GEN-LAST:event_formWindowClosing

    private void menuAbout(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuAbout
        jHelp dlgAbout = new jHelp(this,true);
        dlgAbout.loadAboutText();
        dlgAbout.setLocationRelativeTo(null);
        dlgAbout.setVisible(true);
    }//GEN-LAST:event_menuAbout

    private void menuLicence(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuLicence
        jHelp dlgLicence = new jHelp(this,true);
        dlgLicence.setWindowTitle("Licence");
        dlgLicence.setText("<html><h2>Project Translator</h2><br>"
                + "A program to translate audio editing projects from "
                + "one format to another. "
                + "<br>Copyright © 2011-2012  Arthur Moody"
                + "<br>Source code is available from here, https://sourceforge.net/projects/projecttrans"
                + "<br><br>This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by "
                + "the Free Software Foundation, either version 3 of the License, or (at your option) any later version."
                + "<br><br>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of "
                + "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details."
                + "<br><br>You should have received a copy of the GNU General Public License along with this program.  If not, see http://www.gnu.org/licenses/."
                + "<br><br>Project Translator uses third-party libraries. "
                + "<br>These are their licensing terms."
                + "<br><br><b>HSQLDB</b>"
                + "<br>Copyright © 2001-2010, The HSQL Development Group"
                + "<br>All rights reserved."
                + "<br>http://hsqldb.org/web/hsqlLicense.html"
                + "<br><br><b>OpenForecast</b>"
                + "<br>GNU Library or Lesser General Public License version 2.0 (LGPLv2)"
                + "<br>http://sourceforge.net/projects/openforecast"
                + "<br><br><b>dom4j</b>"
                + "<br>BSD style license"
                + "<br>http://dom4j.sourceforge.net/dom4j-1.6.1/license.html"
                + "<br><br><b>joda-time</b>"
                + "<br>Apache licence"
                + "<br>http://joda-time.sourceforge.net/license.html"
                + "<br><br><b>NanoHTTPD</b>"
                + "<br>Modified BSD licence"
                + "<br>Copyright © 2001,2005-2012 Jarno Elonen <elonen@iki.fi> and Copyright © 2010 Konstantinos Togias <info@ktogias.gr>"
                + "<br>http://elonen.iki.fi/code/nanohttpd/index.html"
                + "<br><br><b>Drop frame timecode calculations.</b>"
                + "<br>Code by David Heidelberger (http://www.davidheidelberger.com), adapted from Andrew Duncan's work (http://www.andrewduncan.ws).</html>");
        dlgLicence.setLocationRelativeTo(null);
        dlgLicence.setVisible(true);
    }//GEN-LAST:event_menuLicence
    /**
     @param args A filename reference to be loaded automatically, the file type will be guessed from the extension
     */
    public static void main(String args[]) {
        /*
         * Set the Nimbus look and feel
         */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /*
         * If Nimbus (introduced in Java SE 6) is not available, stay with the
         * default look and feel. For details see
         * http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(jProjectTranslator.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(jProjectTranslator.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(jProjectTranslator.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(jProjectTranslator.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>
        // The first argument given on the command line could be the source project file.
        if ((args.length > 0) && (args[0].length()>0)) {
            strSourceProjectFile = args[0];
            if (!(new File(strSourceProjectFile)).exists()) {
                // File does not exist!
                System.out.println("Source project file not found...");
                System.exit(0);
            }
        }
        // This is the ini file which stores the users own settings in their home folder
        usersIniFile = new JIniFile(System.getProperty("user.home") + "/jProjectTranslator.ini");
        /* Create a local database in memory
         * 
         */
        ourDatabase = new database();
        
        strBuild = rbProject.getString("application.buildnumber");
        System.out.println("Build number is " + strBuild);
        /* Create a web server for debugging if required.
         * This allows the database table to be viewed in a web browser
         */
        int intHTTPPort = usersIniFile.ReadInteger("General","intHTTPPort",0);
        intPreferredSampleRate = usersIniFile.ReadInteger("General","SampleRate",48000);
        dPreferredFrameRate = Double.parseDouble(usersIniFile.ReadString("General","FrameRate","25"));
        intPreferredXfadeLength = usersIniFile.ReadInteger("General","XfadeLength",960);
        fPath = new File( usersIniFile.ReadString("General","CurrentPath",System.getProperty("user.home")) );
        strLastFileOpenFilter = usersIniFile.ReadString("General","LastFileOpenFilter","");
        strLastFileSaveAsFilter = usersIniFile.ReadString("General","LastFileSaveAsFilter","");
        
        if (intHTTPPort > 0) {
            try {
                HTTPD oWebServer = new HTTPD(intHTTPPort);
                System.out.println("HTTP server created, browse to http://localhost:"+ intHTTPPort +"/debug.html ");
            } catch (IOException ioe) {
                System.out.println("Failed to start HTTP server on port "+ intHTTPPort +" , " + ioe.toString() + ".");
            }
        }
        /*
         * Add the available project readers and writers here so we can filter the source and dest file types
         */
        listReaders.add(new jProjectReader_VCS());
        listReaders.add(new jProjectReader_ARDOUR());
        listReaders.add(new jProjectReader_AES31());
        listWriters.add(new jProjectWriter_AES31());
        listWriters.add(new jProjectWriter_ARDOUR());
        
        
        /*
         * Create and display the form
         */
        java.awt.EventQueue.invokeLater(new Runnable() {

            public void run() {
                ourWindow = new jProjectTranslator();
                java.awt.Dimension screenSize = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
                int intWindowWidth, intWindowHeight, intWindowX, intWindowY;
                intWindowWidth = usersIniFile.ReadInteger("General","WindowWidth",600);
                intWindowHeight = usersIniFile.ReadInteger("General","WindowHeight",500);
                intWindowX = usersIniFile.ReadInteger("General","WindowX",(screenSize.width-intWindowWidth)/2);
                intWindowY = usersIniFile.ReadInteger("General","WindowY",(screenSize.height-intWindowHeight)/2);
                ourWindow.setSize(new java.awt.Dimension(intWindowWidth, intWindowHeight));
                ourWindow.setLocation(intWindowX,intWindowY);
                ourWindow.setVisible(true);
            }
        });
    }
    public void updateTable() {
        ourTableModel.updateData(ourDatabase);
    }
    class soundFilesTableModel extends AbstractTableModel {
        final String[] columnNames = new String [] {"Name" , "Source file" , "Status", "Duration", "Sample rate"};
        Object[][] data = new Object [][] { };
        protected database ourDatabase;
        protected String strSQL;
        protected Statement st;
        protected Connection conn;
        protected ResultSet rs;
        // Vector vRows = new Vector(4);
        @Override
        public int getColumnCount() {
            return columnNames.length;
        }
        @Override
        public int getRowCount() {
            return data.length;
        }
        @Override
        public String getColumnName(int col) {
            return columnNames[col];
        }
        @Override
        public Object getValueAt(int row, int col) {
            return data[row][col];
            //return (vRows.get(row)).get(col);
            // return ((Vector)vRows.get(row)).get(col);
        }
        @Override
        public Class getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }
        /*
        * This method controls updates to the table data 
        */
        @Override
        public void setValueAt(Object value, int row, int col) {

        }
        /*
        *This method allows us to add a row with the eight objects supplied
        */
        @Override
        public boolean isCellEditable(int row, int col) {
            return false;
        }
        public boolean updateData(database setDatabase) {
            ourDatabase = setDatabase;
            int row = 0;
            try {
                conn = ourDatabase.getConnection();
                st = conn.createStatement();
                strSQL = "SELECT COUNT(*) FROM PUBLIC.SOURCE_INDEX ;";
                rs = st.executeQuery(strSQL);
                rs.next();
                data = new Object [rs.getInt(1)][5];
                long lDuration, lSampleRate;
                if (rs.getInt(1) > 0){
                    strSQL = "SELECT strName, strSourceFile, intIndicatedFileSize, intSampleRate, dDuration FROM PUBLIC.SOURCE_INDEX ORDER BY intIndex;";
                    rs = st.executeQuery(strSQL);
                    while (rs.next()) {
                        data[row][0] = URLDecoder.decode(rs.getString(1), "UTF-8");
                        data[row][1] = URLDecoder.decode(rs.getString(2), "UTF-8");
                        lDuration = rs.getLong(3);
                        if (lDuration > 0) {
                            data[row][2] = "Found";
                        } else  {
                            data[row][2] = "Not found";
                        }
                        
                        data[row][3] = getTimeString(rs.getFloat(5));
                        // Get the file sample rate and compare this with the desired sample rate for the project, they should be the same.
                        // intPreferredSampleRate
                        lSampleRate = rs.getLong(4);
                        // <html><font color="FF1493">text text text text text</font></html>
                        if (lSampleRate == intPreferredSampleRate) {
                            data[row][4] = "<html><font color=\"000000\">" + lSampleRate + "</font></html>";
                        } else {
                            data[row][4] = "<html><font color=\"FF4500\">" + lSampleRate + "</font></html>";
                        }
//                        data[row][4] = "" + rs.getLong(4);
                        row++;
                    }
                    
                } 
                fireTableChanged(null);
            } catch (java.sql.SQLException e) {
                System.out.println("Error on SQL " + e.toString());
                return false;
            } catch (java.io.UnsupportedEncodingException e) {
                System.out.println("Error on URL decode " + e.toString());
            }
            
            return true;
        }
        
    }
    public static String getNewUSID() {
        String strUSID = "UKGNU" + String.format("%06d", randomNumber.nextInt(1000000)) + String.format("%06d", randomNumber.nextInt(1000000));
        DateTime dtsCreated = (new DateTime()).withZone(DateTimeZone.UTC);
        strUSID = strUSID + fmtHHMMSS.print(dtsCreated);
        strUSID = strUSID + String.format("%09d", randomNumber.nextInt(1000000000));
        return strUSID;
        
    }
    public static String getTimeString(double dSeconds) {
        long lHours = (long)dSeconds/(60*60);    
        dSeconds = dSeconds - (lHours*60*60);
        long lMinutes = (long)dSeconds/(60);    
        dSeconds = dSeconds - (lMinutes*60);
        return String.format("%02d", lHours) + ":" + String.format("%02d", lMinutes) + ":" + String.format("%1$05.2f", dSeconds);
        
    }    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton3;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenu jMenu2;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JMenuItem jMenuItem1;
    private javax.swing.JMenuItem jMenuItem2;
    private javax.swing.JMenuItem jMenuItem3;
    private javax.swing.JMenuItem jMenuItem4;
    private javax.swing.JMenuItem jMenuItem5;
    private javax.swing.JMenuItem jMenuItem6;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JProgressBar jProgressBar1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JTable jTable1;
    private javax.swing.JTextArea jTextArea1;
    private javax.swing.JTextField jTextField1;
    private javax.swing.JTextField jTextField2;
    private javax.swing.JToolBar jToolBar1;
    // End of variables declaration//GEN-END:variables
}
