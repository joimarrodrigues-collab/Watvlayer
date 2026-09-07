package com.watv.player;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

public final class Playlist {
    public static final class Channel {
        public final String name, group, url, id;
        Channel(String name, String group, String url) {
            this.name = name; this.group = group; this.url = url;
            try {
                byte[] hash = MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));
                StringBuilder out = new StringBuilder();
                for (byte b : hash) out.append(String.format(Locale.ROOT, "%02x", b & 255));
                id = out.toString();
            } catch (Exception e) { throw new IllegalStateException(e); }
        }
    }
    public static URI httpUri(String value) {
        URI uri = URI.create(value.trim());
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
    public static List<Channel> parse(String text, String base) throws IOException {
        List<Channel> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String name = "", group = "Sem categoria";
        boolean header = false;
        for (String raw : text.replace("\uFEFF", "").split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (!header) { if (!line.startsWith("#EXTM3U")) throw new IOException("A fonte não retornou uma lista M3U válida."); header = true; continue; }
            if (line.startsWith("#EXT-X-")) throw new IOException("Este endereço é um vídeo HLS. Informe a lista de canais M3U.");
            if (line.startsWith("#EXTINF:")) {
                boolean quoted = false; int comma = -1;
                for (int i = 0; i < line.length(); i++) { if (line.charAt(i) == '"') quoted = !quoted; if (line.charAt(i) == ',' && !quoted) { comma = i; break; } }
                name = comma >= 0 ? line.substring(comma + 1).trim() : "";
                Matcher m = Pattern.compile("group-title=\"([^\"]*)\"").matcher(line);
                group = m.find() ? m.group(1) : "Sem categoria";
            } else if (line.startsWith("#EXTGRP:")) { group = line.substring(8).trim(); }
            else if (!line.startsWith("#")) {
                try {
                    String url = base == null ? httpUri(line).toString() : httpUri(URI.create(base).resolve(line).toString()).toString();
                    if (seen.add(url)) result.add(new Channel(name.isEmpty() ? "Canal " + (result.size() + 1) : name, group.isEmpty() ? "Sem categoria" : group, url));
                } catch (IllegalArgumentException ignored) { }
                name = ""; group = "Sem categoria";
            }
        }
        if (result.isEmpty()) throw new IOException("Nenhum canal HTTP/HTTPS encontrado. Verifique a fonte e o acesso.");
        return result;
    }
    public static String read(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n;
        while ((n = input.read(buf)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Carregamento cancelado.");
            if (out.size() + n > 20 * 1024 * 1024) throw new IOException("Lista maior que o limite de 20 MB.");
            out.write(buf, 0, n);
        }
        return out.toString("UTF-8");
    }
    public static List<Channel> download(String address) throws IOException {
        String current = httpUri(address).toString();
        for (int redirects = 0; redirects < 6; redirects++) {
            HttpURLConnection c = (HttpURLConnection) new URL(current).openConnection();
            c.setConnectTimeout(15000); c.setReadTimeout(20000); c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "WatvPlayer/0.1");
            try {
                int status = c.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = c.getHeaderField("Location");
                    if (location == null) throw new IOException("Redirecionamento inválido.");
                    current = httpUri(URI.create(current).resolve(location).toString()).toString(); continue;
                }
                if (status == 401 || status == 403) throw new IOException("Acesso recusado. Confira os dados e a validade da conta.");
                if (status != 200) throw new IOException("Servidor indisponível (HTTP " + status + ").");
                try (InputStream in = c.getInputStream()) { return parse(read(in), current); }
            } finally { c.disconnect(); }
        }
        throw new IOException("O servidor redirecionou muitas vezes.");
    }
}
