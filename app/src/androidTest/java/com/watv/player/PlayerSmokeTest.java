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
                try{a.channels.clear();a.channels.addAll(Playlist.parse(sample,null));}catch(Exception e){throw new AssertionError(e);}
                a.catalog();a.section="Filmes";a.filter();assertEquals(1,a.visible.size());
                Playlist.Channel film=a.visible.get(0);a.toggle("favorites",film);a.section="Favoritos";a.filter();assertEquals(1,a.visible.size());
                a.section="Séries";a.filter();assertEquals("Série teste",a.visible.get(0).seriesName());
                a.section="Todos";a.query="Canal teste";a.filter();assertEquals(1,a.visible.size());
                MainActivity.put(a.obj(a.profile,"blocked"),"Notícias",true);MainActivity.put(a.db,"pin","test-hash");assertTrue(a.blocked(a.visible.get(0)));a.db.remove("pin");
                JSONObject other=new JSONObject();MainActivity.put(other,"id","test-profile");MainActivity.put(other,"name","Outro");JSONObject old=a.profile;a.profile=other;a.query="";a.section="Favoritos";a.filter();assertEquals(0,a.visible.size());a.profile=old;
                a.settings();a.profilesPage();a.manageLists();a.importPage();a.home();assertEquals("catalog",a.screen);
            });
        }
        v.remove("fixture.enc");
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
