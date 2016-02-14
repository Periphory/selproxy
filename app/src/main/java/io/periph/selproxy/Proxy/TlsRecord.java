package io.periph.selproxy.Proxy;

import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.nio.ByteBuffer;

/**
 * Created by Periph on 31/08/2015.
 *
 * Represents a single TLSRecord.
 *
 * TLS Record Protocol
 *
 * struct {
 ContentType type;
 ProtocolVersion version;
 uint16 length;
 opaque fragment[TLSPlaintext.length];
 } TLSPlaintext;
 *
 *
 *
 */
public class TlsRecord {

    public enum CopyStatus {
        MSG_FINISHED, RECORD_FINISHED, MSG_AND_RECORD_FINISHED
    }

    byte[] recordHeader = new byte[5];  //TLSRecord header.
    ByteBuffer tlsRecordPayload = null; //TLSRecord payload
    public int bytesIntoPayload = 0;    //TLSRecord payload pointer

    private byte contentType;           //Type of TLS Record.
    private int length;                 //Length of TLS Record payload
    private short version;              //Version of TLS/SSL record.


    //TLSRecord Content-Types
    public static final byte ChangeCipherSpec = 0x14;
    public static final byte Alert = 0x15;
    public static final byte Handshake = 0x16;
    public static final byte Application = 0x17;
    public static final byte Heartbeat = 0x18;

    //Handshake message types
    public static final byte HelloRequest = 0x0;
    public static final byte ClientHello = 0x1;
    public static final byte ServerHello = 0x2;
    public static final byte Certificate = 0xb;
    public static final byte ServerKeyExchange = 0xc;
    public static final byte CertificateRequest = 0xd;
    public static final byte ServerHelloDone = 0xe;
    public static final byte CertificateVerify = 0xf;
    public static final byte ClientKeyExchange = 0x10;
    public static final byte Finished = 0x14;

    //Version types (Minor)
    public static final byte Ssl3 = 0;
    public static final byte Tls10 = 0x1;
    public static final byte Tls11 = 0x2;
    public static final byte Tls12 = 0x3;


    //Protocol Message constants
    public static final int HANDSHAKE_RECORD_HDR_SIZE = 4;
    public static final int HANDSHAKE_MESSAGE_HDR_SIZE = 4;

    public TlsRecord() {

    }
    /**
     *
     * TODO - Not tested - Does this replace the original read method?
     * Copy a message from the current TLSRecord into a buffer. It also takes into account
     * messages that are fragmented across multiple TLSRecords.
     *
     * @param messageBuf The buffer to copy it in to
     * @param msgPtr The offset into the buffer with which to start writing data from.
     */
    public CopyStatus copyNextMessage(byte[] messageBuf, int msgPtr) {
        //case 1 - msgPtr == buf.length -> Message is finished but Record has other messages.
        //case 2 - bytesInto == this.length -> Reached end of TLSRecord but not message.
        //case 3 - both conditions are false -> Reached the end of message AND TLSRecord.
        // msgbufferPtr < messageBuf.length && offset into payload < payload length.
        while (msgPtr < messageBuf.length && bytesIntoPayload < this.length) {
            messageBuf[msgPtr] = tlsRecordPayload.get(bytesIntoPayload);
            bytesIntoPayload++;
            msgPtr++;
        }
        //Return the status of the copying.
        if(msgPtr >= messageBuf.length && bytesIntoPayload >= this.length) {
            return CopyStatus.MSG_AND_RECORD_FINISHED;
        } else if ( msgPtr >= messageBuf.length) {
            return CopyStatus.MSG_FINISHED;
        } else {
            return CopyStatus.RECORD_FINISHED;
        }
    }

    /**
     *
     * TODO - Tested - works.
     * Read in a given TLSRecord from the input stream i. The header is stored in recordHeader
     * and the TLS Record payload is stored in tlsRecordPayload.
     *
     * @param i - The input
     * @return - Whether or not the read was successful. -1 indicates end of stream, -2 for reading less than 5 bytes of the header.
     */
    public int read(InputStream i) {
        int count = 0;
        int readBytes = 0;
        try {
            //Sometimes even before reading anything the other end closes the connection..
            readBytes = i.read(recordHeader);
            if (readBytes == -1) {
                //Connection closed.
                Log.v(ProxyMain.TAG, "EOF - Closed.");
                return -1;
            } else if (readBytes != recordHeader.length) {
                Log.e(ProxyMain.TAG, "Didn't read 5-byte TLS Record header, only got " + readBytes);
                return -2;
            }
            this.contentType = recordHeader[0];
            //Assign length of TLSRecord
            this.length = ((recordHeader[3] << 8) & 0x0000ff00) | (recordHeader[4] & 0x000000ff);
            //Only want minor version of the SSL/TLS.
            this.version = recordHeader[2];

            //Read in the rest of the TLS record into the ByteBuffer.
            tlsRecordPayload = ByteBuffer.wrap(new byte[this.length]);
            //Reset the counter into the payload
            bytesIntoPayload = 0;

            while (count < this.length) {
                //Pull all of the TLS record payload into the buffer.
                tlsRecordPayload.put((byte) i.read());
                count++;
            }
        }
        catch (SocketException se) {
            Log.e(ProxyMain.TAG, "Unexpected socket error when trying to read: " + se.getMessage());
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }

        return 1;
    }

