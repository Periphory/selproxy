package io.periph.selproxy.util;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

import io.periph.selproxy.Proxy.Message;
import io.periph.selproxy.Proxy.ProxyMain;
import io.periph.selproxy.Proxy.RequestMessage;
import io.periph.selproxy.Proxy.ResponseMessage;

/**
 * Created by Periph on 19/08/2015.
 *
 * Utility class that contains methods helpful for parsing HTTP headers.
 *
 * HTTP Request/Response
 * Request Line
 * Headers
 * Empty line (only contains CR/LF)
 * Data
 *
 */
public class HttpUtil {

    public static final String httpHeaderEncoding = "US-ASCII";
    private static final int defaultRequestPort = 80; //By default we assume port 80 is being contacted.
    public static String err = "";

    public class ParseException extends Exception {
        public ParseException(String mes) {
            super(mes);
        }
    }

    /**
     * Given a stream, parse the HTTP headers and return this information in a message obj.
     * @param inputStream The input stream to read data from.
     * @return The message object containing the field values in an easy to access format.
     */
    public static RequestMessage parseRequest(InputStream inputStream) {
        //Extract host from Request line (Use URL) after parsing.
        //Extract port from Request line
        Message newMessage = new RequestMessage();
        int i;
        try {
            //Spin waiting for a request.
            String reqLine;
            reqLine = readLineFromBytes(inputStream, Charset.forName(httpHeaderEncoding)); //This method blocks until data is available.
            if (reqLine == null) {
                err = "Unexpectedly reached end of stream while extracting request line.";
                return null;
            }
            String[] req = reqLine.split("\\s"); //Split on any white space
            if(req.length != 3) {
                //Format: <method> <URL> <HttpVersion>
                err = "Error parsing HTTP Request line, expected 3 have " + req.length;
                return null;
            }

            /**
             * Connect requests will always have the form
             * CONNECT host:port HTTP/1.1
             * https://www.ietf.org/rfc/rfc2817.txt
             *
             * https://tools.ietf.org/html/rfc2616#section-5.1
             * states that the URI must be absolute when sent
             * to a proxy via HTTP/1.1
             */

            //Set method - assume GET.
            ((RequestMessage)newMessage).setMethod(req[0].trim());

            //TODO - Check that  HTTPS connections are to port 443.
            if(((RequestMessage) newMessage).getMethod().equalsIgnoreCase("connect")) {
                ((RequestMessage)newMessage).setExtractedURI(new URI("https://" + req[1].trim()));
            } else {
                ((RequestMessage)newMessage).setExtractedURI(new URI(req[1].trim()));
            }

            //TODO - Should we only allow HTTP 1.1?
            ((RequestMessage)newMessage).setHttpVersion(req[2].trim());

            //Extract host from the URI
            Log.d(ProxyMain.TAG, "Host from URI: " + ((RequestMessage) newMessage).getExtractedURI().getHost());
            ((RequestMessage) newMessage).setHost(((RequestMessage) newMessage).getExtractedURI().getHost());

            //Check if there is a port.
            int port = ((RequestMessage) newMessage).getExtractedURI().getPort();
            if(port > 0) {
                    ((RequestMessage) newMessage).setRequestPort(port);
            } else {
                ((RequestMessage) newMessage).setRequestPort(defaultRequestPort);
            }

            //Finished parsing request line
            //Parse the fields in the HTTP header of the request now.
        } catch(URISyntaxException use) {
            //URI exception
            err = use.getMessage();
            use.printStackTrace();
            return null;
        }
        //Any exceptions we should not pass this point but return before.
        //Request message is passed here.
        return (RequestMessage)parseHttpFields(inputStream,newMessage);
    }

    /**
     * The same as {@link #parseRequest(InputStream)} except dealing with responses.
     * @param inputStream The input stream to read from.
     * @return A Message containing the HTTP header fields OR null on exception.
     */
    public static ResponseMessage parseResponse(InputStream inputStream) {
        //Extract HTTP version
        //Extract response code
        //Extract response message
        Message newMessage = new ResponseMessage();
        String s = readLineFromBytes(inputStream, Charset.forName(httpHeaderEncoding));
        if (s == null) {
            err = "Unexpectedly reached the end of the stream";
            return null;
        }
        String[] respLine = s.split("\\s", 3);
        //Format <httpversion> <respcode> <respmessage>
        if (respLine.length != 3) {
            err = "Error parsing Response line, expected 3 tokens got" + respLine.length;
            return null;
        }
        //Set the information
        ((ResponseMessage)newMessage).setHttpVersion(respLine[0].trim());
        ((ResponseMessage) newMessage).setReturnCode(respLine[1].trim());
        ((ResponseMessage) newMessage).setReturnMessage(respLine[2].trim());
        return (ResponseMessage)parseHttpFields(inputStream, newMessage);
    }

