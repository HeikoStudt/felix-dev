/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.felix.webconsole;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLConnection;
import java.text.MessageFormat;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.servlet.ServletRequestContext;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;


public abstract class AbstractWebConsolePlugin extends HttpServlet
{

    /** Pseudo class version ID to keep the IDE quite. */
    private static final long serialVersionUID = 1L;

    /** The name of the request attribute containig the map of FileItems from the POST request */
    public static final String ATTR_FILEUPLOAD = "org.apache.felix.webconsole.fileupload";

    private static final String HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"
        + "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"xhtml1-transitional.dtd\">"
        + "<html xmlns=\"http://www.w3.org/1999/xhtml\">"
        + "<head>"
        + "<meta http-equiv=\"Content-Type\" content=\"text/html; utf-8\">"
        + "<link rel=\"icon\" href=\"{6}/res/imgs/favicon.ico\">"
        + "<title>{0} - {2}</title>"
        + "<script src=\"{5}/res/ui/jquery-1.3.2.min.js\" language=\"JavaScript\"></script>"
        + "<script src=\"{5}/res/ui/jquery.tablesorter-2.0.3.min.js\" language=\"JavaScript\"></script>"
        + "<script src=\"{5}/res/ui/admin.js\" language=\"JavaScript\"></script>"
        + "<script src=\"{5}/res/ui/ui.js\" language=\"JavaScript\"></script>"
        + "<script language=\"JavaScript\">"
        + "appRoot = \"{5}\";"
        + "pluginRoot = appRoot + \"/{6}\";"
        + "</script>"
        + "<link href=\"{5}/res/ui/admin.css\" rel=\"stylesheet\" type=\"text/css\">"
        + "</head>"
        + "<body>"
        + "<div id=\"main\">"
        + "<div id=\"lead\">"
        + "<h1>"
        + "{0}<br>{2}"
        + "</h1>"
        + "<p>"
        + "<a target=\"_blank\" href=\"{3}\" title=\"{1}\"><img src=\"{5}/res/imgs/logo.png\" width=\"165\" height=\"63\" border=\"0\"></a>"
        + "</p>" + "</div>";

    /*
    String header = MessageFormat.format( HEADER, new Object[]
         { adminTitle, productName, getTitle(), productWeb, vendorName,
                 ( String ) request.getAttribute( OsgiManager.ATTR_APP_ROOT ), getLabel() } );
    */

    public static final String GET_RESOURCE_METHOD_NAME = "getResource";

    private final Method getResourceMethod;

    private BundleContext bundleContext;

    private String adminTitle;
    private String productName;
    private String productWeb;
    private String vendorName;

    {
        getResourceMethod = getGetResourceMethod();
    }


    //---------- HttpServlet Overwrites ----------------------------------------

    /**
     * Returns the title for this plugin as returned by {@link #getTitle()}
     */
    public String getServletName()
    {
        return getTitle();
    }


    /**
     * Renders the web console page for the request. This consist of the following
     * five parts called in order:
     * <ol>
     * <li>Send back a requested resource
     * <li>{@link #startResponse(HttpServletRequest, HttpServletResponse)}</li>
     * <li>{@link #renderTopNavigation(HttpServletRequest, PrintWriter)}</li>
     * <li>{@link #renderContent(HttpServletRequest, HttpServletResponse)}</li>
     * <li>{@link #endResponse(PrintWriter)}</li>
     * </ol>
     * <p>
     * <b>Note</b>: If a resource is sent back for the request only the first
     * step is executed. Otherwise the first step is a null-operation actually
     * and the latter four steps are executed in order.
     */
    protected void doGet( HttpServletRequest request, HttpServletResponse response ) throws ServletException,
        IOException
    {
        if ( !spoolResource( request, response ) )
        {
            PrintWriter pw = startResponse( request, response );
            renderTopNavigation( request, pw );
            renderContent( request, response );
            endResponse( pw );
        }
    }


    //---------- AbstractWebConsolePlugin API ----------------------------------

    public void activate( BundleContext bundleContext )
    {
        this.bundleContext = bundleContext;

        Dictionary headers = bundleContext.getBundle().getHeaders();

        adminTitle = ( String ) headers.get( Constants.BUNDLE_NAME ); // "OSGi Management Console";
        productName = "Apache Felix";
        productWeb = ( String ) headers.get( Constants.BUNDLE_DOCURL );
        vendorName = ( String ) headers.get( Constants.BUNDLE_VENDOR );
    }


    public void deactivate()
    {
        this.bundleContext = null;
    }


    public abstract String getTitle();


    public abstract String getLabel();


    protected abstract void renderContent( HttpServletRequest req, HttpServletResponse res ) throws ServletException,
        IOException;


    protected BundleContext getBundleContext()
    {
        return bundleContext;
    }

    protected Object getResourceProvider() {
        return this;
    }

