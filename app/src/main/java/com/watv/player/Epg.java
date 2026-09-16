package com.watv.player;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.*;

final class Epg {
    static final class Programme {
        String channel,title="",description="";long start,stop;
        String label(){SimpleDateFormat f=new SimpleDateFormat("dd/MM HH:mm",Locale.getDefault());return f.format(new Date(start))+" • "+title+"\n"+description;}
    }
    static List<Programme> parse(String xml) throws Exception {
        if(xml.toUpperCase(Locale.ROOT).contains("<!DOCTYPE") || xml.toUpperCase(Locale.ROOT).contains("<!ENTITY"))throw new IllegalArgumentException("XMLTV com DTD não suportado");
        List<Programme> result=new ArrayList<>();XmlPullParser p=Xml.newPullParser();p.setInput(new StringReader(xml));
        Programme current=null; long now=System.currentTimeMillis();
        while(p.next()!=XmlPullParser.END_DOCUMENT) {
            if(p.getEventType()==XmlPullParser.START_TAG){
                if(p.getName().equals("programme")){current=new Programme();current.channel=p.getAttributeValue(null,"channel");current.start=time(p.getAttributeValue(null,"start"));current.stop=time(p.getAttributeValue(null,"stop"));}
                else if(current!=null && p.getName().equals("title"))current.title=p.nextText();
                else if(current!=null && p.getName().equals("desc"))current.description=p.nextText();
            } else if(p.getEventType()==XmlPullParser.END_TAG && p.getName().equals("programme") && current!=null){
                if(current.channel!=null && current.stop>now-86400000L && current.start<now+7*86400000L)result.add(current);current=null;
            }
        }
        result.sort(Comparator.comparingLong(x->x.start));return result;
    }
    private static long time(String s) {
        if(s==null)return 0;
        try {SimpleDateFormat f=new SimpleDateFormat(s.trim().length()>14?"yyyyMMddHHmmss Z":"yyyyMMddHHmmss",Locale.ROOT);f.setTimeZone(TimeZone.getTimeZone("UTC"));return f.parse(s.trim()).getTime();}
        catch(Exception e){return 0;}
    }
}
