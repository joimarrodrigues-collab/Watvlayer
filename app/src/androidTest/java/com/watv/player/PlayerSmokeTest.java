package com.watv.player;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.*;
import java.util.*;
import java.io.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PlayerSmokeTest {
    @Test public void encryptedListsProfilesFiltersAndScreens()throws Exception{
        String sample="#EXTM3U\n#EXTINF:-1 tvg-id=\"news\" group-title=\"Notícias\",Canal teste\nhttps://example.com/live/1.ts\n#EXTINF:-1 group-title=\"Filmes\",Filme teste\nhttps://example.com/movie/1.mp4\n#EXTINF:-1 group-title=\"Séries\",Série teste S01E01\nhttps://example.com/series/1.mp4\n";
        android.content.Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Vault v=new Vault(ctx);v.write("fixture.enc",sample);
        assertEquals(sample,v.read("fixture.enc"));
        try(FileInputStream in=new FileInputStream(new File(ctx.getFilesDir(),"fixture.enc"))){assertFalse(Playlist.read(in).contains("https://example.com"));}
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{
                assertNotNull(a.profile);assertEquals("catalog",a.screen);
                try{JSONObject meta=new JSONObject();MainActivity.put(meta,"id","fixture");MainActivity.put(meta,"name","Lista de teste");MainActivity.put(meta,"url","");a.acceptSource(meta,sample,null);}catch(Exception e){throw new AssertionError(e);}
                a.catalog();a.section="Filmes";a.filter();assertEquals(1,a.visible.size());
                Playlist.Channel film=a.visible.get(0);a.toggle("favorites",film);a.section="Favoritos";a.filter();assertEquals(1,a.visible.size());
                a.section="Séries";a.filter();assertEquals("Série teste",a.visible.get(0).seriesName());
                a.section="Todos";a.query="Canal teste";a.filter();assertEquals(1,a.visible.size());
                MainActivity.put(a.obj(a.profile,"blocked"),"Notícias",true);MainActivity.put(a.db,"pin","test-hash");assertTrue(a.blocked(a.visible.get(0)));a.db.remove("pin");
                JSONObject other=new JSONObject();MainActivity.put(other,"id","test-profile");MainActivity.put(other,"name","Outro");JSONObject old=a.profile;a.profile=other;a.query="";a.section="Favoritos";a.filter();assertEquals(0,a.visible.size());a.profile=old;
                a.settings();a.profilesPage();a.manageLists();a.importPage();a.home();assertEquals("catalog",a.screen);
            });
        }
        try(ActivityScenario<MainActivity> reopened=ActivityScenario.launch(MainActivity.class)){
            java.util.concurrent.atomic.AtomicBoolean loaded=new java.util.concurrent.atomic.AtomicBoolean();
            long end=System.currentTimeMillis()+10000;
            while(!loaded.get()&&System.currentTimeMillis()<end){
                reopened.onActivity(a->loaded.set(a.channels.size()==3&&!a.busy));
                Thread.sleep(100);
            }
            assertTrue("A lista salva deve reabrir",loaded.get());
        }
    }
    @Test public void localPlaybackAndResume()throws Exception {
        java.net.ServerSocket server=new java.net.ServerSocket(0,10,java.net.InetAddress.getByName("127.0.0.1"));
        int frames=160000;
        java.nio.ByteBuffer wave=java.nio.ByteBuffer.allocate(44+frames*2).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        wave.put("RIFF".getBytes("US-ASCII")).putInt(36+frames*2).put("WAVEfmt ".getBytes("US-ASCII")).putInt(16).putShort((short)1).putShort((short)1).putInt(8000).putInt(16000).putShort((short)2).putShort((short)16).put("data".getBytes("US-ASCII")).putInt(frames*2);
        byte[] data=wave.array();
        Thread service=new Thread(()->{
            while(!server.isClosed()){
                try(java.net.Socket socket=server.accept()){
                    socket.setSoTimeout(3000);BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream()));String line;int start=0;
                    while((line=in.readLine())!=null&&!line.isEmpty())if(line.toLowerCase(Locale.ROOT).startsWith("range: bytes=")){String part=line.substring(13).split("-")[0];start=Integer.parseInt(part.trim());}
                    start=Math.min(start,data.length-1);
                    String header="HTTP/1.1 "+(start>0?"206 Partial Content":"200 OK")+"\r\nContent-Type: audio/wav\r\nAccept-Ranges: bytes\r\n"+(start>0?"Content-Range: bytes "+start+"-"+(data.length-1)+"/"+data.length+"\r\n":"")+"Content-Length: "+(data.length-start)+"\r\nConnection: close\r\n\r\n";
                    OutputStream out=socket.getOutputStream();out.write(header.getBytes(java.nio.charset.StandardCharsets.US_ASCII));out.write(data,start,data.length-start);out.flush();
                }catch(Exception ignored){}
            }
        });service.setDaemon(true);service.start();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            java.util.concurrent.atomic.AtomicBoolean ready=new java.util.concurrent.atomic.AtomicBoolean();
            Playlist.Channel channel=new Playlist.Channel("Teste de reprodução","Filmes","http://127.0.0.1:"+server.getLocalPort()+"/test.wav");
            scenario.onActivity(a->a.play(channel));
            long end=System.currentTimeMillis()+15000;
            while(!ready.get()&&System.currentTimeMillis()<end){
                scenario.onActivity(a->ready.set(a.player!=null&&a.player.getPlaybackState()==androidx.media3.common.Player.STATE_READY&&a.player.getCurrentPosition()>600));
                Thread.sleep(100);
            }
            assertTrue("O player deve reproduzir a fonte de teste",ready.get());
            scenario.onActivity(a->{
                a.remember();assertTrue(a.obj(a.profile,"positions").optLong(channel.id)>0);
                a.home();assertNull(a.player);
                a.play(channel);assertTrue(a.resume>0);a.home();
            });
        }finally{server.close();service.join(3000);}
    }

    @Test public void xmltvAndPinValidation()throws Exception{
        long now=System.currentTimeMillis();java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyyMMddHHmmss Z",Locale.ROOT);
        String xml="<tv><programme channel=\"news\" start=\""+f.format(new Date(now-60000))+"\" stop=\""+f.format(new Date(now+3600000))+"\"><title>Jornal</title><desc>Ao vivo</desc></programme></tv>";
        List<Epg.Programme> rows=Epg.parse(xml);assertEquals(1,rows.size());assertEquals("Jornal",rows.get(0).title);
        boolean rejected=false;try{Epg.parse("<!DOCTYPE tv [<!ENTITY x SYSTEM 'file:///x'>]><tv/>");}catch(Exception e){rejected=true;}assertTrue(rejected);
        assertEquals(Vault.pinHash("1234","salt"),Vault.pinHash("1234","salt"));
        assertNotEquals(Vault.pinHash("0000","salt"),Vault.pinHash("1234","salt"));
    }
}
