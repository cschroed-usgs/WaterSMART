package gov.usgs.cida.watersmart.util;

import gov.usgs.cida.config.DynamicReadOnlyProperties;
import gov.usgs.cida.watersmart.common.JNDISingleton;
import gov.usgs.cida.watersmart.common.RunMetadata;
import gov.usgs.cida.watersmart.communication.EmailHandler;
import gov.usgs.cida.watersmart.communication.EmailMessage;
import gov.usgs.cida.watersmart.communication.HTTPUtils;
import gov.usgs.cida.watersmart.csw.CSWTransactionHelper;
import gov.usgs.cida.watersmart.parse.CreateDSGFromZip;
import gov.usgs.cida.watersmart.parse.StationLookup;
import gov.usgs.cida.watersmart.wps.completion.CheckProcessCompletion;
import gov.usgs.cida.watersmart.wps.completion.ProcessStatus;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.mail.MessagingException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathExpressionException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
class WPSImpl implements WPSInterface {

    static final String algorithmName = "org.n52.wps.server.r.stats_csv_nahat_test_wps";
    
    @Override
    public String executeProcess(File zipLocation, RunMetadata metadata) {
        return executeProcess(zipLocation, metadata.toKeyValueMap());
    }

    @Override
    public String executeProcess(File zipLocation, Map<String, String> metadata) {
        WPSTask task = new WPSTask(zipLocation, metadata);
        task.start();
        if (task.isAlive()) {
            return "ok";
        }
        else {
            return "not started";
        }
    }
    
    static String createWaterSMARTStatsAlgorithmRequest(String sosEndpoint) {

        // TODO need to get station list and properties from file just created
        return new String(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<wps:Execute service=\"WPS\" version=\"1.0.0\" " +
                    "xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" " +
                    "xmlns:ows=\"http://www.opengis.net/ows/1.1\" " +
                    "xmlns:xlink=\"http://www.w3.org/1999/xlink\" " +
                    "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                    "xsi:schemaLocation=\"http://www.opengis.net/wps/1.0.0 " +
                    "http://schemas.opengis.net/wps/1.0.0/wpsExecute_request.xsd\">" +
                "<ows:Identifier>"+algorithmName+"</ows:Identifier>" +
                "<wps:DataInputs>" +
                    "<wps:Input>" +
                        "<ows:Identifier>sos_url</ows:Identifier>" +
                        "<wps:Data>" +
                            "<wps:LiteralData>" +
                                StringEscapeUtils.escapeXml(sosEndpoint) +
                            "</wps:LiteralData>" +
                        "</wps:Data>" +
                    "</wps:Input>" +
                    "<wps:Input>" +
                        "<ows:Identifier>sites</ows:Identifier>" +
                        "<wps:Data>" +
                            "<wps:LiteralData>" +
                                "\\\"02177000\\\",\\\"02178400\\\",\\\"02184500\\\",\\\"02186000\\\"" +
                            "</wps:LiteralData>" +
                        "</wps:Data>" +
                    "</wps:Input>" +
                    "<wps:Input>" +
                        "<ows:Identifier>property</ows:Identifier>" +
                        "<wps:Data>" +
                            "<wps:LiteralData>" +
                                StringEscapeUtils.escapeXml("MEAN") +
                            "</wps:LiteralData>" +
                        "</wps:Data>" +
                    "</wps:Input>" +
                "</wps:DataInputs>" +
                "<wps:ResponseForm>" +
                    "<wps:ResponseDocument storeExecuteResponse=\"true\" status=\"true\">" +
                        "<wps:Output asReference=\"true\">" +
                            "<ows:Identifier>output</ows:Identifier>" +
                        "</wps:Output>" +
                    "</wps:ResponseDocument>" +
                "</wps:ResponseForm>" +
            "</wps:Execute>");
    }
}

class WPSTask extends Thread {

    static org.slf4j.Logger log = LoggerFactory.getLogger(WPSTask.class);
    private static final DynamicReadOnlyProperties props = JNDISingleton.getInstance();
    private File zipLocation;
    private Map<String, String> metadata;

    public WPSTask(File zipLocation, Map<String, String> metadata) {
        this.zipLocation = zipLocation;
        this.metadata = metadata;
    }

    String postToWPS(String url, String wpsRequest) throws IOException {
        HttpPost post = null;
        HttpClient httpClient = new DefaultHttpClient();

        post = new HttpPost(url);

        AbstractHttpEntity entity = new InputStreamEntity(new ByteArrayInputStream(wpsRequest.getBytes()), wpsRequest.length());
        post.setEntity(entity);
        HttpResponse response = httpClient.execute(post);

        return EntityUtils.toString(response.getEntity());

    }
    
