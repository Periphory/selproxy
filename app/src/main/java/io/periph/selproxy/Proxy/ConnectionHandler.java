package io.periph.selproxy.Proxy;

import android.util.Log;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.Map.Entry;

import io.periph.selproxy.util.ConnectionFailureException;
import io.periph.selproxy.util.HttpUtil;
import io.periph.selproxy.util.ParseException;

//import org.apache.http.protocol.HTTP;

/**
 * Created by Periph on 18/08/2015.
 *
 * Handles an incoming request to the proxy.
 *
 * Need to deal with - Constant connections - > by default the connection should be persistent
 *                                              and only closed on the receipt of Connection: close
 *                                              in the response HTTP header.
 *
 */

public class ConnectionHandler implements Runnable {

    public static int socketTimeout = 200;

    private Socket tClientSocket = null; //From the client
    private Socket tServerSocket = null; //To the server

    private boolean isKeepAlive = true;
    private boolean isSslClosed = false;

    //Streams from the Client
    private InputStream fromClientStream = null;
    private OutputStream toClientStream = null;

    //Streams from the Server.
    private InputStream fromServerStream = null;
    private OutputStream toServerStream = null;



    public ConnectionHandler(Socket toClient) {
        //Take in a reference to the socket from the client.
        this.tClientSocket = toClient;
        this.tServerSocket = null;

        try {
            fromClientStream = tClientSocket.getInputStream();
            toClientStream = tClientSocket.getOutputStream();

        } catch(SocketException se) {
            se.printStackTrace();
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    /**
     * Reads in a request from the client side processes the HTTP header and forwards it.
     * @throws ConnectionFailureException Thrown if there is a problem initialising the connection.
     */
    public void serviceClientRequest() throws ParseException, IOException, ConnectionFailureException {
        boolean result;
        RequestMessage m = HttpUtil.parseRequest(fromClientStream);
        if (m == null) {
            //Can be null because of:
            //HTTP request line parse fail
            //URI syntax exception
            //in either case, we cannot proceed.
            throw new ParseException(HttpUtil.err);
        }
        //If we detect TLS/SSL branch here.
        if(m.getMethod().equalsIgnoreCase("connect")) {
            //setup connection to port 443.
            if(Integer.valueOf(m.getRequestPort()) != 443) {
                throw new ParseException("Request for HTTPS over port other than 443.");
            }
            connect(m.getHost(), m.getRequestPort());
            SslConnectionHandler newSSLCon = new SslConnectionHandler(fromClientStream,
                    fromServerStream, toClientStream,toServerStream, this);
            newSSLCon.start();
            //TLS has finished - close connection.
            isSslClosed = true;
            return;
        } else {
            //Sets up server streams in connect().
            connect(m.getHost(), m.getRequestPort()); //Connection failure exception.
        }
        //Write to server.
        writeMessage(toServerStream, fromClientStream, m);
    }

    /**
     * Reads the response from the server, parses the HTTP response line and headers and writes
     * the data back to the client. Assumes so far that there is no data in the response.
     * @throws IOException - Thrown from writeMessage,
     * @throws ParseException - Thrown if error in parsing HTTP response line.
     */
    public void serviceServerResponse() throws IOException, ParseException {
        //Read the response from the server and forward it to the client.
        //Check the field header for a connection close.
        //Write the data to the client socket.
        ResponseMessage m = HttpUtil.parseResponse(fromServerStream);
        if (m == null) {
            //Returns null if there is an error parsing the HTTP server response line.
            throw new ParseException(HttpUtil.err);
        }

        if(m.headerFields.get("Connection") != null) {
            //We have a connection header....
            if(m.headerFields.get("Connection").equalsIgnoreCase("close")){
                //Server sent a Connection: close therefore we close the connection after.
                Log.v(ProxyMain.TAG, "Connection close detected.");
                isKeepAlive = false;
            }
        }
        //Write the response back to the Client.
        //IOException thrown to caller.
        writeMessage(toClientStream, fromServerStream, m);
    }


    /**
     * Main looping function that alternates between listening for client requests and forwarding
     * server responses.
     */
    public void run() {
        try {
            while (isKeepAlive) {
                serviceClientRequest();
                if(isSslClosed) {
                    //SSL connection closed so break and close connection.
                    break;
                }
                serviceServerResponse();
            }

        }  catch (ParseException pe) {
            Log.e(ProxyMain.TAG, "EXCEPTION Parse error: " + pe.getMessage());
        } catch (ConnectionFailureException cfe) {
            //TODO - Error in app due to lack of internet connection.
            Log.e(ProxyMain.TAG, "EXCEPTION Connection failure: " + cfe.getMessage());
        } catch (IOException ioe) {
            ioe.printStackTrace();
            Log.e(ProxyMain.TAG, "EXCEPTION I/O failure: " + ioe.getMessage());
        } finally {
            cleanUpConnection();
        }
    }

    /**
     * Setup a connection to the host using the given hostname.
     * @param host The host to connect to
     * @return Whether the connection has been successful.
     */
    public void connect(String host, int port) throws ConnectionFailureException{
        if (tServerSocket == null) {
            //Need to establish socket to server.
            InetAddress hostIP;
            String err;
            try {
                hostIP = InetAddress.getByName(host);
                tServerSocket = new Socket(hostIP, port);
                //tServerSocket.setSoTimeout(socketTimeout);
                Log.v(ProxyMain.TAG, "Established connection to " + host + ":" + port);
            } catch (UnknownHostException e) {
                //Error when there is no internet connection / Internet permission.
                throw new ConnectionFailureException("Error trying to resolve host: " + host);
            } catch (IOException ioe) {
                ioe.printStackTrace();
                throw new ConnectionFailureException("Error trying to establish connection to: " + host + "[ " +
                ioe.getMessage() + " ].");
            }
        }

        //Setup streams to the server anyway.
        try {
            fromServerStream = tServerSocket.getInputStream(); //read
            toServerStream = tServerSocket.getOutputStream();  //write
        } catch (IOException io) {
            io.printStackTrace();
            throw new ConnectionFailureException("Error getting streams from the server socket! : " + io.getMessage());
        }
    }

    /**
     * Given a socket and inputstream that contains data and a Message, write the data to the socket.
     * This method re-creates the HTTP header using information from the Message and simply re-directs
     * input from a stream to the socket.
     *
     * @param o The outputstream in which to write the message to.
     * @param i The stream that is the source of the HTTP body data (if any)
     * @param m The message that contains the HTTP header information.
     * @return Whether the write of the message was successful.
     */
    public void writeMessage(OutputStream o, InputStream i, Message m) throws IOException {
            //Every field in HTTP is terminated by a CRLF
            //Header is delimited by a single CRLF.
            //Data is then written.
            //Serveclientrequest - o toServerStream, i fromClientStream
            //ServeServerRequest - o toClientSream, i fromServerStream
            DataInputStream ds = new DataInputStream(i);
            //For header.
            Writer wo = new BufferedWriter(new OutputStreamWriter(o, HttpUtil.httpHeaderEncoding));
            DataOutputStream dos = new DataOutputStream(o);
            //Write and flush the request/response line.
            if (m instanceof RequestMessage) {
                //<method> <URI> <HTTPversion>
                //We only want the path here not the full URL.
                String resqLine = ((RequestMessage) m).getMethod()  + " " + ((RequestMessage) m).getExtractedURI().getPath() +
                        " " + ((RequestMessage) m).getHttpVersion() + "\r\n";
                wo.write(resqLine.toCharArray());
            } else if (m instanceof ResponseMessage) {
                //<HTTPv> <resp code> <resp mesg>
                String respLine = ((ResponseMessage) m).getHttpVersion() + " " + ((ResponseMessage) m).getReturnCode() +
                        " " + ((ResponseMessage) m).getReturnMessage() + "\r\n";
                wo.write(respLine.toCharArray());
            }

            Iterator it = m.headerFields.entrySet().iterator();
            //Wrap input stream with data reader.

            while(it.hasNext()) {
                // Write of the form <key>:SP<value>
                Entry<String, String> e = (Entry<String, String>)it.next();
                String s = new String(e.getKey() + ": " + e.getValue() + "\r\n");
                wo.write(s.toCharArray());
            }

            //Write \CR\LF to delimit the HTTP header.
            wo.write("\r\n".toCharArray());

            //Flush encoded HTTP header to stream.
            wo.flush();

            int totalSeenBytes = 0;
            //Write the bytes to the output stream.
            if (m instanceof ResponseMessage) {
                //Only if the content-length > 0 do we attempt to read from socket.
                if (m.headerFields.get("Content-Length") != null && Integer.valueOf(m.headerFields.get("Content-Length")) != 0) {
                    Log.d(ProxyMain.TAG, "Expecting " + m.headerFields.get("Content-Length") + " bytes from content-length header.");
                    byte[] buf = new byte[4096]; //Create buffer.
                    int read = 0;
                    int contentLength = Integer.valueOf(m.headerFields.get("Content-Length"));
                    byte b;
                    while (totalSeenBytes < contentLength) {

                        if ((read = ds.read(buf)) == -1) {
                            //EOF early.
                        }
                        totalSeenBytes += read;
                        dos.write(buf, 0, read);
                        dos.flush();
                    }
                }
                //Don't do anything with the socket?
            } else {
                //We are requesting something so we don't normally have any request body.
                //according to RFC, checking for content-length or transfer-encoding header.
                //Or where the request method does not allow sending an entity body in requests.
            }
    }

    public void cleanUpConnection() {
        try {
            //close streams
            if(fromClientStream != null) fromClientStream.close();
            if(toServerStream != null) toServerStream.close();
            if (toClientStream != null) toClientStream.close();
            if (fromServerStream != null) fromServerStream.close();
        } catch (IOException e) {
            Log.e(ProxyMain.TAG, "Error closing the socket streams on cleanUpConnection()");
            e.printStackTrace();
        }
        try {
            //close sockets
            if(tServerSocket != null) tServerSocket.close();
            if (tClientSocket != null) tClientSocket.close();
        } catch (IOException ioe) {
            Log.e(ProxyMain.TAG, "Error closing the sockets on cleanUpConnection()");
            ioe.printStackTrace();
        }
    }
}
