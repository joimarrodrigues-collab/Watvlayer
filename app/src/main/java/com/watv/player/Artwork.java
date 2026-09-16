package com.watv.player;
import android.graphics.*;
import android.widget.ImageView;
import android.util.LruCache;
import android.os.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

final class Artwork {
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(6*1024*1024){
        protected int sizeOf(String k,Bitmap b){return b.getByteCount();}
    };
    private final ThreadPoolExecutor pool=new ThreadPoolExecutor(2,2,15,TimeUnit.SECONDS,new ArrayBlockingQueue<>(40),new ThreadPoolExecutor.DiscardOldestPolicy());
    private final Handler main=new Handler(Looper.getMainLooper());
    void show(ImageView view,String url){
        view.setTag(url);view.setImageResource(com.watv.player.R.drawable.icon);
        if(url==null||url.isEmpty())return;
        Bitmap known=cache.get(url);if(known!=null){view.setImageBitmap(known);return;}
        pool.execute(()->{
            try {
                HttpURLConnection c=(HttpURLConnection)Playlist.httpUri(url).toURL().openConnection();c.setConnectTimeout(6000);c.setReadTimeout(6000);
                byte[] bytes;
                try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>2*1024*1024)return;out.write(b,0,n);}bytes=out.toByteArray();
                }finally{c.disconnect();}
                BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                options.inSampleSize=1;while(options.outWidth/options.inSampleSize>240||options.outHeight/options.inSampleSize>240)options.inSampleSize*=2;
                options.inJustDecodeBounds=false;Bitmap image=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                if(image!=null){cache.put(url,image);main.post(()->{if(url.equals(view.getTag()))view.setImageBitmap(image);});}
            }catch(Exception ignored){}
        });
    }
    void close(){pool.shutdownNow();main.removeCallbacksAndMessages(null);cache.evictAll();}
}