    public boolean checkWPSProcess(Document document) throws
            XPathExpressionException, IOException {

        ProcessStatus procStat = new ProcessStatus(document);

        if (procStat.isSuccess()) {
            return true;
        }
//        else if (procStat.isStarted()) {
//            // keep it going
//        }
        else if (procStat.isFailed()){
            throw new IOException("Process failed");
        }
        return false;
    }

    public void sendCompleteEmail(String url, String to) throws MessagingException {
        String subject = "Processing Complete";
        String content = "Your upload has finished conversion and processing,"
                         + " you may view the results of the processing by going to "
                         + url + " or return to the application to view your upload.";
        List<String> bcc = new ArrayList<String>();
        String from = props.getProperty("watersmart.email.from");
        String bccAddr = props.getProperty("watersmart.email.tracker");
        if (!"".equals(bccAddr)) {
            bcc.add(bccAddr);
        }

        EmailMessage message = new EmailMessage(from, to, null, bcc, subject,
                                                content);
        EmailHandler.sendMessage(message);
    }
    
    public void sendFailedEmail(Exception ex) {
        String subject = "WaterSMART processing failed";
        StringBuilder content = new StringBuilder();
        content.append("The user uploaded a file, but processing failed, here is the stack trace:\n\n"); 
        for (StackTraceElement el : ex.getStackTrace()) {
            content.append(el.toString()).append("\n");
        }
        List<String> bcc = new ArrayList<String>();
        String from = props.getProperty("watersmart.email.from");
        String bccAddr = props.getProperty("watersmart.email.tracker");

        EmailMessage message = new EmailMessage(from, bccAddr, null, null, subject,
                                                content.toString());
        try {
            EmailHandler.sendMessage(message);
        }
        catch (MessagingException me) {
            log.error("Can't send email to maintainers for troubleshooting", ex);
        }
    }
    
    @Override
    public void run() {
        RunMetadata metaObj = RunMetadata.getInstance(metadata);
        String filename;
        InputStream is = null;
        try {
            filename = CreateDSGFromZip.create(zipLocation, metaObj);

            String repo = props.getProperty("watersmart.sos.model.repo");
            String sosEndpoint = repo + metaObj.getTypeString() + "/" + filename;
            String wpsRequest = WPSImpl.createWaterSMARTStatsAlgorithmRequest(sosEndpoint);
            String wpsResponse = postToWPS(props.getProperty("watersmart.wps.url"), wpsRequest);
            log.debug(wpsResponse);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document wpsResponseDoc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(wpsResponse.getBytes()));
            ProcessStatus procStat = new ProcessStatus(wpsResponseDoc);
            String wpsCheckPoint = procStat.getStatusLocation();
            log.debug(wpsCheckPoint);
            String contextPath = props.getProperty("watersmart.external.mapping.url");
            boolean completed = false;
            
            // leave this commented out until process exists
            Document document = null;
            while (!completed) {
                Thread.sleep(5000);
                log.debug("checking");
                is = HTTPUtils.sendPacket(new URL(wpsCheckPoint), "GET");
                document = CheckProcessCompletion.parseDocument(is);
                completed = checkWPSProcess(document);
            }
            
            ProcessStatus resultStatus = new ProcessStatus(document);
            String outputReference = resultStatus.getOutputReference();
            Document resultDocument = factory.newDocumentBuilder().parse(outputReference);
            // copy results to persistant location // switch to completed document above
            String xml = CSWTransactionHelper.nodeToString(resultDocument);
            log.debug(xml);
            File destinationFile = new File(
                    props.getProperty("watersmart.file.location") 
                    + props.getProperty("watersmart.file.location.wps.repository") 
                    + File.separatorChar 
                    + UUID.randomUUID() 
                    + ".xml");
            FileUtils.write(destinationFile, xml, "UTF-8");
            String destinationFileName = destinationFile.getName();
            String webAccessibleFile = contextPath + props.getProperty("watersmart.file.location.wps.repository") + "/" + destinationFileName;
            
            // move csw to module?
            CSWTransactionHelper helper = new CSWTransactionHelper(metaObj, sosEndpoint, WPSImpl.algorithmName, webAccessibleFile);
            String response = helper.insert();
            
            sendCompleteEmail(webAccessibleFile, metaObj.getEmail());
        }
        catch (Exception ex) {
            log.error("This is bad, send email to be fixed: " + ex.getMessage());
            sendFailedEmail(ex);
        }
        finally {
            IOUtils.closeQuietly(is);
        }
    }
}
