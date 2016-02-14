package io.periph.selproxy.Proxy;

import java.io.InputStream;
import java.util.LinkedHashMap;

/**
 * Created by Periph on 18/08/2015.
 *
 * Represents a HTTP message.
 *
 * Header fields are terminated by a CR/LF
 * Header terminated by a blank line containing only a CR/LF.
 *
 * Potential problems:
 *  - Reading in a huge request/response into memory may be problematic.
 *  - Do I need to record the Host we're
 *
 *
 *  Important Fields:
 *
 */
public class Message {
    public static final String RequestLineKey = "RequestLine";
    public LinkedHashMap<String, String> headerFields;
    public Message() {
        headerFields = new LinkedHashMap<String,String>();
    }
}
