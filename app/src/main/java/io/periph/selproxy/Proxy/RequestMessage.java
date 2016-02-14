package io.periph.selproxy.Proxy;

import java.net.URI;

/**
 * Created by Periph on 19/08/2015.
 */
public class RequestMessage extends Message {

    private String host = null; //If we have a null host then one hasn't been set yet.
    private String method;
    private String httpVersion;

    public int getRequestPort() {
        return requestPort;
    }

    public void setRequestPort(int requestPort) {
        this.requestPort = requestPort;
    }

    private int requestPort;
    private URI extractedURI;
    private String path;

    public URI getExtractedURI() {
        return extractedURI;
    }

    public void setExtractedURI(URI extractedURI) {
        this.extractedURI = extractedURI;
    }


    public String getHttpVersion() {
        return httpVersion;
    }

    public void setHttpVersion(String httpVersion) {
        this.httpVersion = httpVersion;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public RequestMessage(){
        super();
    }
    public void setHost(String host) {
        this.host = host;
    }

    public String getHost() {
        return this.host;
    }
}