    /**
     *
     * Write a given TLSRecord to the outputstream o. Read must have been invoked on this
     * object before attempting to write it to an output stream.
     * @param o
     */
    public boolean write(OutputStream o) {

        BufferedOutputStream bo = new BufferedOutputStream(o);

        if(tlsRecordPayload == null) {
            //error.
            return false;
        }
        //Write the header.
        try {
            bo.write(recordHeader);
            bo.write(tlsRecordPayload.array());
            bo.flush();

        } catch (SocketException se) {
            Log.e(ProxyMain.TAG, "Unexpected socket error when trying to write: " + se.getMessage());
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
            return false;
        }
        return true;
    }

    public int getRecordPayloadLength() {
        return this.length;
    }

    /**
     * By taking the current position inside the payload as the starting position of a message,
     * calculate the size of the next message. It does not move the pointer into the payload.
     * @return
     */
    public int getNextMessageLength() {
        //Assuming the current position held in bytesIntoPayload is the beginning of the msg,
        //calculate the size of the message.
        //We only care about the first 8bits.
        int msb = ((int)tlsRecordPayload.get(bytesIntoPayload+1)) & 0xf;
        int nsb = ((int)tlsRecordPayload.get(bytesIntoPayload+2)) & 0xf;
        int lsb = ((int)tlsRecordPayload.get(bytesIntoPayload+3)) & 0xf;
        return (msb << 16) | (nsb << 8) | lsb;
    }

    /**
     * By taking the current position inside the payload as the starting position of a TLS message, return
     * the type of the message
     * @return The 8-bit value corresponding to the type of Message inside the TLSRecord.
     */
    public int getNextMessageType() {
        return ((int)tlsRecordPayload.get(bytesIntoPayload)) & 0xF;
    }


    public byte getContentType() {
        return contentType;
    }

    public short getVersion() {
        return version;
    }

    public int getLength() {
        return length;
    }

    /**
     * TODO - Tested works.
     * Needs access to the ConnectionHandler in order to get the ThreadID.
     * @param isUpstream Boolean flag true if its Client to Server stream false if Server to Client stream
     * @param ch Handler object associated with stream
     */
    public void printRecordInfo(boolean isUpstream, ConnectionHandler ch) {
        String info = "TLS 1.";
        switch(this.version) {
            case Tls10:
                info += "0: ";
                break;
            case Tls11:
                info += "1: ";
                break;
            case Tls12:
                info += "2: ";
                break;
            default:
                info += "u: ";
        }

        //Direction of information
        if (isUpstream) {
            //client -> server
            info += " (C->S) ";
        } else {
            info += " (S->C) ";
        }

        switch(this.contentType) {
            case TlsRecord.Handshake:
                //Log.i(ProxyMain.TAG, "TLSRecord -> Handshake");
                info += "Handshake";
                break;
            case TlsRecord.Alert:
                //Log.i(ProxyMain.TAG, "TLSRecord -> Alert");
                info += "Alert";
                break;
            case TlsRecord.Application:
                //Log.i(ProxyMain.TAG, "TLSRecord -> Application");
                info += "Application";
                break;
            case TlsRecord.Heartbeat:
                //Log.i(ProxyMain.TAG, "TLSRecord -> Heartbeat");
                info += "Heartbeat";
                break;
            case TlsRecord.ChangeCipherSpec:
                //Log.i(ProxyMain.TAG, "TLSRecord -> ChangeCipherSpec");
                info += "ChangeCipherSpec";
                break;
            default:
                //Log.i(ProxyMain.TAG, "TLSRecord -> Unknown");
                info += "Unknown";
                break;
        }
        info += " record size: " + this.length;
        Log.d(ProxyMain.TAG, info);

        //Parse messages - get type and number

        //Given some length of the record get the messages.
        int msgSize = 0;
        int msgType = 0;
        int ptr = 0;
        int t = 0;
        byte[] tmp = new byte[TlsRecord.HANDSHAKE_MESSAGE_HDR_SIZE];
        String s = "";

        if(this.contentType == TlsRecord.Handshake) {
            while (true) {
                //get the messages (Specific to Handshake messages)
                msgType = tlsRecordPayload.get(ptr++) & 0x000000ff;
                msgSize = ( ( tlsRecordPayload.get(ptr) << 16 ) & 0x00ff0000) | ((tlsRecordPayload.get(ptr+1) << 8) & 0x0000ff00) | (tlsRecordPayload.get(ptr+2) & 0x000000ff);
                //Move pointer over size bytes (3-bytes)
                ptr += 3;
                //Start of message.
                //Print details
                s += "M: --> ";
                switch(msgType) {
                    case HelloRequest:
                        s += "HELLOREQ";
                        break;
                    case ClientHello:
                        s += "CLIENTHELLO";
                        break;
                    case ServerHello:
                        s += "SERVERHELLO";
                        break;
                    case CertificateRequest:
                        s += "CERTIFICATEREQ";
                        break;
                    case Certificate:
                        s += "CERTIFICATE";
                        break;
                    case ServerHelloDone:
                        s += "SERVERHELLODONE";
                        break;
                    case Finished:
                        s += "FINISHED";
                        break;
                    default:
                        s += String.valueOf(msgType);
                }
                s += " " + msgSize + " ";
                Log.d(ProxyMain.TAG, s);
                //Move pointer to the next message (the loop condition will check whether it goes over the TLS Record size.
                t = ptr;
                //If the current message moves the ptr off the end of the array then its fragmented.
                if ( (t + msgSize) > this.length) {
                    //Is fragmented
                    Log.d(ProxyMain.TAG, "FRAGGED");
                    break;
                } else if ((t + msgSize) == this.length) {
                    //Ends exactly at the end of the record.
                    Log.d(ProxyMain.TAG, "EXACT");
                    break;
                } else {
                    //More messages to read.
                    ptr += msgSize;
                }
            }
        }

    }
}
