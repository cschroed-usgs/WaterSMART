package gov.usgs.cida.watersmart.util;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import sun.misc.BASE64Decoder;

/**
 *
 * @author isuftin
 */
public class Export extends HttpServlet {

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String filename = request.getParameter("filename");
        String data =  request.getParameter("data");
        String type = request.getParameter("type");
        
        if (StringUtils.isBlank(filename) || StringUtils.isBlank(data)) {
            response.sendError(500, "Either 'filename' or 'data' elements were empty.");
            return;
        }
        
        StringBuilder sb = new StringBuilder(data);
        byte[] dataByteArr;
        if ("image/png;base64".equalsIgnoreCase(type)) {
            dataByteArr = new BASE64Decoder().decodeBuffer(sb.toString());
        } else {
            dataByteArr = sb.toString().getBytes("UTF-8");
        }
        int length = dataByteArr.length;
        
        InputStream in = null;
        BufferedOutputStream out = null;

        try {
            response.setContentType("application/octet-stream");
            response.setContentLength(length);
            response.setHeader("Content-Disposition", "attachment;filename=" + filename);
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
            
            in = new ByteArrayInputStream(dataByteArr);
            out = new BufferedOutputStream(response.getOutputStream());
           
            IOUtils.copy(in, out);
        } finally {
            IOUtils.closeQuietly(out);
            IOUtils.closeQuietly(in);
        }
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /** 
     * Returns a short description of the servlet.
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Exports posted data via a file back to the client";
    }// </editor-fold>
}
