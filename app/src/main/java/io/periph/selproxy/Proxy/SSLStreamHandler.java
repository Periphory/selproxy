package io.periph.selproxy.Proxy;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by Periph on 10/09/2015.
 *
 * Created in order to handle a particular direction of communication regardless
 * of whether its SSL or not.
 *
 * Once we get EOF - Close both streams.
 */
public class SSLStreamHandler implements Runnable {

    /**
     * @param in Inputstream from one socket.
     * @param out OutputStream from the other socket.
     */

    private OutputStream o = null;
    private InputStream i = null;

    private volatile boolean isClosed = false;
    private int currentMessagePtr = 0;
    private byte[] currentMessage = null;

    private TlsRecord currentRecord = new TlsRecord();

    private ConnectionHandler conHan = null;
    private boolean isUpStream;

    public SSLStreamHandler(InputStream in, OutputStream out, ConnectionHandler ch, boolean isUpStream) {
        this.o = out;
        this.i = in;
        this.conHan = ch;
        this.isUpStream = isUpStream;
    }

    public void run() {
        //Just read and write the records.
        //TODO - Connection can be reset by peer here.
        while(currentRecord.read(this.i) != -1) {
            //currentRecord.printRecordInfo(isUpStream, conHan);
            currentRecord.write(o);
        }
    }
    /**
     * Sets a flag to show whether the reader detected an EOF.
     * @return
     */
    public void close() {
        this.isClosed = true;
    }

    /**
     *
     * TODO - Needs to be tested.
     * Extracts the next Tls Message from a set of or a single Tls record.
     * Assumes that invocations of read here do not result in EOF's.
     */
    private void parseTlsMessage(){
        //Initialise the message buffer and pointers.
        this.currentMessagePtr = 0;
        this.currentMessage = new byte[currentRecord.getNextMessageLength() + TlsRecord.HANDSHAKE_MESSAGE_HDR_SIZE];
        while (true) {
            switch (currentRecord.copyNextMessage(this.currentMessage, this.currentMessagePtr)) {
                //Check if we need to keep reading in Records to reassemble fragmented messages.
                case RECORD_FINISHED:
                    currentRecord.write(o);
                    //TODO - Does continuing data have an initial header?? - assumed it doesn't
                    break;
                case MSG_FINISHED:
                    //Read in the next message but DO NOT read in the next TLSRecord.
                    processMessage();
                    currentMessage = new byte[currentRecord.getNextMessageLength() + TlsRecord.HANDSHAKE_MESSAGE_HDR_SIZE];
                    currentMessagePtr = 0;
                    break;
                case MSG_AND_RECORD_FINISHED:
                    currentRecord.write(o);
                    processMessage();
                    //Read in the next TLSRecord before attempting to read the next Message.
                    currentMessage = new byte[currentRecord.getNextMessageLength() + TlsRecord.HANDSHAKE_MESSAGE_HDR_SIZE];
                    currentMessagePtr = 0;
                    //Stop - if both message and record end then we assume the message to be sent has completed.
                    return;
            }
        }
    }

    private void processMessage(){
        //Do something with the message.
    }
}
