package com.watv.player;
import java.net.*;
import java.io.*;
import org.json.*;

final class Metadata {
    static JSONObject get(String path,String token)throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL("https://api.themoviedb.org/3/"+path).openConnection();
        c.setConnectTimeout(20000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Authorization","Bearer "+token);
        try {
            if(c.getResponseCode()!=200)throw new Playlist.LoadException("METADADOS: confira o token TMDB nas configurações ou tente novamente.");
            try(InputStream in=c.getInputStream()){return new JSONObject(Playlist.read(in));}
        }finally{c.disconnect();}
    }
    static JSONArray search(String title,boolean series,String token)throws Exception{
        return get("search/"+(series?"tv":"movie")+"?language=pt-BR&query="+URLEncoder.encode(title,"UTF-8"),token).optJSONArray("results");
    }
    static String details(int id,boolean series,String token)throws Exception{
        JSONObject d=get((series?"tv/":"movie/")+id+"?language=pt-BR&append_to_response=credits",token);
        StringBuilder s=new StringBuilder(d.optString("overview","Sinopse indisponível."));
        if(d.optJSONObject("credits")!=null){
            JSONArray cast=d.optJSONObject("credits").optJSONArray("cast");
            if(cast!=null){s.append("\n\nElenco\n");for(int i=0;i<Math.min(12,cast.length());i++){JSONObject p=cast.optJSONObject(i);s.append(p.optString("name")).append(" — ").append(p.optString("character")).append("\n");}}
        }
        return s.append("\nDados: TMDB. Este produto utiliza a API TMDB, mas não é endossado nem certificado pelo TMDB.").toString();
    }
}
