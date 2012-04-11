package jprojecttranslator;
/*
 * BWFProcessor.java
 *
 * Created on 09 September 2005, 19:51
 */

/**
 * A general purpose handler for BWF files.
 * @author  moodya71
 */
import java.io.*;
import java.util.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.util.Observable;

public class BWFProcessor extends Observable implements Runnable {
    private long lSourceFileStart = 0;
    private long lSourceFileEnd = 0;
    // This sets a flag to show that we're reading a miltipart files where wave files are concatenated
    private boolean bMultipart = false;
    private boolean bHasBextChunk = false;
    private static int objectCount = 0;
    private File srcFile;
    private File destFile;
    /** This is a vector which contains objects of type chunk. These are chunks from the start of the<br>
     * wave file up to but not including the data chunk.
     */
    public Vector startChunks = new Vector(5);
    /** This is a vector which contains objects of type chunk. These are chunks from the end of the<br>
     * wave file starting after (but not including) the data chunk.
     */
    public Vector endChunks = new Vector(3);
    private ByteBuffer sourceBytes = ByteBuffer.allocate(262144);
    private FileInputStream inFile = null;
    private FileOutputStream outFile = null;
    private FileChannel inChannel, outChannel;
    private String tempckID;
    private long tempckSIZE, dataChunkSize;
        /** <p>Error codes return are<br>
         * 0 All OK<br>
         * 1 Parsing error of source file, returned by running thread<br>
         * 2 Unable to open source file for some reason, returned only by constructor<br>
         * 3 Unable to open destination file, returned by constructor or running thread<br>
         * 4 The source file is still being written so can't start processing yet.<br></p>
         */    
    private int errorcode = 0;
        /** <p>Status codes return are<br>
         * 0 Not started yet<br>
         * 1 Parsing source file<br>
         * 2 Parsed source file<br>
         * 3 Copying file<br>
         * 4 Finished<br>
         * 5 Failed<br></p>
         */    
    private int status = 0;
    private long lCalculatedFileSize, lIndicatedFileSize;
    private long lTotalFileSize;
    private long dataChunkOffset;
    // Create the thread, we won't start it yet.
    private Thread ourThread; 
    private long lBytePointer;
    private long lByteWriteCounter = 0;
    private long lastActivity;
    private String tempDestName, tempSourceName;
    private File tempDestFile, tempSourceFile;
    /** This value shows if a destination file should be written. This allows you
     * to use this class just for reading in the chinks of a file without creating a new one.
     */
    private boolean readOnly = false;
    private boolean bDebug = true;
    private Iterator chunkIterator;
    /* This string stores the originator reference, 
     * when the write process is started the bext chunk is deleted
     * but we might still need this string so we save it here
     */
    private String strOriginatorReference = "";
    
    
    
    
    /** Creates a new instance of BWFProcessor */
    public BWFProcessor(File setSrcFile, File setDestFile) {
        objectCount++;

        
        srcFile = setSrcFile;
        if (!srcFile.exists() || !srcFile.canRead()){
            errorcode = 2;
            return;
        }
        destFile = setDestFile;
        if (destFile.exists()) {
            errorcode = 3;
            return;
        }
        // Need to generate a temporary name for the source file
        tempSourceName = srcFile.getName().substring(0,(srcFile.getName().length())-3) + "tmp";
        tempSourceFile = new File(srcFile.getParent(),tempSourceName);        
        /** Now to check that the source file is not being written to we will try to rename it.
         * If this works we will change the name back again. If it does not work we 
         * will return with an error code of 4 which indicates that the source file
         * can not be converted yet.
         */
        if (tempSourceFile.exists()) {
            // Temp file already exists, so does the source file.
            tempSourceFile.delete();
        }
        if (srcFile.renameTo(tempSourceFile)) {
            // We were able to rename the source file, good. Change the name back again
            tempSourceFile.renameTo(srcFile);
        } else {
            // We were unable to rename the source file so it is stil being written
            errorcode = 4;
            return;
        }
        
        // Need to generate a temporary name for the destination file
        tempDestName = destFile.getName().substring(0,(destFile.getName().length())-3) + "tmp";
        tempDestFile = new File(destFile.getParent(),tempDestName);
        lTotalFileSize = (long)srcFile.length();
        try {
            inFile = new FileInputStream(srcFile);
        } catch (Exception e) {
            errorcode = 2;
            System.out.println("Error, can not open input file");
            // e.printStackTrace();
            return;
        }
        inChannel = inFile.getChannel();
        try {
            outFile = new FileOutputStream(tempDestFile);
        } catch (Exception e) {
            errorcode = 3;
            System.out.println("Error, can not open output file");
            e.printStackTrace();
            return;
        }
        outChannel = outFile.getChannel();        
        // All OK so far, we just need to set the thread running.
        ourThread = new Thread(this,"BWF_processor");
        ourThread.setDaemon(true);
        ourThread.start();
        status = 1;
        lastActivity = System.currentTimeMillis()/1000;
    }
    /** Alternate constructor for use when a destination file is not written.*/
    public BWFProcessor(File setSrcFile) {
        objectCount++;
        readOnly = true;
        
        srcFile = setSrcFile;
        if (!srcFile.exists() || !srcFile.canRead()){
            errorcode = 2;
            return;
        }
        // Need to generate a temporary name for the source file
        tempSourceName = srcFile.getName().substring(0,(srcFile.getName().length())-3) + "tmp";
        tempSourceFile = new File(srcFile.getParent(),tempSourceName);        
        /** Now to check that the source file is not being written to we will try to rename it.
         * If this works we will change the name back again. If it does not work we 
         * will return with an error code of 4 which indicates that the source file
         * can not be converted yet.
         */
        if (tempSourceFile.exists()) {
            // Temp file already exists, so does the source file.
            tempSourceFile.delete();
        }
        if (srcFile.renameTo(tempSourceFile)) {
            // We were able to rename the source file, good. Change the name back again
            tempSourceFile.renameTo(srcFile);
        } else {
            // We were unable to rename the source file so it is stil being written
            errorcode = 4;
            return;
        }
        lTotalFileSize = (long)srcFile.length();
        try {
            inFile = new FileInputStream(srcFile);
        } catch (Exception e) {
            errorcode = 2;
            System.out.println("Error, can not open input file");
            // e.printStackTrace();
            return;
        }
        inChannel = inFile.getChannel();
        // All OK so far, we just need to set the thread running.
        ourThread = new Thread(this,"BWF_processor");
        ourThread.setDaemon(true);
        ourThread.start();
        lastActivity = System.currentTimeMillis()/1000;
    }
    public BWFProcessor() {

    }
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here
        BWFProcessor ourProcessor = new BWFProcessor(new File("D:/audio/input.wav"), new File("D:/audio/output.wav"));
        while (ourProcessor.isAlive()) {
            try {
                Thread.sleep(1500); // Sleep for 1.5s
                System.out.println("Bytes written so far " + ourProcessor.getLBytePointer()); 
            } catch (InterruptedException e) {
                System.out.println("Sleep interrupted." );            
            }
        }
        System.out.println("Error code is " + ourProcessor.getErrorCode());
        
    }
    
    public static int getObjectCount(){
        return objectCount;
    }    
    
    protected void finalize() {
        objectCount--;
    }    
    
    public int getErrorCode(){
        return errorcode;
    }
    
    public boolean isAlive(){
        return ourThread.isAlive();
    }
    
    public long getLBytePointer(){
        return lBytePointer;
    }
    public long getLByteWriteCounter() {
        return lByteWriteCounter;
    }    

    public long getIndicatedFileSize() {
        return lIndicatedFileSize;
    }
 
    public int getStatus(){
        return status;
    }
    public boolean setSrcFile(File setSrcFile){
        objectCount++;
        readOnly = true;
        srcFile = setSrcFile;
        if (srcFile.exists() && srcFile.canRead()){
            return true;
        }
        return false;
    }
    /**
     * Find the details of the source file.
     * @return A file object representing the source file.
     */
    public String getSrcFile(){
        return srcFile.toString();
    }
    /**
     * Find the details of the destination file.
     * @return A file object representing the destination file.
     */
    public String getDestFile(){
        return destFile.toString();
    }
    
    
    /**
     * Find the time when the last file read / write occurred.
     * @return  The date/time in ms since the epoch.
     */
    public long getLastActivity(){
        return lastActivity;
    }
    /**
     * Read a file, fix the offsets and then write it without any edits to the chunks.
     */
    public void run() {
        readFile(0,0);
        // Now if the errorcode is still 0 we can write the data to the output file unless we're in readOnly mode.
        status = 2;
        if (!readOnly) {
            status = 3;
            writeFile();
        }
        try {
            inFile.close();
        } catch (Exception e) {
            status = 5;
        }
        status = 4;
            
        
    }
    /**
     * This is used to set whether the source file has more than one riff wave file embedded within it.
     * @param setMultipart Set to true if you ant to start reading the data later than the start of the file.
     */
    public void setMultipart (boolean setMultipart) {
        bMultipart = setMultipart;
    }
    /**
     * Read a source file.
     * The source file is parsed so all the chunks at the start can be read and or edited.
     * The file size values are recalculated.
     * It is possible to read audio data from a file which contains multiple riff wave files stacked end to end.
     * @param setSourceFileStart    This is an offset in bytes where the start of the riff wave file should be.
     * @param setSourceFileEnd      This is an offset in bytes where the end of the riff wave file should be.
     * @return 
     */
    public boolean readFile(long setSourceFileStart, long setSourceFileEnd) {
        if (bMultipart) {
            lSourceFileStart = setSourceFileStart;
            lSourceFileEnd = setSourceFileEnd;
        } else {
            lSourceFileStart = 0;
            lSourceFileEnd = 0;
        }
        
        try {
            lTotalFileSize = (long)srcFile.length();
            inFile = new FileInputStream(srcFile);
            inChannel = inFile.getChannel();
            inChannel.position(lSourceFileStart);
            inChannel.read(sourceBytes);
            // The buffer should be full

        } catch (Exception e) {
            e.printStackTrace();
        }
        // Check that RIFF and WAVE are present in the first 12 bytes
        sourceBytes.order(ByteOrder.LITTLE_ENDIAN);
        sourceBytes.flip();
        if (bDebug) {
            System.out.println("" + sourceBytes.remaining() + " bytes remaining.");
        }
        StringBuffer strTemp;
        // Check the first four characters are RIFF
        strTemp = new StringBuffer(4);
        for (int i=0; i<4; i++) {
            strTemp.append((char)sourceBytes.get());
        }
        // Position now four bytes from start of file. strTemp should be RIFF check this here
        if (!(strTemp.toString()).equalsIgnoreCase("RIFF")) {
            // First four characters are wrong
            errorcode = 1;
            status = 5;
        }
        lIndicatedFileSize = sourceBytes.getInt() + 8; // There are 8 extra bytes at the start 'RIFF (filesize(4))'
        // Catch a special case in VCS where indicated size in 0 when the data is MP3
        if (lIndicatedFileSize == 8 && lSourceFileEnd > lSourceFileStart) {
            lIndicatedFileSize = lSourceFileEnd - lSourceFileStart;
        }
        //  Otherwise we can simple calculate the expected end of the source file
        if (lSourceFileEnd == 0) {
            lSourceFileEnd = lSourceFileStart + lIndicatedFileSize;
        }
        // Position now eight bytes from start of file and we have read the indicated file size of all the chunks including the RIFF/WAVE headers.
        strTemp = new StringBuffer(4);
        for (int i=0; i<4; i++) {
            strTemp.append((char)sourceBytes.get());
        }
        // Position now twelve bytes from start of file. strTemp should be WAVE check this here
        if (!(strTemp.toString()).equalsIgnoreCase("WAVE")) {
            // Characters 8 - 11 are wrong
            errorcode = 1;
            status = 5;
        }
        if (bDebug && errorcode == 0) {
            System.out.println("Start of file looks OK, now looking at end chunks.");
        }
        extractChunks(sourceBytes,startChunks);
        if (errorcode!= 0) {
            System.out.println("Error, can not parse input file " + srcFile);
//            jMediaHarvest.writeErrorString("Error, can not parse input file " + srcFile);
            try {
                outFile.close();
                inFile.close();
                // Rename the source file so we don't pick it up again
                String newSourceName = srcFile.getName().substring(0,(srcFile.getName().length())-3) + "err";
                // System.out.println("Renaming source file to " + srcFile.getParent() + " / " + newSourceName);
                srcFile.renameTo(new File(srcFile.getParent(),newSourceName));
            } catch (Exception e) {
		status = 5;
            }
            return false;
        }
        /** All of the chunks at the start of the file have been read. there could be more chunks at the
         * end of the file and we have to take these in to account when recalculating the file length.
         * To check for chunks at the end we need to compare all the chunks we know about and see if they add up to
         * the current file size.
         * The file size is stored in bytes 4 to 7 as a little endian int and should add up to
         * allchunks(chunksize + 8)
         * The total file size is actually 12 bytes more than this due to the RIFF/WAVE and filesize characters at the beginning of the file.
         */
        lCalculatedFileSize = dataChunkSize + 8 + 12;
        // Thats the data chunk added, now to add the other chucks.
        chunkIterator = startChunks.iterator();
        while (chunkIterator.hasNext()){
            lCalculatedFileSize = lCalculatedFileSize + 8 + ((chunk)chunkIterator.next()).getoriginalckSIZE();
        }
        // If lCalculatedFileSize is equal to lIndicatedFileSize then we have read all the chunks.
        System.out.println("Total file size is " + lTotalFileSize);
        System.out.println("Indicated file size is " + lIndicatedFileSize);
        System.out.println("Calculated file size is " + lCalculatedFileSize);
        // Catch a special case where dataChunkSize = 0 in VCS files where the data block contains mp3 data.
        if (dataChunkSize == 0 && lIndicatedFileSize > lCalculatedFileSize) {
            dataChunkSize = lIndicatedFileSize - lCalculatedFileSize;
            lCalculatedFileSize = lIndicatedFileSize;
            System.out.println("After correcting for the special case where VCS has set the data chunk size to 0...");
            System.out.println("Total file size is " + lTotalFileSize);
            System.out.println("Indicated file size is " + lIndicatedFileSize);
            System.out.println("Calculated file size is " + lCalculatedFileSize);
        }
        if (lIndicatedFileSize != lCalculatedFileSize) {
            /** Either the file is corrupt or there are more chunks at the end.
             * To read the end of the file we need to calculate where the data chunk ends
             * This should be at lCalculatedFileSize + 8
             * We also need to know the actual length of the file so we can read the end of it
             * in to a new ByteBuffer, this should be size of srcFile.
             * But first we should check that the amount of data which seems to be at
             * the end of the file is a sensible amount.
             */
            if (lTotalFileSize-lCalculatedFileSize > 10000000 && !bMultipart) {
                // This can't be right, file must be corrupt or it is a multipart file
                errorcode = 1;
                status = 5;
                System.out.println("Error, can not process input file " + srcFile);
//                jMediaHarvest.writeErrorString("Error, can not process input file " + srcFile);
                // e.printStackTrace();
                return false;
            }
            ByteBuffer extraSourceBytes;
            int intEndBytes;
            if (bMultipart) {
                intEndBytes = (int)(lIndicatedFileSize - lCalculatedFileSize);
            } else {
                intEndBytes = (int)(lTotalFileSize - lCalculatedFileSize);
            }
            if (intEndBytes < 8) {
                return false;
            }
            System.out.println("Allocating " + intEndBytes + " bytes for the chunks at the end of the file.");
            extraSourceBytes = ByteBuffer.allocate(intEndBytes);
            extraSourceBytes.order(ByteOrder.LITTLE_ENDIAN);
            try {
                inChannel.position(lSourceFileStart + lCalculatedFileSize);
                inChannel.read(extraSourceBytes);
                // The buffer should be full

            } catch (Exception e) {
                errorcode = 1;
                status = 5;
                System.out.println("Error, can not read input file while reading chunks at end of file " + srcFile);
    //            jMediaHarvest.writeErrorString("Error, can not read input file while reading chunks at end of file  " + srcFile);
                // e.printStackTrace();
                return false;
            }
            // The buffer extraSourceBytes should now contain the chunks at the end of the file.
            extraSourceBytes.flip();
            extractChunks(extraSourceBytes,endChunks);
            // The extra chunks have now been read so the file size calculation should be OK.
            lCalculatedFileSize = dataChunkSize + 8 + 12;
            // Thats the data chunk added, now to add the other chucks.
            chunkIterator = startChunks.iterator();
            while (chunkIterator.hasNext()){
                lCalculatedFileSize = lCalculatedFileSize + 8 + ((chunk)chunkIterator.next()).getoriginalckSIZE();
            }
            chunkIterator = endChunks.iterator();
            while (chunkIterator.hasNext()){
                lCalculatedFileSize = lCalculatedFileSize + 8 + ((chunk)chunkIterator.next()).getoriginalckSIZE();
            }
            // If lCalculatedFileSize is equal to lIndicatedFileSize then we have read all the chunks.
            // System.out.println("Indicated file size is " + lIndicatedFileSize);
            // System.out.println("Calculated file size is " + lCalculatedFileSize);
            if (lIndicatedFileSize != lCalculatedFileSize) {
                // errorcode = 1;
                System.out.println("WARNING - Potential problem with file " + srcFile + " calculated and indicated sizes do not match, using calculated size.");
                System.out.println("Total file size is " + lTotalFileSize);
                System.out.println("Indicated file size is " + lIndicatedFileSize);
                System.out.println("Calculated file size is " + lCalculatedFileSize);
                lSourceFileEnd = lSourceFileStart + lCalculatedFileSize;
//                jMediaHarvest.writeErrorString("Potential problem with file " + srcFile + " calculated and indicated sizes do not match.");

                // return;
            }
        }
        if (lTotalFileSize != lCalculatedFileSize && !bMultipart) {
            errorcode = 1;
            System.out.println("Error, can not process input file " + srcFile+ " calculated and total file sizes do not match.");
//            jMediaHarvest.writeErrorString("Error, can not process input file " + srcFile+ " calculated and total file sizes do not match.");
            System.out.println("Total file size is " + lTotalFileSize);
            System.out.println("Indicated file size is " + lIndicatedFileSize);
            System.out.println("Calculated file size is " + lCalculatedFileSize);
            try {
                inFile.close();
            } catch (Exception e) {
                status = 5;
            }

            return false;
        }
        try {
            inFile.close();
        } catch (java.io.IOException e) {

        }
        return true;
    }
    /**
     * Write an audio file.
     * The chunks at the start of the file will include any edited or added chunks.
     * The data chunk and end chunks will simply be copied from the source file.
     * The sizes written to the file have been recalculated to allow for the new or edited chunks.
     * @param setDestFile   This is a File object which represents where the file should be written. 
     */
    public void writeFile(File setDestFile) {
        try {
            destFile = setDestFile;
            tempDestName = destFile.getName().substring(0,(destFile.getName().length())-3) + "tmp";
            tempDestFile = new File(destFile.getParent(),tempDestName);
            if (tempDestFile.exists()) {
                tempDestFile.delete();
            }
            outFile = new FileOutputStream(tempDestFile);
            outChannel = outFile.getChannel();
            inFile = new FileInputStream(srcFile);
            inChannel = inFile.getChannel();
            writeFile();
        } catch (java.io.FileNotFoundException e) {
            System.out.println("Error while writing new file " + destFile.toString());
        }
        
    }

    private void writeFile(){
        lByteWriteCounter = 0;
        // First update the first 12 bytes of the ByteBuffer sourceBytes with the new file size
        // So we need to calculate the new file size.        
        lCalculatedFileSize = dataChunkSize + 8 + 12;
        chunk tempChunk;
        // Thats the data chunk size added in, now to add up the other chucks.
        chunkIterator = startChunks.iterator();
        while (chunkIterator.hasNext()){
            tempChunk = ((chunk)chunkIterator.next());
            lCalculatedFileSize = lCalculatedFileSize + 8 + tempChunk.getckSIZE();
            System.out.println("Chunk " + tempChunk.getckID() + "  " + tempChunk.getckSIZE());
        }
        System.out.println("Chunk data  " + dataChunkSize);
        chunkIterator = endChunks.iterator();
        while (chunkIterator.hasNext()){
            tempChunk = ((chunk)chunkIterator.next());
            lCalculatedFileSize = lCalculatedFileSize + 8 + tempChunk.getckSIZE();
            System.out.println("Chunk " + tempChunk.getckID() + "  " + tempChunk.getckSIZE());
        }            
        sourceBytes.limit(12);
        // Limit set 
        sourceBytes.position(4);
        sourceBytes.putInt((int)lCalculatedFileSize - 8);
        // Updated file size written
        sourceBytes.position(0);
        try {
            outChannel.write(sourceBytes);
            lByteWriteCounter = lByteWriteCounter + 12;
            // Thats the first 12 bytes written, now we need to add the bext chunk
            chunkIterator = startChunks.iterator();  
            while (chunkIterator.hasNext()){
                  tempChunk = (chunk)chunkIterator.next();
                  // Is this the bext chunk?
                  if ((tempChunk.getckID()).equalsIgnoreCase("bext")){
                      // Yes we have found the bext chunk, this needs to go at the start of the file
                      outChannel.write(tempChunk.getBytes());
                      lByteWriteCounter = lByteWriteCounter + tempChunk.getckSIZE() + 8;
                      // Now remove it from the Vector so it does not get added again
                      startChunks.remove(tempChunk);
                      bHasBextChunk = false;
                      break;
                  }
            }            
            // Now write the data from the other start chunks
            chunkIterator = startChunks.iterator();
            while (chunkIterator.hasNext()){
                tempChunk = (chunk)chunkIterator.next();
                outChannel.write(tempChunk.getBytes());
                lByteWriteCounter = lByteWriteCounter + tempChunk.getckSIZE() + 8;
            }
            // The start chunks are written, now we need to add the remainder of the source file starting from the data chunk.
            inChannel.position(dataChunkOffset);
            lBytePointer = dataChunkOffset;
            long lEndByte;
            if (bMultipart) {
//                lEndByte = lCalculatedFileSize + 12 + lSourceFileStart;
                lEndByte = lSourceFileEnd;
            } else {
                lEndByte = inChannel.size();
            }
            System.out.println("Calculated file size is " + lCalculatedFileSize + " data chunk offset is " + dataChunkOffset);
            System.out.println("Writing output file starting at " + lBytePointer + " offset on input file and finishing at  " + lEndByte + " offset on input file.");
            while (lBytePointer<lEndByte) {
                
                try {
                    Thread.sleep(Main.randomNumber.nextInt(200) + 100); // Sleep for about 0.3s
                    // Thread.sleep(Main.randomNumber.nextInt(200) + 500 +(lEndByte/600000)); // Sleep for about 0.6 plus 1s per 600M file size
                } catch (InterruptedException e) {
                    System.out.println("Sleep interrupted." );            
                } 
                synchronized(Main.randomNumber) {
                    // There is only one of those objects so this code can only be excecuted by one thread at a time.

                    if (lEndByte-lBytePointer<5000000) {
                        lByteWriteCounter = lByteWriteCounter + lEndByte-lBytePointer;
                        lBytePointer += inChannel.transferTo(
                            lBytePointer,
                            lEndByte-lBytePointer,
                            outChannel); 
                        
                    } else {
                        lBytePointer += inChannel.transferTo(
                            lBytePointer,
                            5000000, 
                            outChannel);
                        lByteWriteCounter = lByteWriteCounter + 5000000;
                    }

                    lastActivity = System.currentTimeMillis()/1000;                    
                    
                }

                // No matter how large the file being copied the thread should pass this point regularly.
                setChanged();
                notifyObservers();
               
                // Good place for a yield or WDT?
            }
            outFile.close();
            // Add a cvt file with the same name as the source file so we don't pick it up again
            String newSourceName = srcFile.getName().substring(0,(srcFile.getName().length())-3) + "cvt";
            // System.out.println("Renaming source file to " + srcFile.getParent() + " / " + newSourceName);
            // srcFile.renameTo(new File(srcFile.getParent(),newSourceName));
            File cvtFile = new File(srcFile.getParent(),newSourceName);
            cvtFile.createNewFile();
            
            // Rename the dest file extension so that it will be picked up by the next process
            tempDestFile.renameTo(destFile);
            inFile.close();

        } catch (Exception e) {
            System.out.println("Error while writing new file.");
//            jMediaHarvest.writeErrorString("Error while writing new file." + destFile);
            errorcode = 3;
            e.printStackTrace();
        }         
    }
    /**
     * This will process the given Byte Buffer and add chunk objects to the given vector.
     * The values of errorcount and data chunk information will be updated if required.
     * When this method is called the pointer must be at the start of the ckID
     * @param inputData     This is a ByteBuffer which should contain some chunks.
     * @param chunkVector   This is a vector to which the chunks will be added.
     */
    private void extractChunks(ByteBuffer inputData, Vector chunkVector) {
        while (inputData.hasRemaining()) {
            // We are going to read the next 8 bytes, need to check that there are 8 bytes available.
            if (bDebug) {
                System.out.println(inputData.remaining() + " bytes remaining.");
            }
            if (inputData.remaining() < 8) {
                // Not enough bytes left and data block not found!
                errorcode = 1;
                System.out.println("Unexpectedly reached the end of data, file could be corrupt!");  
                return;
            }
            StringBuffer strTemp = new StringBuffer(4);
            for (int i=0; i<4; i++) {
                strTemp.append((char)inputData.get());
            }
            // Position now four bytes in.
            tempckID = strTemp.toString();   
            tempckSIZE = inputData.getInt();
            if (tempckID.equalsIgnoreCase("bext")){
                bHasBextChunk = true;
            }

            if (bDebug) {
                System.out.println("Found chunk  " + tempckID + " size " + tempckSIZE + " at position " + (inputData.position() - 8));
            }
            // Position now eight bytes in
            if (tempckID.equalsIgnoreCase("data")) {
                // We have found the data chunk, don't want to load this in, just save the size.
                dataChunkSize = tempckSIZE;
                dataChunkOffset = lSourceFileStart + inputData.position() - 8; // This is the offset from the start of file
                // System.out.println("Data chunk size is   " + dataChunkSize);
                return;
            } else {
                try {

                    inputData.position(inputData.position() - 8);
                    // Position now back at the start of the chunk
                    inputData.limit(inputData.position() + (int)tempckSIZE + 8);
                    // Limit now set at end of current chunk
                } catch (IllegalArgumentException e) {
                    errorcode = 1;
                    System.out.println("Unexpectedly reached the end of data while extracting chunk " + tempckID + ", file could be corrupt!\nError code is " + e);
                    return;
                }
                if (tempckID.equalsIgnoreCase("bext")) {
                    chunkVector.add(new chunk(inputData.slice(),858));
                } else {
                    chunkVector.add(new chunk(inputData.slice()));
                }

                // Chunk created
                inputData.limit(inputData.capacity());
                // Limit set back to the end
                inputData.position(inputData.position() + (int)tempckSIZE + 8);
                // Position now set at start of next chunk                

            } 
        }
        
    }
    /**
     * This is used to find out the indicated sample rate of the file from the fmt chunk
     * @return Returns an int, e.g. 48000
     */
    public int getSampleRate(){
        chunkIterator = startChunks.iterator();
        chunk tempChunk;
        while (chunkIterator.hasNext()){
              tempChunk = (chunk)chunkIterator.next();
              // Is this the fmt chunk?
              if ((tempChunk.getckID()).equalsIgnoreCase("fmt ") || (tempChunk.getckID()).equalsIgnoreCase(" fmt")){
                  // Yes we have found the fmt chunk.
                  ByteBuffer byteData = tempChunk.getBytes();
                  byteData.order(ByteOrder.LITTLE_ENDIAN);
                  byteData.position(12);
                  return byteData.getInt();
//                  break;
              }
        }
        return 0;
    }
    /**
     * This gets the byte rate which is indicated in the fmt chunk.
     * This is the average byte rate and is intended to be used for buffer estimation
     * If the file in linear PCM then it is equal to...
     * (nChannels * nSamplesPerSecond * nBitsPerSample) / 8
     * @return The byte rate indicated in the fmt chunk
     */
    public int getByteRate(){
        chunkIterator = startChunks.iterator();
        chunk tempChunk;
        while (chunkIterator.hasNext()){
              tempChunk = (chunk)chunkIterator.next();
              // Is this the fmt chunk?
              if ((tempChunk.getckID()).equalsIgnoreCase("fmt ") || (tempChunk.getckID()).equalsIgnoreCase(" fmt")){
                  // Yes we have found the fmt chunk.
                  ByteBuffer byteData = tempChunk.getBytes();
                  byteData.order(ByteOrder.LITTLE_ENDIAN);
                  byteData.position(16);
                  return byteData.getInt();
//                  break;
              }
        }
        return 0;
    }
    /**
     * Find the number of samples indicated in the file by calculation.
     * This simply reads some number from the fmt chunk and carries out a calculation.
     * @return Return the number of samples in the file.
     */
    public double getNoOfSamples() {
        // The number of samples in the file should be calculable from
        // No of channels * Sample rate * data chunk size / Byte rate
        chunkIterator = startChunks.iterator();
        chunk tempChunk;
        int intChannels, intSampleRate, intByteRate;
        while (chunkIterator.hasNext()){
            tempChunk = (chunk)chunkIterator.next();
            // Is this the fmt chunk?
            if ((tempChunk.getckID()).equalsIgnoreCase("fmt ") || (tempChunk.getckID()).equalsIgnoreCase(" fmt")){
                // Yes we have found the fmt chunk.
                ByteBuffer byteData = tempChunk.getBytes();
                byteData.order(ByteOrder.LITTLE_ENDIAN);
                byteData.position(10);
                intChannels = byteData.getShort();
                byteData.position(12);
                intSampleRate = byteData.getInt();
                byteData.position(16);
                intByteRate = byteData.getInt();
                if (intByteRate > 0) {
                    return intChannels * intSampleRate * dataChunkSize / intByteRate;
                }
                
                
            }
        }
        return 0;
    }
    /**
     * This can set the number of samples indicated in the fact chunk.
     * Wave files do not always have a fact chunk, it's mandatory for MPEG files, and sometimes exists in 32bit float files.
     * If the file did not have a fact chunk then one is created.
     * @param setNoOfSamples    Set the number of samples.
     * @return                  Return true is successful.
     */
    public boolean setFactSamples(int setNoOfSamples) {
        chunkIterator = startChunks.iterator();
        chunk tempChunk;
        while (chunkIterator.hasNext()){
            tempChunk = (chunk)chunkIterator.next();
            // Is this the fact chunk?
            if ((tempChunk.getckID()).equalsIgnoreCase("fact") ){
                ByteBuffer byteData = ByteBuffer.allocate(4);
                byteData.order(ByteOrder.LITTLE_ENDIAN);
                byteData.putInt(setNoOfSamples);
                byteData.position(0);
                if (tempChunk instanceof chunk) {
                    return tempChunk.setBytes(byteData, 0);
                } else {
                    return false;
                }
            }
        }
        // If we get here the fact chunk was not found
        ByteBuffer tempData = ByteBuffer.allocate(12);
        tempData.order(ByteOrder.LITTLE_ENDIAN);
        tempData.position(0);
        tempData.put("fact".getBytes());
        tempData.putInt(4);
        tempData.putInt(setNoOfSamples);
        tempData.position(0);
        startChunks.add(new chunk(tempData));
        return true;
    }
    /**
     * Get the indicated duration in seconds from the fmt chunk.
     * This may not always be correct.
     * @return Return the indicated duration in seconds.
     */
    public double getDuration(){
        chunkIterator = startChunks.iterator();
        chunk tempChunk;
        short shortAudioFormat;
        int intByteRate;
        while (chunkIterator.hasNext()){
            tempChunk = (chunk)chunkIterator.next();
            // Is this the fmt chunk?
            if ((tempChunk.getckID()).equalsIgnoreCase("fmt ") || (tempChunk.getckID()).equalsIgnoreCase(" fmt")){
                // Yes we have found the fmt chunk.
                ByteBuffer byteData = tempChunk.getBytes();
                byteData.order(ByteOrder.LITTLE_ENDIAN);
                byteData.position(8);
                shortAudioFormat = byteData.getShort();
                byteData.position(16);
                intByteRate = byteData.getInt();
                if (shortAudioFormat == 1 || shortAudioFormat == 3 || shortAudioFormat == 0x0055) {
                    return dataChunkSize/intByteRate;
                } else {
                    // Don't know how to calculate duration
                    return 0;
                }
            }
        }
        return 0;
    }
    /**
     * @return Return true if the file has a bext chunk.
     */
    public boolean hasBextChunk() {
        return bHasBextChunk;
    }
    /**
     * Get the title from the bext chunk.
     * @return Return a string containing the title from the bext chunk.
     */
    public String getBextTitle() {
        if (!hasBextChunk()) {
            return "";
        }
        chunkIterator = startChunks.iterator();
        chunk tempChunk;
        while (chunkIterator.hasNext()){
              tempChunk = (chunk)chunkIterator.next();
              // Is this the bext chunk?
              if ((tempChunk.getckID()).equalsIgnoreCase("bext")){
                  // Yes we have found the bext chunk.
                  ByteBuffer byteData = tempChunk.getBytes();
                  byteData.order(ByteOrder.LITTLE_ENDIAN);
                  byteData.position(8);
                  StringBuilder strTemp = new StringBuilder(256);
                  byte tempByte;
                  for (int i=0; i<256; i++) {
                       tempByte = byteData.get();
                      if (tempByte > 0) {
                          strTemp.append((char)tempByte);
                      }
                  }
                  return strTemp.toString();
            }
        }
        return "";
        
    }
    /**
     * This will set the format tag in the fmt chunk.
     * @param setFormatTag This is a short contaning the new format tag.
     * @return         Return true if the title is written successfully.
     */    
    public boolean setFormatTag(short setFormatTag) {
        chunk tempChunk = null;
        chunkIterator = startChunks.iterator();
        while (chunkIterator.hasNext()){
            tempChunk = (chunk)chunkIterator.next();
            // Is this the fmt chunk?
            if ((tempChunk.getckID()).equalsIgnoreCase("fmt ") || (tempChunk.getckID()).equalsIgnoreCase(" fmt")){
                break;
            }
        }
        ByteBuffer byteData = ByteBuffer.allocate(2);
        byteData.order(ByteOrder.LITTLE_ENDIAN);
        byteData.putShort(setFormatTag);
        byteData.position(0);
        if (tempChunk instanceof chunk) {
            return tempChunk.setBytes(byteData, 0);
        } else {
            return false;
        }
        
    }
    /**
     * This will set the title in the bext chunk.
     * If the file did not have a bext chunk then a dummy one is added and the title is written to it.
     * If the given string is too long it will be truncated.* 
     * @param setTitle This is a string containing the title, it can't be more than 256 characters.
     * @return         Return true if the title is written successfully.
     */
    public boolean setBextTitle(String setTitle) {
        chunk tempChunk = null;
        if (!hasBextChunk()) {
            tempChunk = createBextChunk();
            startChunks.add(tempChunk);
            bHasBextChunk = true;
        } else {
            chunkIterator = startChunks.iterator();
            while (chunkIterator.hasNext()){
                tempChunk = (chunk)chunkIterator.next();
                if ((tempChunk.getckID()).equalsIgnoreCase("bext")){
                    break;
                }
            }
        }
        if (setTitle.length() > 256) {
            setTitle = setTitle.substring(0, 256);
        }
        ByteBuffer byteData = ByteBuffer.allocate(256);
        byteData.put(setTitle.getBytes());
        byte nullByte = 0;
        while (byteData.hasRemaining()) {
            byteData.put(nullByte);
        }
        byteData.position(0);
        if (tempChunk instanceof chunk) {
            return tempChunk.setBytes(byteData, 0);
        } else {
            return false;
        }
    }
    /**
     * Get the originator reference.
     * @return Return a 32 character string containing the originator reference.
     */
    public String getBextOriginatorRef() {
        if (!hasBextChunk()) {
            return strOriginatorReference;
        }
        chunkIterator = startChunks.iterator();
        chunk tempChunk;
        while (chunkIterator.hasNext()){
              tempChunk = (chunk)chunkIterator.next();
              // Is this the bext chunk?
              if ((tempChunk.getckID()).equalsIgnoreCase("bext")){
                  // Yes we have found the bext chunk.
                  ByteBuffer byteData = tempChunk.getBytes();
                  byteData.order(ByteOrder.LITTLE_ENDIAN);
                  byteData.position(296);
                  StringBuilder strTemp = new StringBuilder(32);
                  byte tempByte;
                  for (int i=0; i<32; i++) {
                       tempByte = byteData.get();
                      if (tempByte > 0) {
                          strTemp.append((char)tempByte);
                      }
                  }
                  strOriginatorReference = strTemp.toString();
                  return strTemp.toString();
            }
        }
        return "";
        
    }
    /**
     * This will set the originator reference in the bext chunk.
     * If the file did not have a bext chunk then a dummy one is added and the originator reference is written to it.
     * @param setOriginatorReference    This is a 32 character string containing an originator reference.
     * If the given string is too long it will be truncated.
     * @return Returns true if the reference is written.
     */
    public boolean setBextOriginatorRef(String setOriginatorReference) {
        chunk tempChunk = null;
        if (!hasBextChunk()) {
            tempChunk = createBextChunk();
            startChunks.add(tempChunk);
            bHasBextChunk = true;
        } else {
            chunkIterator = startChunks.iterator();
            while (chunkIterator.hasNext()){
                tempChunk = (chunk)chunkIterator.next();
                if ((tempChunk.getckID()).equalsIgnoreCase("bext")){
                    break;
                }
            }
        }
        if (setOriginatorReference.length() > 32) {
            setOriginatorReference = setOriginatorReference.substring(0, 32);
        }
        ByteBuffer byteData = ByteBuffer.allocate(32);
        byteData.put(setOriginatorReference.getBytes());
        byte nullByte = 0;
        while (byteData.hasRemaining()) {
            byteData.put(nullByte);
        }
        byteData.position(0);
        strOriginatorReference = setOriginatorReference;
        if (tempChunk instanceof chunk) {
            return tempChunk.setBytes(byteData, 288);
        } else {
            return false;
        }
    }
    /**
     * This finds the bext chunk in the file and returns the time code offset for the start of the recording.
     * @return Return the timecode offset in samples.
     */
    public long getBextTimeCodeOffset() {
        if (!hasBextChunk()) {
            return 0;
        }
        chunkIterator = startChunks.iterator();
        chunk tempChunk;
        while (chunkIterator.hasNext()){
              tempChunk = (chunk)chunkIterator.next();
              // Is this the bext chunk?
              if ((tempChunk.getckID()).equalsIgnoreCase("bext")){
                  // Yes we have found the bext chunk.
                  ByteBuffer byteData = tempChunk.getBytes();
                  byteData.order(ByteOrder.LITTLE_ENDIAN);
                  byteData.position(346);
                  return byteData.getLong();
            }
        }
        return 0;

    }
    /**
     * Create an empty bext chunk with dummy values.
     * @return Return a bext chunk.
     */
    public chunk createBextChunk() {
        chunk tempChunk = null;
        ByteBuffer byteData = ByteBuffer.allocate(1032);
        byteData.order(ByteOrder.LITTLE_ENDIAN);
        byteData.put("bext".getBytes());
        byteData.putInt(1024);
        byteData.position(328);
        byteData.put("1970-01-0100-00-00".getBytes());
        byteData.putLong(0);
        byteData.putInt(0);
        byteData.position(0);
        tempChunk = new chunk(byteData);
        return tempChunk;
    }
}
