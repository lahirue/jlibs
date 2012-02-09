/**
 * JLibs: Common Utilities for Java
 * Copyright (C) 2009  Santhosh Kumar T <santhosh.tekuri@gmail.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 */

package jlibs.wadl.cli.commands;

import jlibs.core.io.FileUtil;
import jlibs.core.io.IOUtil;
import jlibs.core.lang.Ansi;
import jlibs.core.lang.JavaProcessBuilder;
import jlibs.core.lang.NotImplementedException;
import jlibs.core.util.RandomUtil;
import jlibs.wadl.cli.WADLTerminal;
import jlibs.wadl.cli.commands.auth.BasicAuthenticator;
import jlibs.wadl.cli.model.Path;
import jlibs.wadl.cli.ui.Editor;
import jlibs.wadl.model.Method;
import jlibs.wadl.model.Representation;
import jlibs.wadl.model.Request;
import jlibs.xml.sax.AnsiHandler;
import jlibs.xml.sax.XMLDocument;
import jlibs.xml.xsd.XSInstance;

import javax.xml.namespace.QName;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.net.HttpURLConnection;
import java.util.*;

import static jlibs.core.lang.Ansi.Attribute;
import static jlibs.core.lang.Ansi.Color;

/**
 * @author Santhosh Kumar T
 */
public class Runner{
    private WADLTerminal terminal;

    public Runner(WADLTerminal terminal){
        this.terminal = terminal;
    }

    public boolean run(String command) throws Exception{
        List<String> args = getArguments(command);

        String arg1 = args.get(0);
        if(arg1.equals("import")){
            args.remove(0);
            new Import(terminal).run(args);
        }else if(arg1.equals("cd"))
            return cd(args.size()==1 ? null : args.get(1));
        else if(arg1.equals("set")){
            Properties vars = new Properties();
            for(String arg: args){
                int equals = arg.indexOf('=');
                if(equals!=-1){
                    String var = arg.substring(0, equals);
                    String value = arg.substring(equals+1);
                    vars.setProperty(var, value);
                }
            }
            Path path = terminal.getCurrentPath();
            while(path!=null){
                String var = path.variable();
                if(var!=null){
                    String value = vars.getProperty(var);
                    if(value!=null)
                        path.value = value;
                }
                path = path.parent;
            }
        }else if(arg1.equals("target"))
            terminal.getCurrentPath().getRoot().value = args.size()==1 ? null : args.get(1);
        else if(arg1.equals("server")){
            server(args.get(1));
        }else if(arg1.equals("authenticate")){
            if(args.size()==1)
                return false;
            authenticate(args.get(1), args.subList(2, args.size()));
        }else
            return send(args);
        return true;
    }

    private List<String> getArguments(String command){
        List<String> args = new ArrayList<String>();
        StringTokenizer stok = new StringTokenizer(command, " ");
        while(stok.hasMoreTokens())
            args.add(stok.nextToken());
        return args;
    }

    private boolean cd(String pathString){
        Path path = terminal.getCurrentPath();
        if(pathString==null)
            path = path.getRoot();
        else
            path = path.get(pathString);
        if(path==null){
            System.err.println("no such resource");
            return false;
        }else{
            terminal.setCurrentPath(path);
            return true;
        }
    }


    private void server(String server){
        for(Path root: terminal.getRoots()){
            if(root.name.equalsIgnoreCase(server)){
                terminal.setCurrentPath(root);
                return;
            }
        }
    }

    private static final File FILE_PAYLOAD = new File("temp.xml");

    private void generatePayload(Path path, QName element) throws Exception{
        if(path.variable()!=null){
            for(Object item: path.resource.getMethodOrResource()){
                if(item instanceof Method){
                    Method method = (Method)item;
                    if(method.getName().equalsIgnoreCase("GET")){
                        try{
                            HttpURLConnection con = path.execute(method, new HashMap<String, List<String>>(), null);
                            if(con.getResponseCode()==200){
                                IOUtil.pump(con.getInputStream(), new FileOutputStream(FILE_PAYLOAD), true, true);
                                return;
                            }
                        }catch(Exception ex){
                            ex.printStackTrace();
                        }
                    }
                }
            }
        }

        XSInstance xsInstance = new XSInstance();
        XMLDocument xml = new XMLDocument(new StreamResult(FILE_PAYLOAD), true, 4, null);
        xsInstance.generate(path.getSchema(), element, xml);
    }

