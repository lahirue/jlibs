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

package jlibs.xml.sax.helpers;

import jlibs.core.lang.Util;
import jlibs.core.net.URLUtil;
import jlibs.xml.Namespaces;
import org.xml.sax.helpers.NamespaceSupport;

import javax.xml.namespace.QName;
import java.util.Enumeration;
import java.util.Properties;

/**
 * @author Santhosh Kumar T
 */
public class MyNamespaceSupport extends NamespaceSupport{
    private Properties suggested;

    public MyNamespaceSupport(){
        this(Namespaces.getSuggested());
    }

    public MyNamespaceSupport(Properties suggested){
        this.suggested = suggested;
    }

    /**
     * This method is used to override the prefix generated by
     * {@link #declarePrefix(String)}, to your own choice.
     *
     * <p> if the suggested prefix already exists, then it will
     * generate new prefix, from suggested prefix by appending
     * a number
     */
    public void suggestPrefix(String prefix, String uri){
        suggested.put(prefix, uri);
    }

    /**
     * Return one of the prefixes mapped to a Namespace URI.
     *
     * <p>If more than one prefix is currently mapped to the same
     * URI, this method will make an arbitrary selection;
     *
     * <p>Unlike {@link #getPrefix(String)} this method, this returns empty
     * prefix if the given uri is bound to default prefix.
     */
    public String findPrefix(String uri){
        if(uri==null)
            uri = "";
        String prefix = getPrefix(uri);
        if(prefix==null){
            String defaultURI = getURI("");
            if(defaultURI==null)
                defaultURI = "";
            if(Util.equals(uri, defaultURI))
                prefix = "";
        }
        return prefix;
    }

    public String findURI(String prefix){
        if(prefix==null)
            return "";
        String uri = getURI(prefix);
        if(uri==null){
            if(prefix.isEmpty())
                return "";
        }
        return uri;
    }

    /**
     * generated a new prefix and binds it to given uri.
     *
     * <p>you can customize the generated prefix using {@link #suggestPrefix(String, String)}
     */
    public String declarePrefix(String uri){
        String prefix = findPrefix(uri);
        if(prefix==null){
            prefix = URLUtil.suggestPrefix(suggested, uri);
            if(getURI(prefix)!=null){
                int i = 1;
                String _prefix;
                while(true){
                    _prefix = prefix + i;
                    if(getURI(_prefix)==null){
                        prefix = _prefix;
                        break;
                    }
                    i++;
                }
            }
            declarePrefix(prefix, uri);
        }
        return prefix;
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public Enumeration<String> getPrefixes(String uri){
        return super.getPrefixes(uri);
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public Enumeration<String> getPrefixes(){
        return super.getPrefixes();
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public Enumeration<String> getDeclaredPrefixes(){
        return super.getDeclaredPrefixes();
    }

    public boolean isDeclaredPrefix(String prefix){
        Enumeration declaredPrefixes = getDeclaredPrefixes();
        while(declaredPrefixes.hasMoreElements()){
            if(prefix.equals(declaredPrefixes.nextElement()))
                return true;
        }
        return false;
    }

    public QName toQName(String qname){
        String prefix = "";
        String localName = qname;

        int colon = qname.indexOf(':');
        if(colon!=-1){
            prefix = qname.substring(0, colon);
            localName = qname.substring(colon+1);
        }

        String uri = findURI(prefix);
        if(uri==null)
            throw new IllegalArgumentException("prefix \""+prefix+"\" is not bound to any uri");
        return new QName(uri, localName, prefix);
    }

    public String toQName(String uri, String localName){
        String prefix = findPrefix(uri);
        if(prefix==null)
            throw new IllegalArgumentException("no prefix found for uri \""+uri+"\"");
        return "".equals(prefix) ? localName : prefix+':'+localName;
    }

    /*-------------------------------------------------[ SAX Population ]---------------------------------------------------*/

    private boolean needNewContext;

    public void startDocument(){
        reset();
        needNewContext = true;
    }

    public void startPrefixMapping(String prefix, String uri){
        if(needNewContext){
            pushContext();
            needNewContext = false;
        }
        declarePrefix(prefix, uri);
    }

    public String startPrefixMapping(String uri){
        if(needNewContext){
            pushContext();
            needNewContext = false;
        }
        return declarePrefix(uri);
    }

    public void startElement(){
        if(needNewContext)
            pushContext();
        needNewContext = true;
    }

    public void endElement(){
        popContext();
    }

    public static void main(String[] args){
        MyNamespaceSupport ns = new MyNamespaceSupport();
        System.out.println(ns.declarePrefix("http://www.sonoasystems.com/schemas/2007/8/3/sci/"));
        System.out.println(ns.declarePrefix("http://www.sonoasystems.com/schemas/2007/8/7/sci/"));
        System.out.println(ns.declarePrefix("http://com"));
        System.out.println(ns.declarePrefix("http://google.org"));
    }
}
