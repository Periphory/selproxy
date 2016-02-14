package io.periph.selproxy.Proxy;

/**
 * Created by Periph on 21/08/2015.
 */
public class ResponseMessage extends Message {

    private String httpVersion;
    private String returnCode;
    private String returnMessage;

    public ResponseMessage(){
        super();
    }

    public String getReturnMessage() {
        return returnMessage;
    }

    public void setReturnMessage(String returnMessage) {
        this.returnMessage = returnMessage;
    }

    public String getReturnCode() {
        return returnCode;
    }

    public void setReturnCode(String returnCode) {
        this.returnCode = returnCode;
    }

    public String getHttpVersion() {
        return httpVersion;
    }

    public void setHttpVersion(String httpVersion) {
        this.httpVersion = httpVersion;
    }


}
