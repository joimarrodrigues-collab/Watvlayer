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
        check(Playlist.parse("https://example.com/a.ts\rhttps://example.com/b.ts", null).size()==2);
        check(Playlist.parse("#EXTINF:-1,Sem cabeçalho\nhttps://example.com/live.ts", null).get(0).name.equals("Sem cabeçalho"));
        check(Playlist.parse("# comentário\n#EXTM3U\nhttps://example.com/live.ts",null).size()==1);
        check(Playlist.parse("#EXTM3U\n#EXTINF:-1,Nome\nhttps://example.com/meu canal.ts",null).get(0).url.contains("meu%20canal"));
        String unicode="#EXTM3U\n#EXTINF:-1,Notícias\nhttps://example.com/live.ts";
        check(Playlist.read(new ByteArrayInputStream(unicode.getBytes("UTF-16"))).equals(unicode));
        check(Playlist.read(new ByteArrayInputStream(unicode.getBytes("windows-1252"))).equals(unicode));
        check(!Playlist.errorMessage(new IOException("https://secret:password@example.com")).contains("password"));
        check(Playlist.errorMessage(new java.net.SocketTimeoutException()).startsWith("TEMPO"));
        check(Playlist.errorMessage(new javax.net.ssl.SSLException("secret")).startsWith("TLS"));
        String extended="#EXTM3U\n#EXTINF:-1 tvg-id=\"news\" tvg-logo=\"https://example.com/logo.png\" group-title=\"News\",Live\n#EXTVLCOPT:http-user-agent=CustomPlayer\n#EXTVLCOPT:http-referrer=https://example.com/\nhttps://example.com/live.ts\n";
        Playlist.Channel rich=Playlist.parse(extended,null).get(0);
        check(rich.epgId.equals("news"));check(rich.logo.endsWith("logo.png"));check(rich.headers.get("User-Agent").equals("CustomPlayer"));check(rich.headers.get("Referer").equals("https://example.com/"));
        check(Playlist.parse("#EXTM3U\nhttps://example.com/live.ts|User-Agent=Custom%20Player",null).get(0).headers.get("User-Agent").equals("Custom Player"));
        check(new Playlist.Channel("Example S02E03","Séries","https://example.com/series/3.mp4").episodeOrder()==2003);
        check(new Playlist.Channel("Example S02E03","Séries","https://example.com/series/3.mp4").seriesName().equals("Example"));
        check(new Playlist.Channel("Movie","Filmes","https://example.com/movie/1.mp4").kind().equals("Filmes"));
        check(new Playlist.Channel("News","Notícias","https://example.com/live/1.ts").kind().equals("Ao vivo"));
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/list",exchange->{byte[] bytes="#EXTM3U\n#EXTINF:-1,Teste\nhttps://example.com/live.ts".getBytes("UTF-8");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});
        server.createContext("/redirect",exchange->{exchange.getResponseHeaders().add("Location","/list");exchange.sendResponseHeaders(302,-1);exchange.close();});
        server.createContext("/denied",exchange->{exchange.sendResponseHeaders(403,-1);exchange.close();});
        server.createContext("/gzip",exchange->{
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(java.util.zip.GZIPOutputStream gzip=new java.util.zip.GZIPOutputStream(out)){gzip.write(unicode.getBytes("UTF-8"));}
            byte[] bytes=out.toByteArray();
            exchange.getResponseHeaders().add("Content-Encoding","gzip");
            exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.createContext("/html",exchange->{
            byte[] bytes="<html>Login</html>".getBytes("UTF-8");
            exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.createContext("/nested/list",exchange->{byte[] bytes="#EXTM3U\n#EXTINF:-1,Relative\nchannel.ts".getBytes("UTF-8");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});
        server.createContext("/nested-redirect",exchange->{exchange.getResponseHeaders().add("Location","/nested/list");exchange.sendResponseHeaders(302,-1);exchange.close();});
        server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            check(Playlist.download(base+"/nested-redirect").get(0).url.equals(base+"/nested/channel.ts"));
            check(Playlist.download(base+"/redirect").get(0).name.equals("Teste"));
            check(Playlist.download(base+"/gzip").get(0).name.equals("Notícias"));
            boolean html=false;try{Playlist.download(base+"/html");}catch(IOException e){html=Playlist.errorMessage(e).startsWith("FORMATO");}check(html);
            boolean denied=false;try{Playlist.download(base+"/denied");}catch(IOException e){denied=e.getMessage().contains("Acesso recusado");}check(denied);
        } finally{server.stop(0);}
        System.out.println(checks+" verificações aprovadas.");
    }
}
