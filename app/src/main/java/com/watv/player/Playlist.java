package com.watv.player;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.ByteBuffer;
import java.util.zip.GZIPInputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

public final class Playlist {
    public static final class Channel {
        public final String name, group, url, id, epgId, logo;
        public final Map<String,String> headers;
        Channel(String name, String group, String url) {
            this(name,group,url,"","",Collections.emptyMap());
        }
        Channel(String name,String group,String url,String epgId,String logo,Map<String,String> headers) {
            this.name = name; this.group = group; this.url = url; this.epgId=epgId; this.logo=logo; this.headers=new HashMap<>(headers);
            try {
                byte[] hash = MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));
                StringBuilder out = new StringBuilder();
                for (byte b : hash) out.append(String.format(Locale.ROOT, "%02x", b & 255));
                id = out.toString();
            } catch (Exception e) { throw new IllegalStateException(e); }
        }
        public String kind() {
            String path=URI.create(url).getPath().toLowerCase(Locale.ROOT);
            String g=group.toLowerCase(Locale.ROOT);
            if(path.contains("/series/") || Pattern.compile("(?i)\\bS\\d{1,2}\\s*E\\d{1,3}\\b").matcher(name).find())return "Séries";
            if(path.contains("/movie/") || path.contains("/vod/") || g.contains("filme") || path.matches(".*\\.(mp4|mkv|avi|mov)$"))return "Filmes";
            return "Ao vivo";
        }
        public String seriesName() { return name.replaceFirst("(?i)\\s*S\\d{1,2}\\s*E\\d{1,3}.*$", "").trim(); }
        public int episodeOrder() {
            Matcher m=Pattern.compile("(?i)S(\\d{1,2})\\s*E(\\d{1,3})").matcher(name);
            return m.find()?Integer.parseInt(m.group(1))*1000+Integer.parseInt(m.group(2)):0;
        }
    }
    public static final class LoadException extends IOException {
        LoadException(String message) { super(message); }
    }
    // Never surface arbitrary exception messages: they can contain credentials in URLs.
    public static String errorMessage(Exception error) {
        if (error instanceof LoadException) return error.getMessage();
        if (error instanceof java.net.SocketTimeoutException) return "TEMPO ESGOTADO: o servidor demorou para responder. Tente novamente.";
        if (error instanceof java.net.UnknownHostException) return "DNS: servidor não encontrado. Confira o endereço e a conexão.";
        if (error instanceof javax.net.ssl.SSLException) return "TLS: não foi possível validar a conexão segura. Confira data/hora do aparelho e o certificado do provedor.";
        if (error instanceof java.net.ConnectException) return "CONEXÃO: não foi possível conectar ao servidor. Confira rede e porta.";
        if (error instanceof FileNotFoundException || error instanceof SecurityException) return "ARQUIVO: não foi possível abrir o arquivo. Selecione-o novamente.";
        if (error instanceof IllegalArgumentException) return "ENDEREÇO: use uma URL HTTP/HTTPS completa e válida.";
        return "LEITURA: não foi possível ler a fonte. Confira a conexão e tente novamente.";
    }
    public static URI httpUri(String value) {
        URI uri = URI.create(value.trim().replace(" ", "%20"));
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null)
            throw new IllegalArgumentException("Informe um endereço HTTP ou HTTPS válido.");
        return uri;
    }
    public static String xtream(String server, String user, String password) {
        URI base = httpUri(server);
        if (base.getQuery() != null || base.getFragment() != null || base.getUserInfo() != null)
            throw new IllegalArgumentException("Informe apenas o endereço e a porta do servidor.");
        if (user.trim().isEmpty() || password.isEmpty()) throw new IllegalArgumentException("Preencha usuário e senha.");
        return base.toString().replaceAll("/+$", "") + "/get.php?username=" + encode(user.trim()) + "&password=" + encode(password) + "&type=m3u_plus&output=ts";
    }
    private static String encode(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (UnsupportedEncodingException e) { throw new AssertionError(e); }
    }
    public static String attribute(String line,String key) {
        Matcher m=Pattern.compile(Pattern.quote(key)+"=[\\\"']([^\\\"']*)[\\\"']",Pattern.CASE_INSENSITIVE).matcher(line);
        return m.find()?m.group(1):"";
    }
    public static List<Channel> parse(String text, String base) throws IOException {
        List<Channel> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String name = "", group = "Sem categoria", epgId="", logo="";
        Map<String,String> headers=new HashMap<>();
        String clean = text.replace("\uFEFF", "").trim();
        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.startsWith("<") || lower.startsWith("{") || lower.startsWith("[")) throw new LoadException("FORMATO: a fonte retornou uma página ou resposta de API, não uma lista M3U. Use o link direto da lista.");
        if (!(clean.startsWith("#") || lower.startsWith("http://") || lower.startsWith("https://"))) throw new LoadException("FORMATO: o conteúdo recebido não é uma lista M3U.");
        for (String raw : clean.split("\\r\\n|\\n|\\r")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXTM3U")) continue;
            if (line.startsWith("#EXT-X-")) throw new LoadException("HLS: este endereço é de um vídeo individual. Informe a lista de canais M3U.");
            if (line.startsWith("#EXTINF:")) {
                boolean quoted = false; int comma = -1;
                for (int i = 0; i < line.length(); i++) { if (line.charAt(i) == '"') quoted = !quoted; if (line.charAt(i) == ',' && !quoted) { comma = i; break; } }
                name = comma >= 0 ? line.substring(comma + 1).trim() : "";
                Matcher m = Pattern.compile("group-title=\"([^\"]*)\"").matcher(line);
                group = m.find() ? m.group(1) : "Sem categoria";
                epgId=attribute(line,"tvg-id"); logo=attribute(line,"tvg-logo");
            } else if(line.startsWith("#EXTVLCOPT:http-user-agent=")) { headers.put("User-Agent",line.substring(line.indexOf('=')+1));
            } else if(line.startsWith("#EXTVLCOPT:http-referrer=")) { headers.put("Referer",line.substring(line.indexOf('=')+1));
            } else if (line.startsWith("#EXTGRP:")) { group = line.substring(8).trim(); }
            else if (!line.startsWith("#")) {
                try {
                    String[] parts=line.split("\\|",2); line=parts[0];
                    if(parts.length>1)for(String pair:parts[1].split("&")){
                        String[] kv=pair.split("=",2);
                        if(kv.length==2 && (kv[0].equalsIgnoreCase("User-Agent") || kv[0].equalsIgnoreCase("Referer"))) headers.put(kv[0],URLDecoder.decode(kv[1],"UTF-8"));
                    }
                    String url = base == null ? httpUri(line).toString() : httpUri(URI.create(base).resolve(line.replace(" ", "%20")).toString()).toString();
                    if (seen.add(url)) result.add(new Channel(name.isEmpty() ? "Canal " + (result.size() + 1) : name, group.isEmpty() ? "Sem categoria" : group, url, epgId, logo, headers));
                } catch (IllegalArgumentException ignored) { }
                name = ""; group = "Sem categoria"; epgId=""; logo=""; headers=new HashMap<>();
            }
        }
        if (result.isEmpty()) throw new LoadException("LISTA VAZIA: nenhum canal HTTP/HTTPS válido encontrado. Confira se a conta está ativa e se o arquivo contém links completos.");
        return result;
    }
    public static String read(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n;
        while ((n = input.read(buf)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new LoadException("Carregamento cancelado.");
            if (out.size() + n > 20 * 1024 * 1024) throw new LoadException("TAMANHO: a lista excede 20 MB. Use uma lista menor do provedor.");
            out.write(buf, 0, n);
        }
        byte[] bytes = out.toByteArray();
        if (bytes.length >= 2 && ((bytes[0] == (byte)0xFF && bytes[1] == (byte)0xFE) || (bytes[0] == (byte)0xFE && bytes[1] == (byte)0xFF)))
            return new String(bytes, StandardCharsets.UTF_16);
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }
    public static final class Source {
        public final String text,url;
        Source(String text,String url){this.text=text;this.url=url;}
    }
    public static List<Channel> download(String address) throws IOException { Source s=fetch(address);return parse(s.text,s.url); }
    public static String downloadText(String address) throws IOException {return fetch(address).text;}
    public static Source fetch(String address) throws IOException {
        String current = httpUri(address).toString();
        for (int redirects = 0; redirects < 6; redirects++) {
            HttpURLConnection c = (HttpURLConnection) new URL(current).openConnection();
            c.setConnectTimeout(30000); c.setReadTimeout(60000); c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "WatvPlayer/0.2");
            c.setRequestProperty("Accept", "application/x-mpegURL, audio/x-mpegurl, text/plain, */*");
            c.setRequestProperty("Accept-Encoding", "gzip");
            try {
                int status = c.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = c.getHeaderField("Location");
                    if (location == null) throw new LoadException("REDIRECIONAMENTO: endereço de destino ausente.");
                    current = httpUri(URI.create(current).resolve(location).toString()).toString(); continue;
                }
                if (status == 401 || status == 403) throw new LoadException("HTTP " + status + ": Acesso recusado. Confira os dados e a validade da conta.");
                if (status != 200) throw new LoadException("HTTP " + status + ": o servidor não forneceu a lista. Confira o link e tente novamente.");
                try (InputStream raw = c.getInputStream();
                     PushbackInputStream peek = new PushbackInputStream(raw, 2)) {
                    int a = peek.read(), b = peek.read();
                    if (b != -1) peek.unread(b);
                    if (a != -1) peek.unread(a);
                    if (a == 0x1f && b == 0x8b) {
                        try (InputStream decoded = new GZIPInputStream(peek)) { return new Source(read(decoded),current); }
                    }
                    return new Source(read(peek),current);
                }
            } finally { c.disconnect(); }
        }
        throw new LoadException("REDIRECIONAMENTO: o servidor redirecionou muitas vezes.");
    }
}
