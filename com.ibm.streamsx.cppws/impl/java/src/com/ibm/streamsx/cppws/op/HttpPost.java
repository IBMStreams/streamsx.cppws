/* Generated by Streams Studio: March 18, 2020 at 4:13:29 PM EDT */
/*
==============================================
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2020
==============================================
*/

/*
==================================================================
First created on: Mar/15/2020
Last modified on: Mar/26/2020

This Java operator is an utility operator available in the
streamsx.cppws toolkit. It can be used to do HTTP(S) post of
the plain text, JSON and XML formatted data. It can be used
to test the HTTP/HTTPS data reception feature of the 
streamsx.cppws::WebSocketSource operator. If you see sit,
you can also use it for use in your applications.

This operator can do the HTTPS (SSL) POST at a faster rate 
(200 posts per second). For the HTTP (non-SSL) POST, this utility 
Java operator can give a post rate of 1500 per second. 

The same tests done with the streamsx.inet toolkit's deprecated 
HTTPPost operator resulted in 28 HTTPS (SSL) and 350 HTTP (non-SSL)
posts per second.
==================================================================
*/
package com.ibm.streamsx.cppws.op;


import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.*;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import com.ibm.streams.operator.AbstractOperator;
import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamSchema;
import com.ibm.streams.operator.Attribute;
import com.ibm.streams.operator.StreamingData.Punctuation;
import com.ibm.streams.operator.StreamingInput;
import com.ibm.streams.operator.StreamingOutput;
import com.ibm.streams.operator.Tuple;
import com.ibm.streams.operator.model.InputPortSet;
import com.ibm.streams.operator.model.InputPortSet.WindowMode;
import com.ibm.streams.operator.model.InputPortSet.WindowPunctuationInputMode;
import com.ibm.streams.operator.model.InputPorts;
import com.ibm.streams.operator.model.Libraries;
import com.ibm.streams.operator.model.OutputPortSet;
import com.ibm.streams.operator.model.OutputPortSet.WindowPunctuationOutputMode;
import com.ibm.streams.operator.model.OutputPorts;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.model.PrimitiveOperator;


/**
 * Class for an operator that receives a tuple and then optionally submits a tuple. 
 * This pattern supports one or more input streams and one or more output streams. 
 * <P>
 * The following event methods from the Operator interface can be called:
 * </p>
 * <ul>
 * <li><code>initialize()</code> to perform operator initialization</li>
 * <li>allPortsReady() notification indicates the operator's ports are ready to process and submit tuples</li> 
 * <li>process() handles a tuple arriving on an input port 
 * <li>processPuncuation() handles a punctuation mark arriving on an input port 
 * <li>shutdown() to shutdown the operator. A shutdown request may occur at any time, 
 * such as a request to stop a PE or cancel a job. 
 * Thus the shutdown() may occur while the operator is processing tuples, punctuation marks, 
 * or even during port ready notification.</li>
 * </ul>
 * <p>With the exception of operator initialization, all the other events may occur concurrently with each other, 
 * which lead to these methods being called concurrently by different threads.</p> 
 */
@PrimitiveOperator(name="HttpPost", namespace="com.ibm.streamsx.cppws.op",
description=HttpPost.DESC)
@InputPorts({@InputPortSet(description="Receives tuples whose first string based attribute's content will be sent as the HTTP(S) POST content.", 
cardinality=1, optional=false, windowingMode=WindowMode.NonWindowed, windowPunctuationInputMode=WindowPunctuationInputMode.Oblivious), @InputPortSet(description="Optional input ports", optional=true, windowingMode=WindowMode.NonWindowed, windowPunctuationInputMode=WindowPunctuationInputMode.Oblivious)})
@OutputPorts({@OutputPortSet(description="Emits a tuple containing the HTTP POST status code, status message and the response from " +
"the remote web server. This tuple's schema should be tuple<int32 statusCode, rstring statusMessage, rstring responseMessage>. " +
"Any other matching attributes from the incoming tuple will be forwarded via the output tuple.", 
cardinality=1, optional=false, windowPunctuationOutputMode=WindowPunctuationOutputMode.Generating), @OutputPortSet(description="Optional output ports", optional=true, windowPunctuationOutputMode=WindowPunctuationOutputMode.Generating)})
@Libraries("opt/HTTPClient-4.3.6/lib/*")
public class HttpPost extends AbstractOperator {
	private HttpClient httpClient = null;
	private String url = null;
	private String contentType = "text/plain";
	private boolean logHttpPostActions  = false;
	private long httpPostCnt = 0;
	