    /**
     * Extracts the HTTP header fields given the stream. The message is then populated with these
     * fields and values.
     *
     * Inconsistent with readLineFromBytes because this relies on explicit casting to char (UTF-8)
     *
     * @param br The reader that wraps the stream to read from
     * @param m The message obj to which the fields and values will be added.
     */
    private static Message parseHttpFields(InputStream br, Message m){
        //At this point the next line should be the start of the headers.
        int c;
        boolean mayEnd = false;
        StringBuilder sb = new StringBuilder();
        //make sure it breaks otherwise it will read the whole stream.
        try {
            while (true) {
                if ( (c = br.read()) == -1 ){
                    err = "Unexpectedly reached the end of the stream while parsing HTTP header";
                    return null;
                }
                if (c == '\r') {
                    c = br.read();
                    if (c == '\n' && mayEnd) {
                        //Terminate HTTP header
                        break;
                    } else if (c == '\n') {
                        //Line terminated - set may end flag
                        //Add the field to the Message
                        addHttpField(sb, m);
                        sb = new StringBuilder();
                        mayEnd = true;
                    } else if ( c == -1 ) {
                        //Reached unexpected end of steam
                        err = "Unexpectedly reached end of stream while looking for \\n " +
                                "to delimit the end of the HTTP header.";
                        return null;
                    } else {
                        //Accepts the following sequence without error and strips them out.
                        //\r\n\rb
                        sb.append((char)c);
                        mayEnd = false;
                    }
                } else {
                    sb.append((char)c);
                    mayEnd = false;
                }
            }
            //Add the last field to the Message.
            //No need to create a new StringBuilder because we're finished now.
            //addHttpField(sb, m);
        } catch (IOException io) {
            //Error reading from the bufferedreader.
            err = io.getMessage();
            io.printStackTrace();
            return null;
        }
        return m;
    }

    /**
     * Given a string in the form field:value, add it it to the Message
     * @param sb - The parsed field.
     * @param m - The message to add it to.
     */
    private static void addHttpField(StringBuilder sb, Message m) {
        String[] tokens = sb.toString().split(":", 2);
        if( tokens.length != 2 ) {
            Log.d(ProxyMain.TAG, "Error in addHttpField() - expected 2 tokens seen " + tokens.length);
        } else {
            if (m instanceof RequestMessage) {
                if (tokens[0].trim().equalsIgnoreCase("host")) {
                    Log.d(ProxyMain.TAG, "Found host field " + tokens[1].trim());
                    //We may have already set host from the URI, so just ignore it here.
                    if (((RequestMessage) m).getHost() == null) {
                        //We still need to add the Host header because it is required.
                        ((RequestMessage) m).setHost(tokens[1].trim());
                    }
                }
                else if(tokens[0].trim().equalsIgnoreCase("proxy-connection")) {
                    //ignore any Proxy-Connection headers fields.
                    return;
                }
            }
            //Add the field
            m.headerFields.put(tokens[0].trim(), tokens[1].trim());
        }
    }

    /**
     * Given a byte stream and a charset, this method returns a string of text.
     * The given delimiter is a carriage-return.
     * Even though UTF-8 by default is 2-byte, the first 1-byte set of characters is the same
     * as US-ASCII.
     *
     * @param i The input stream containing bytes.
     * @return The first line of character text.
     */
    public static String readLineFromBytes(InputStream i, Charset c) {

        byte[] b = new byte[1]; // Only read 1 byte at a time.
        CharBuffer cb;
        StringBuffer sb = new StringBuffer();
        char theChar;
        boolean seenCarriage = false;

        try {
            while (true) {
                //Read one byte at a time and decode it.
                //Wrap it in byte-buffer.
                if( i.read(b) == -1) return null; //If it reaches the end of the stream is returns null/
                cb = c.decode(ByteBuffer.wrap(b));
                //Decode it and add it to the String buffer.
                theChar = cb.get();
                if (theChar == '\n' && seenCarriage) {
                    //return we have a line.
                    break;
                } else if ( theChar == '\n') {
                    //ignore
                } else if ( theChar == '\r') {
                    seenCarriage = true;
                } else {
                    sb.append(theChar);
                }
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return sb.toString();
    }
}