    /**
     * Returns a method which is called on the
     * {@link #getResourceProvider() resource provder} class to return an URL
     * to a resource which may be spooled when requested. The method has the
     * following signature:
     * <pre>
     * [modifier] URL getResource(String path);
     * </pre>
     * Where the <i>[modifier]</i> may be <code>public</code>, <code>protected</code>
     * or <code>private</code> (if the method is declared in the class of the
     * resource provider). It is suggested to use the <code>private</code>
     * modifier if the method is declared in the resource provider class or
     * the <code>protected</code> modifier if the method is declared in a
     * base class of the resource provider.
     *
     * @return The <code>getResource(String)</code> method or <code>null</code>
     *      if the {@link #getResourceProvider() resource provider} is
     *      <code>null</code> or does not provide such a method.
     */
    private Method getGetResourceMethod()
    {
        Method tmpGetResourceMethod = null;

        Object resourceProvider = getResourceProvider();
        if ( resourceProvider != null )
        {
            try
            {
                Class cl = resourceProvider.getClass();
                while ( tmpGetResourceMethod == null && cl != Object.class )
                {
                    Method[] methods = cl.getDeclaredMethods();
                    for ( int i = 0; i < methods.length; i++ )
                    {
                        Method m = methods[i];
                        if ( GET_RESOURCE_METHOD_NAME.equals( m.getName() ) && m.getParameterTypes().length == 1
                            && m.getParameterTypes()[0] == String.class && m.getReturnType() == URL.class )
                        {
                            // ensure modifier is protected or public or the private
                            // method is defined in the plugin class itself
                            int mod = m.getModifiers();
                            if ( Modifier.isProtected( mod ) || Modifier.isPublic( mod )
                                || ( Modifier.isPrivate( mod ) && cl == resourceProvider.getClass() ) )
                            {
                                m.setAccessible( true );
                                tmpGetResourceMethod = m;
                                break;
                            }
                        }
                    }
                    cl = cl.getSuperclass();
                }
            }
            catch ( Throwable t )
            {
                tmpGetResourceMethod = null;
            }
        }

        return tmpGetResourceMethod;
    }


    /**
     * If the request addresses a resource which may be served by the
     * <code>getResource</code> method of the
     * {@link #getResourceProvider() resource provider}, this method serves it
     * and returns <code>true</code>. Otherwise <code>false</code> is returned.
     * <code>false</code> is also returned if the resource provider has no
     * <code>getResource</code> method.
     * <p>
     * If <code>true</code> is returned, the request is considered complete and
     * request processing terminates. Otherwise request processing continues
     * with normal plugin rendering.
     *
     * @param request The request object
     * @param response The response object
     * @return <code>true</code> if the request causes a resource to be sent back.
     *
     * @throws IOException If an error occurrs accessing or spooling the resource.
     */
    private boolean spoolResource( HttpServletRequest request, HttpServletResponse response ) throws IOException
    {
        // no resource if no resource accessor
        if ( getResourceMethod == null )
        {
            return false;
        }

        String pi = request.getPathInfo();
        InputStream ins = null;
        try
        {

            // check for a resource, fail if none
            URL url = ( URL ) getResourceMethod.invoke( getResourceProvider(), new Object[]
                { pi } );
            if ( url == null )
            {
                return false;
            }

            // open the connection and the stream (we use the stream to be able
            // to at least hint to close the connection because there is no
            // method to explicitly close the conneciton, unfortunately)
            URLConnection connection = url.openConnection();
            ins = connection.getInputStream();

            // check whether we may return 304/UNMODIFIED
            long lastModified = connection.getLastModified();
            if ( lastModified > 0 )
            {
                long ifModifiedSince = request.getDateHeader( "If-Modified-Since" );
                if ( ifModifiedSince >= ( lastModified / 1000 * 1000 ) )
                {
                    // Round down to the nearest second for a proper compare
                    // A ifModifiedSince of -1 will always be less
                    response.setStatus( HttpServletResponse.SC_NOT_MODIFIED );

                    return true;
                }

                // have to send, so set the last modified header now
                response.setDateHeader( "Last-Modified", lastModified );
            }

            // describe the contents
            response.setContentType( getServletContext().getMimeType( pi ) );
            response.setIntHeader( "Content-Length", connection.getContentLength() );

            // spool the actual contents
            OutputStream out = response.getOutputStream();
            byte[] buf = new byte[2048];
            int rd;
            while ( ( rd = ins.read( buf ) ) >= 0 )
            {
                out.write( buf, 0, rd );
            }

            // over and out ...
            return true;
        }
        catch ( IllegalAccessException iae )
        {
            // log or throw ???
        }
        catch ( InvocationTargetException ite )
        {
            // log or throw ???
            // Throwable cause = ite.getTargetException();
        }
      finally
        {
            if ( ins != null )
            {
                try
                {
                    ins.close();
                }
                catch ( IOException ignore )
                {
                }
            }
        }

        return false;
    }


    protected PrintWriter startResponse( HttpServletRequest request, HttpServletResponse response ) throws IOException
    {
        response.setCharacterEncoding( "utf-8" );
        response.setContentType( "text/html" );

        PrintWriter pw = response.getWriter();

        String header = MessageFormat.format( HEADER, new Object[]
            { adminTitle, productName, getTitle(), productWeb, vendorName,
                ( String ) request.getAttribute( WebConsoleConstants.ATTR_APP_ROOT ), getLabel() } );
        pw.println( header );

        return pw;
    }


