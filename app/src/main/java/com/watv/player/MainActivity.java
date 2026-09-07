package com.watv.player;
import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Playlist.Channel> channels = new ArrayList<>(), visible = new ArrayList<>();
    private Set<String> favorites;
    private LinearLayout root;
    private TextView status;
    private ListView list;
    private EditText search;
    private Spinner categories;
    private boolean onlyFavorites, loading;
    private int generation;
    private Future<?> pending;
    private ExoPlayer player;
    private PlayerView playerView;
    private Playlist.Channel playing;
    private final int bg = Color.rgb(12,18,30), accent = Color.rgb(50,213,173);
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        favorites = new HashSet<>(getSharedPreferences("watv", MODE_PRIVATE).getStringSet("favorites", Collections.emptySet()));
        login();
    }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density); }
    private LinearLayout page() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(bg); root.setPadding(dp(24),dp(16),dp(24),dp(16));
        root.setOnApplyWindowInsetsListener((v,insets)-> { v.setPadding(dp(24)+insets.getSystemWindowInsetLeft(),dp(16)+insets.getSystemWindowInsetTop(),dp(24)+insets.getSystemWindowInsetRight(),dp(16)+insets.getSystemWindowInsetBottom()); return insets; });
        setContentView(root); root.requestApplyInsets(); return root;
    }
    private TextView text(String value, int size) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(Color.WHITE); t.setPadding(0,dp(8),0,dp(8)); return t;
    }
    private void title(String subtitle) {
        TextView heading = text("WATV  PLAYER",26); heading.setTypeface(null, Typeface.BOLD); heading.setTextColor(accent); root.addView(heading);
        root.addView(text(subtitle,14));
    }
    private Button button(String label, LinearLayout parent, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setMinHeight(dp(48)); b.setOnClickListener(v->action.run()); parent.addView(b); return b;
    }
    private EditText field(String hint, LinearLayout parent, boolean secret) {
        EditText e = new EditText(this); e.setHint(hint); e.setSingleLine(true); e.setTextColor(Color.WHITE); e.setHintTextColor(Color.LTGRAY); e.setMinHeight(dp(52));
        e.setInputType(secret ? 129 : 1); e.setSaveEnabled(false); if (Build.VERSION.SDK_INT >= 26) e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        parent.addView(e); return e;
    }
    private void login() {
        page(); title("Seus canais. Na sua tela.");
        ScrollView scroll = new ScrollView(this); LinearLayout form = new LinearLayout(this); form.setOrientation(1); scroll.addView(form); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Spinner mode = new Spinner(this); mode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Lista M3U","Xtream Codes"})); form.addView(mode);
        EditText address = field("URL da lista M3U",form,false);
        EditText user = field("Usuário",form,false), password = field("Senha",form,true);
        mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> p) {}
            public void onItemSelected(AdapterView<?> p, View v, int i, long id) {
                user.setVisibility(i==1?View.VISIBLE:View.GONE); password.setVisibility(i==1?View.VISIBLE:View.GONE);
                address.setHint(i==1?"Servidor: https://servidor:porta":"URL da lista M3U");
            }
        });
        button("Conectar",form,()->{
            if (loading) return;
            try {
                String url = mode.getSelectedItemPosition()==1 ? Playlist.xtream(address.getText().toString(),user.getText().toString(),password.getText().toString()) : Playlist.httpUri(address.getText().toString()).toString();
                load(()->Playlist.download(url));
            } catch (IllegalArgumentException e) { status.setText("Confira o endereço, o usuário e a senha."); }
        });
        button("Abrir arquivo M3U",form,()->{
            if (loading) return;
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
            try { startActivityForResult(i,10); } catch (ActivityNotFoundException e) { status.setText("Este aparelho não possui seletor de arquivos. Use uma URL."); }
        });
        status = text("Escolha uma fonte para carregar seus canais.",14); form.addView(status);
        form.addView(text("Dados de acesso ficam apenas nesta sessão.",12));
    }
    interface Loader { List<Playlist.Channel> get() throws Exception; }
    private void load(Loader loader) {
        loading=true; status.setText("Carregando canais…"); final int token=++generation;
        pending=worker.submit(()->{
            try {
                List<Playlist.Channel> data=loader.get();
                runOnUiThread(()->{ if(isDestroyed() || token!=generation)return; loading=false; channels.clear(); channels.addAll(data); catalog(); });
            } catch(Exception e) {
                runOnUiThread(()->{ if(isDestroyed() || token!=generation)return; loading=false; status.setText("Não foi possível carregar. Confira a fonte, o acesso e a conexão e tente novamente."); });
            }
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==10 && result==RESULT_OK && data!=null && data.getData()!=null) {
            android.net.Uri uri=data.getData();
            load(()->{ try(InputStream in=getContentResolver().openInputStream(uri)) { if(in==null)throw new IOException(); return Playlist.parse(Playlist.read(in),null); } });
        }
    }
    private void catalog() {
        playing=null; page(); title("Canais disponíveis");
        LinearLayout bar=new LinearLayout(this); root.addView(bar);
        button("Trocar fonte",bar,()->{ channels.clear(); visible.clear(); onlyFavorites=false; login(); });
        Button fav=button(onlyFavorites?"★ Favoritos":"Todos os canais",bar,()->{});
        fav.setOnClickListener(v->{ onlyFavorites=!onlyFavorites; fav.setText(onlyFavorites?"★ Favoritos":"Todos os canais"); filter(); });
        search=field("Buscar canal",root,false);
        categories=new Spinner(this);
        List<String> groups=new ArrayList<>(); groups.add("Todas as categorias"); TreeSet<String> names=new TreeSet<>(); for(Playlist.Channel c:channels) names.add(c.group); groups.addAll(names);
        categories.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,groups)); root.addView(categories);
        status=text("",13); root.addView(status);
        list=new ListView(this); list.setChoiceMode(ListView.CHOICE_MODE_SINGLE); root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        list.setOnItemClickListener((p,v,pos,id)->play(visible.get(pos)));
        search.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s,int a,int c,int f){} public void onTextChanged(CharSequence s,int a,int b,int c){filter();} public void afterTextChanged(Editable e){} });
        categories.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() { public void onNothingSelected(AdapterView<?> p){} public void onItemSelected(AdapterView<?> p,View v,int pos,long id){filter();} });
        filter();
    }
    private void filter() {
        String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);
        String group=String.valueOf(categories.getSelectedItem());
        visible.clear(); List<String> labels=new ArrayList<>();
        for(Playlist.Channel c:channels) if(c.name.toLowerCase(Locale.ROOT).contains(q) && (categories.getSelectedItemPosition()<=0 || c.group.equals(group)) && (!onlyFavorites || favorites.contains(c.id))) {
            visible.add(c); labels.add((favorites.contains(c.id)?"★  ":"")+c.name+"\n"+c.group);
        }
        list.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,labels) {
            @Override public View getView(int position,View recycled,android.view.ViewGroup parent) {
                TextView row=(TextView)super.getView(position,recycled,parent); row.setMinHeight(dp(64)); row.setTextColor(Color.WHITE); row.setTextSize(17); return row;
            }
        });
        status.setText(visible.isEmpty()?"Nenhum canal encontrado. Ajuste a busca ou os filtros.":visible.size()+" canais • Selecione para assistir");
    }
    private void play(Playlist.Channel c) {
        release(); playing=c; page();
        root.addView(text(c.name,20));
        LinearLayout bar=new LinearLayout(this); root.addView(bar);
        button("Voltar",bar,()->{release();catalog();});
        Button fav=button(favorites.contains(c.id)?"★ Favorito":"☆ Favoritar",bar,()->{});
        fav.setOnClickListener(v->{
            if(!favorites.remove(c.id)) favorites.add(c.id);
            getSharedPreferences("watv",MODE_PRIVATE).edit().putStringSet("favorites",new HashSet<>(favorites)).apply();
            fav.setText(favorites.contains(c.id)?"★ Favorito":"☆ Favoritar");
        });
        button("Tentar novamente",bar,()->startPlayer());
        playerView=new PlayerView(this); playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        root.addView(playerView,new LinearLayout.LayoutParams(-1,0,1));
        status=text("Conectando ao canal…",13); root.addView(status);
        startPlayer();
    }
    private void startPlayer() {
        release(); if(playing==null)return;
        player=new ExoPlayer.Builder(this).build(); playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException error) { status.setText("Não foi possível reproduzir. Confira a conexão ou tente outro canal."); }
            @Override public void onPlaybackStateChanged(int state) { if(state==Player.STATE_READY)status.setText("Use OK para os controles • Voltar para os canais"); else if(state==Player.STATE_BUFFERING)status.setText("Carregando vídeo…"); }
        });
        player.setMediaItem(MediaItem.fromUri(playing.url)); player.prepare(); player.play();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); playerView.requestFocus();
    }
    private void release() {
        if(playerView!=null) playerView.setPlayer(null);
        if(player!=null){player.release();player=null;}
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if(playerView!=null && playing!=null && playerView.dispatchKeyEvent(event))return true;
        return super.dispatchKeyEvent(event);
    }
    @Override public void onBackPressed() {
        if(playing!=null){release();catalog();}
        else if(!channels.isEmpty()){channels.clear();visible.clear();login();}
        else if(loading){generation++;loading=false;if(pending!=null)pending.cancel(true);status.setText("Carregamento cancelado.");}
        else super.onBackPressed();
    }
    @Override protected void onStop(){release();super.onStop();}
    @Override protected void onStart(){super.onStart();if(playing!=null && player==null)startPlayer();}
    @Override protected void onDestroy(){generation++;worker.shutdownNow();release();super.onDestroy();}
}