    /**
     * Initialize this operator. Called once before any tuples are processed.
     * @param context OperatorContext for this operator.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
	@Override
	public synchronized void initialize(OperatorContext context)
			throws Exception {
    	// Must call super.initialize(context) to correctly setup an operator.
		super.initialize(context);
        Logger.getLogger(this.getClass()).trace("Operator " + context.getName() + " initializing in PE: " + context.getPE().getPEId() + " in Job: " + context.getPE().getJobId() );
        
        // TODO:
        // If needed, insert code to establish connections or resources to communicate an external system or data store.
        // The configuration information for this may come from parameters supplied to the operator invocation, 
        // or external configuration files or a combination of the two.
        // Get our HTTP Client that will work with SSL connections.
        httpClient = getHttpClient(url);
        
    	if (httpClient == null) {
    		System.out.println("We have no valid HTTP client. Incoming tuples arriving for HTTP POST will be ignored.");
    	}
	}

    /**
     * Notification that initialization is complete and all input and output ports 
     * are connected and ready to receive and submit tuples.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    @Override
    public synchronized void allPortsReady() throws Exception {
    	// This method is commonly used by source operators. 
    	// Operators that process incoming tuples generally do not need this notification. 
        OperatorContext context = getOperatorContext();
        Logger.getLogger(this.getClass()).trace("Operator " + context.getName() + " all ports are ready in PE: " + context.getPE().getPEId() + " in Job: " + context.getPE().getJobId() );
    }

    private static HttpClient getHttpClient(String myUrl) {
    	// Get either a plain or secure HTTP client for a given URL.
        try {
            SSLContext sslContext = SSLContext.getInstance("SSL");

            sslContext.init(null,
                    new TrustManager[]{new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {

                            return null;
                        }

                        public void checkClientTrusted(
                                X509Certificate[] certs, String authType) {

                        }

                        public void checkServerTrusted(
                                X509Certificate[] certs, String authType) {

                        }
                    }}, new SecureRandom());

            SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(sslContext,SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

            // We will create a HTTP client with SSL only if the URL has https: at the beginning.
            if (myUrl.indexOf("https:") == 0) {
            	HttpClient myHttpClient = HttpClientBuilder.create().setSSLSocketFactory(socketFactory).build();
            	return myHttpClient;
            } else {
            	// URL doesn't start with https: and we can create a plain HTTP client with no SSL support.
            	HttpClient myHttpClient = HttpClientBuilder.create().build();
            	return myHttpClient;
            }
        } catch (Exception e) {
            e.printStackTrace();
            HttpClient myHttpClient = HttpClientBuilder.create().build();
            return myHttpClient;
        }
    }      
    
    /**
     * Process an incoming tuple that arrived on the specified port.
     * <P>
     * Copy the incoming tuple to a new output tuple and submit to the output port. 
     * </P>
     * @param inputStream Port the tuple is arriving on.
     * @param tuple Object representing the incoming tuple.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    @Override
    public final void process(StreamingInput<Tuple> inputStream, Tuple tuple)
            throws Exception {

    	// Create a new tuple for output port 0
        StreamingOutput<OutputTuple> outStream = getOutput(0);
        OutputTuple outTuple = outStream.newTuple();

        // Copy across all matching attributes.
        outTuple.assign(tuple);

        // TODO: Insert code to perform transformation on output tuple as needed:
        // outTuple.setString("AttributeName", "AttributeValue");

    	if (httpClient == null) {
    		return;
    	}

        // Create a HTTP post object.
    	// As of Mar/19/2020, this operator supports posting of 
    	// text based data and not binary data.
    	org.apache.http.client.methods.HttpPost httpPost = 
    		new org.apache.http.client.methods.HttpPost(url);
    	// At this time, the incoming tuple must have its string based
    	// POST content in its first attribute. In a future release,
    	// this operator will support blob content to be posted.
    	httpPost.setHeader("Content-Type", contentType);
    	org.apache.http.entity.ContentType ct = 
    		org.apache.http.entity.ContentType.create(contentType);
    	
    	if (ct == null) {
    		OperatorContext context = getOperatorContext();
            Logger.getLogger(this.getClass()).error("Operator " + 
            	context.getName() + ", Unable to create a MIME content type object from " + 
            		contentType + ": " + context.getPE().getPEId() + 
            		" in Job: " + context.getPE().getJobId() );
            return;
    	}
    	
        // if the content-type is set to application/x-www-form-urlencoded, then we will
        // conform to the normal practice of having the request body's format as the query string.
        // e-g: param1=value
        if (contentType.equalsIgnoreCase("application/x-www-form-urlencoded") == true) {
        	// This technique is explained in this URL: 
        	// https://stackoverflow.com/questions/8120220/how-to-use-parameters-with-httppost
        	ArrayList<NameValuePair> postParameters;
        	postParameters = new ArrayList<NameValuePair>();
        	StreamSchema ss = tuple.getStreamSchema();
        	Attribute a1 = ss.getAttribute(0);
        	String a1Name = a1.getName();
        	postParameters.add(new BasicNameValuePair(a1Name, tuple.getString(a1Name)));
        	UrlEncodedFormEntity ue = new UrlEncodedFormEntity(postParameters, "UTF-8");
        	httpPost.setEntity(ue);
        } else  {
        	StringEntity se = new StringEntity(tuple.getString(0), ct);
        	httpPost.setEntity(se);
        }
        
        // Set the connection keep-alive request header.
        httpPost.setHeader("connection", "keep-alive");
        httpPostCnt++;
        
        if(logHttpPostActions == true) {
        	System.out.println((httpPostCnt) + 
    			") Executing request " + httpPost.getRequestLine());
        }

        HttpResponse response = httpClient.execute(httpPost);
                
        int responseStatusCode = response.getStatusLine().getStatusCode();
        String responseStatusReason = response.getStatusLine().getReasonPhrase();
        HttpEntity resEntity = response.getEntity();
        String responseMessage = "";
        
        if (resEntity != null) {
        	// System.out.println("HTTP response JSON=" + EntityUtils.toString(resEntity));
        	responseMessage = EntityUtils.toString(resEntity);
        }
        
        if (resEntity != null) {
        	EntityUtils.consume(resEntity);
        }
        
        if(logHttpPostActions == true) {
        	System.out.println((httpPostCnt) + ") Response=" + 
        		response.getStatusLine() + " " + responseMessage);
        }
        
        // Release the HTTP connection which is a must after 
        // consuming the response from the remote web server.
        // If we don't do this, it will start hanging when doing the 
        // next HTTP POST for the next incoming  tuple.
        httpPost.releaseConnection();
        
        outTuple.setInt("statusCode", responseStatusCode);
        outTuple.setString("statusMessage", responseStatusReason);
        outTuple.setString("responseMessage", responseMessage);
        // Submit new tuple to output port 0
        outStream.submit(outTuple);
        
        // Submit new tuple to output port 0
        outStream.submit(outTuple);
    }
    
    /**
     * Process an incoming punctuation that arrived on the specified port.
     * @param stream Port the punctuation is arriving on.
     * @param mark The punctuation mark
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    @Override
    public void processPunctuation(StreamingInput<Tuple> stream,
    		Punctuation mark) throws Exception {
    	// For window markers, punctuate all output ports 
    	super.processPunctuation(stream, mark);
    }

    /**
     * Shutdown this operator.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    public synchronized void shutdown() throws Exception {
        OperatorContext context = getOperatorContext();
        Logger.getLogger(this.getClass()).trace("Operator " + context.getName() + " shutting down in PE: " + context.getPE().getPEId() + " in Job: " + context.getPE().getJobId() );
        
        // TODO: If needed, close connections or release resources related to any external system or data store.
        // Close the HTTP client connection before shutting down this operator.
        if (httpClient != null) {
        	httpClient.getConnectionManager().shutdown();
        }
                
        // Must call super.shutdown()
        super.shutdown();
        // Must call super.shutdown()
        super.shutdown();
    }
    
    @Parameter (name="url", description="Specify the URL where HTTP POSTs will be made to.", optional=false)
    public void setUrl(String _url) {
    	url = _url;
    }
    
    @Parameter (name="contentType", description="Specify the MIME content type that you want. Default is text/plain.", optional=true)
    public void setContentType(String _contentType) {
    	contentType = _contentType;
    }
    
    @Parameter (name="logHttpPostActions", description="Do you want to log HTTP POST actions to the screen? (Default: false)", optional=true)
    public void setLogOnScreen(boolean val) {
    	logHttpPostActions = val;
    }
    
    public static final String DESC = "This operator sends the incoming tuple's contents to the " +
    		"specified HTTP or HTTPS endpoint via the operator parameter named url. The incoming tuple " +
    		"must have its first attribute with a data type rstring and it must carry " + 
    		"string based content that needs be posted to the remote web server. " +
    		"Support for blob data will be added in a future version. This operator is " + 
    		"mainly used to test the HTTP(S) feature available in the WebSocketSource " +
    		"operator from  the streamsx.cppws toolkit. If this operator can be useful " +
    		"in other application scenarios, developers can use it as they see fit.";
}