    protected void renderTopNavigation( HttpServletRequest request, PrintWriter pw )
    {
        // assume pathInfo to not be null, else this would not be called
        boolean linkToCurrent = true;
        String current = request.getPathInfo();
        int slash = current.indexOf( "/", 1 );
        if ( slash < 0 )
        {
            slash = current.length();
            linkToCurrent = false;
        }
        current = current.substring( 1, slash );

        boolean disabled = false;
        String appRoot = ( String ) request.getAttribute( WebConsoleConstants.ATTR_APP_ROOT );
        Map labelMap = ( Map ) request.getAttribute( WebConsoleConstants.ATTR_LABEL_MAP );
        if ( labelMap != null )
        {
            pw.println( "<div id='technav'>" );

            SortedMap map = new TreeMap();
            for ( Iterator ri = labelMap.entrySet().iterator(); ri.hasNext(); )
            {
                Map.Entry labelMapEntry = ( Map.Entry ) ri.next();
                if ( labelMapEntry.getKey() == null )
                {
                    // ignore renders without a label
                }
                else if ( disabled || current.equals( labelMapEntry.getKey() ) )
                {
                    if ( linkToCurrent )
                    {
                        map.put( labelMapEntry.getValue(), "<a class='technavat' href='" + appRoot + "/"
                            + labelMapEntry.getKey() + "'>" + labelMapEntry.getValue() + "</a>" );
                    }
                    else
                    {
                        map.put( labelMapEntry.getValue(), "<span class='technavat'>" + labelMapEntry.getValue()
                            + "</span>" );
                    }
                }
                else
                {
                    map.put( labelMapEntry.getValue(), "<a href='" + appRoot + "/" + labelMapEntry.getKey() + "'>"
                        + labelMapEntry.getValue() + "</a>" );
                }
            }

            for ( Iterator li = map.values().iterator(); li.hasNext(); )
            {
                pw.print( "<nobr>" );
                pw.print( li.next() );
                pw.println( "</nobr>" );
            }

            pw.println( "</div>" );
        }
    }


    protected void endResponse( PrintWriter pw )
    {
        pw.println( "</body>" );
        pw.println( "</html>" );
    }


    public static String getParameter( HttpServletRequest request, String name )
    {
        // just get the parameter if not a multipart/form-data POST
        if ( !ServletFileUpload.isMultipartContent( new ServletRequestContext( request ) ) )
        {
            return request.getParameter( name );
        }

        // check, whether we alread have the parameters
        Map params = ( Map ) request.getAttribute( ATTR_FILEUPLOAD );
        if ( params == null )
        {
            // parameters not read yet, read now
            // Create a factory for disk-based file items
            DiskFileItemFactory factory = new DiskFileItemFactory();
            factory.setSizeThreshold( 256000 );

            // Create a new file upload handler
            ServletFileUpload upload = new ServletFileUpload( factory );
            upload.setSizeMax( -1 );

            // Parse the request
            params = new HashMap();
            try
            {
                List items = upload.parseRequest( request );
                for ( Iterator fiter = items.iterator(); fiter.hasNext(); )
                {
                    FileItem fi = ( FileItem ) fiter.next();
                    FileItem[] current = ( FileItem[] ) params.get( fi.getFieldName() );
                    if ( current == null )
                    {
                        current = new FileItem[]
                            { fi };
                    }
                    else
                    {
                        FileItem[] newCurrent = new FileItem[current.length + 1];
                        System.arraycopy( current, 0, newCurrent, 0, current.length );
                        newCurrent[current.length] = fi;
                        current = newCurrent;
                    }
                    params.put( fi.getFieldName(), current );
                }
            }
            catch ( FileUploadException fue )
            {
                // TODO: log
            }
            request.setAttribute( ATTR_FILEUPLOAD, params );
        }

        FileItem[] param = ( FileItem[] ) params.get( name );
        if ( param != null )
        {
            for ( int i = 0; i < param.length; i++ )
            {
                if ( param[i].isFormField() )
                {
                    return param[i].getString();
                }
            }
        }

        // no valid string parameter, fail
        return null;
    }

    /**
     * Utility method to handle relative redirects.
     * Some app servers like web sphere handle relative redirects differently
     * therefore we should make an absolute url before invoking send redirect.
     */
    protected void sendRedirect(final HttpServletRequest request,
                                final HttpServletResponse response,
                                String redirectUrl) throws IOException {
        // check for relative url
        if ( !redirectUrl.startsWith("/") ) {
            String base = request.getContextPath() + request.getServletPath() + request.getPathInfo();
            int i = base.lastIndexOf('/');
            if (i > -1) {
                base = base.substring(0, i);
            } else {
                i = base.indexOf(':');
                base = (i > -1) ? base.substring(i + 1, base.length()) : "";
            }
            if (!base.startsWith("/")) {
                base = '/' + base;
            }
            redirectUrl = base + '/' + redirectUrl;

        }
        response.sendRedirect(redirectUrl);
    }
}