    private HttpURLConnection prepareSend(List<String> args) throws Exception{
        Path path = terminal.getCurrentPath();
        if(args.size()>1)
            path = path.get(args.get(1));
        if(path==null || path.resource==null){
            System.err.println("resource not found");
            return null;
        }

        Method method = null;
        for(Object obj: path.resource.getMethodOrResource()){
            if(obj instanceof Method){
                Method m = (Method)obj;
                if(m.getName().equalsIgnoreCase(args.get(0))){
                    method = m;
                    break;
                }
            }
        }
        if(method==null){
            System.err.println("unsupported method: "+args.get(0));
            return null;
        }

        Request request = method.getRequest();
        File payload = null;
        if(request!=null){
            if(!request.getRepresentation().isEmpty()){
                Representation rep = request.getRepresentation().get(RandomUtil.random(0, request.getRepresentation().size()-1));
                if(rep.getElement()!=null){
                    payload = FILE_PAYLOAD;
                    generatePayload(path, rep.getElement());
                }
            }
        }

        if(payload!=null){
            JavaProcessBuilder processBuilder = new JavaProcessBuilder();
            StringTokenizer stok = new StringTokenizer(System.getProperty("java.class.path"), FileUtil.PATH_SEPARATOR);
            while(stok.hasMoreTokens())
                processBuilder.classpath(stok.nextToken());
            processBuilder.mainClass(Editor.class.getName());
            processBuilder.arg(payload.getAbsolutePath());
            processBuilder.arg("text/xml");
            processBuilder.launch(DUMMY_OUTPUT, DUMMY_OUTPUT).waitFor();
            if(!payload.exists())
                return null;
        }

        return path.execute(method, new HashMap<String, List<String>>(), payload);
    }

    private static final Ansi SUCCESS = new Ansi(Attribute.BRIGHT, Color.GREEN, Color.BLACK);
    private static final Ansi FAILURE = new Ansi(Attribute.BRIGHT, Color.RED, Color.BLACK);

    public boolean authenticate(HttpURLConnection con) throws IOException{
        String value = con.getHeaderField("WWW-Authenticate");
        if(value==null)
            return false;
        int space = value.indexOf(' ');
        if(space==-1)
            return false;
        if(!authenticate(value.substring(0, space), Collections.<String>emptyList()))
            return false;
        return true;
    }

    private boolean send(List<String> args) throws Exception{
        HttpURLConnection con = prepareSend(args);
        if(con==null)
            return false;

        if(con.getResponseCode()==401){ // Unauthorized
            if(authenticate(con))
                return send(args);
            else
                return false;
        }
        Ansi result = con.getResponseCode()/100==2 ? SUCCESS : FAILURE;
        result.outln(con.getResponseCode()+" "+con.getResponseMessage());
        System.out.println();

        boolean success = true;
        InputStream in = con.getErrorStream();
        if(in==null)
            in = con.getInputStream();
        else
            success = false;

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        IOUtil.pump(in, bout, true, true);
        if (bout.size() == 0)
            return success;
        if(isXML(con.getContentType())){
            PrintStream sysErr = System.err;
            System.setErr(new PrintStream(new ByteArrayOutputStream()));
            try {
                TransformerFactory factory = TransformerFactory.newInstance();
                Transformer transformer = factory.newTransformer();
                transformer.transform(new StreamSource(new ByteArrayInputStream(bout.toByteArray())), new SAXResult(new AnsiHandler()));
                transformer.reset();
                return success;
            } catch (Exception ex) {
                // ignore
            } finally {
                System.setErr(sysErr);
            }
        }
        System.out.println(bout);
        System.out.println();
        return success;
    }

    public static boolean isXML(String contentType) {
        if(contentType==null)
            return false;
        int semicolon = contentType.indexOf(';');
        if(semicolon!=-1)
            contentType = contentType.substring(0, semicolon);
        if("text/xml".equalsIgnoreCase(contentType))
            return true;
        else if(contentType.startsWith("application/"))
            return contentType.endsWith("application/xml") || contentType.endsWith("+xml");
        else
            return false;
    }

    public boolean authenticate(String type, List<String> args) throws IOException{
        if(type.equalsIgnoreCase("none")){
            terminal.getCurrentPath().getRoot().authenticator = null;
            return true;
        }else if(type.equalsIgnoreCase(BasicAuthenticator.TYPE)){
            String user;
            if(!args.isEmpty())
                user = args.remove(0);
            else
                user = terminal.console.readLine("Login: ");
            if(user==null)
                return false;

            String passwd;

            if(!args.isEmpty())
                passwd = args.remove(0);
            else
                passwd = terminal.console.readLine("Password: ", (char)0);

            terminal.getCurrentPath().getRoot().authenticator = new BasicAuthenticator(user, passwd);
            return true;
        }else
            throw new NotImplementedException(type);
    }

    private static final OutputStream DUMMY_OUTPUT = new OutputStream(){
        @Override
        public void write(int b) throws IOException{}
        @Override
        public void write(byte[] b) throws IOException{}
        @Override
        public void write(byte[] b, int off, int len) throws IOException{}
    };
}