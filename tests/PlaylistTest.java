package com.watv.player;
import java.io.*;
import java.util.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
public class PlaylistTest {
    static int checks;
    static void check(boolean value) { checks++; if(!value)throw new AssertionError("Falha "+checks); }
    public static void main(String[] args) throws Exception {
        String m3u="\uFEFF#EXTM3U\r\n#EXTINF:-1 group-title=\"Notícias, Brasil\",Canal Um\r\nlive/1.ts\r\n#EXTINF:-1,Repetido\r\nlive/1.ts\n#EXTINF:-1,Inválido\nfile:///etc/passwd\n#EXTINF:-1,Canal Dois\nhttps://example.com/2.m3u8";
        List<Playlist.Channel> c=Playlist.parse(m3u,"https://example.com/list.m3u");
        check(c.size()==2); check(c.get(0).name.equals("Canal Um")); check(c.get(0).group.equals("Notícias, Brasil"));
        check(c.get(0).url.equals("https://example.com/live/1.ts")); check(c.get(0).id.length()==64);
        check(Playlist.xtream("https://example.com:8443/","a&b","x+y").equals("https://example.com:8443/get.php?username=a%26b&password=x%2By&type=m3u_plus&output=ts"));
        for(String invalid:new String[]{"<html>login</html>","#EXTM3U\n","#EXTM3U\n#EXT-X-TARGETDURATION:10\npart.ts"}) {
            boolean rejected=false; try { Playlist.parse(invalid,"https://example.com/"); } catch(IOException e){rejected=true;} check(rejected);
        }
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/list",exchange->{byte[] bytes="#EXTM3U\n#EXTINF:-1,Teste\nhttps://example.com/live.ts".getBytes("UTF-8");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});
        server.createContext("/redirect",exchange->{exchange.getResponseHeaders().add("Location","/list");exchange.sendResponseHeaders(302,-1);exchange.close();});
        server.createContext("/denied",exchange->{exchange.sendResponseHeaders(403,-1);exchange.close();});
        server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            check(Playlist.download(base+"/redirect").get(0).name.equals("Teste"));
            boolean denied=false;try{Playlist.download(base+"/denied");}catch(IOException e){denied=e.getMessage().contains("Acesso recusado");}check(denied);
        } finally{server.stop(0);}
        System.out.println(checks+" verificações aprovadas.");
    }
}
