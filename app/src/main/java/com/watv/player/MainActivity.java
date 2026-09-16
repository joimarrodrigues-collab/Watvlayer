package com.watv.player;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.res.Configuration;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.*;
import android.view.*;
import android.widget.*;
import android.net.Uri;
import android.util.Rational;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.AspectRatioFrameLayout;

public class MainActivity extends Activity {
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    final Handler handler=new Handler(Looper.getMainLooper());
    final List<Playlist.Channel> channels=new ArrayList<>(),visible=new ArrayList<>();
    List<Epg.Programme> guide=new ArrayList<>();
    final Artwork artwork=new Artwork();
    Vault vault; JSONObject db,profile; JSONArray lists,profiles;
    String activeList="",section="Todos",query="",category="",customFilter="",seriesFilter="",screen="home";
    boolean busy=false,full=false; int generation=0,sort=0; long resume=0;
    LinearLayout root,toolbar; TextView status; ListView listView; EditText search; Spinner groups;
    ExoPlayer player; PlayerView video; Playlist.Channel playing;
    final int bg=Color.rgb(12,18,30),accent=Color.rgb(50,213,173);
    final Runnable ticker=new Runnable(){public void run(){if(player!=null){remember();handler.postDelayed(this,10000);}}};
    @Override public void onCreate(Bundle b){
        super.onCreate(b);vault=new Vault(this);
        try{
            String stored=vault.read("state.enc"); db=stored==null?new JSONObject():new JSONObject(stored);
            lists=db.optJSONArray("lists");if(lists==null)lists=new JSONArray();profiles=db.optJSONArray("profiles");if(profiles==null)profiles=new JSONArray();
            if(profiles.length()==0){JSONObject p=new JSONObject();put(p,"id",UUID.randomUUID().toString());put(p,"name","Principal");put(p,"avatar","▶");profiles.put(p);}
            String pid=db.optString("profile");profile=profiles.getJSONObject(0);
            for(int i=0;i<profiles.length();i++)if(profiles.getJSONObject(i).optString("id").equals(pid))profile=profiles.getJSONObject(i);
            activeList=db.optString("activeList");save();home();
            if(!activeList.isEmpty())loadSaved(activeList);
        }catch(Exception e){new AlertDialog.Builder(this).setTitle("Dados locais indisponíveis").setMessage("Não foi possível abrir os dados salvos. Feche e abra o app novamente.").setPositiveButton("Fechar",(d,w)->finish()).show();}
    }
    static void put(JSONObject o,String k,Object v){Vault.put(o,k,v);}
    JSONObject obj(JSONObject o,String k){return Vault.object(o,k);}
    void save(){
        put(db,"lists",lists);put(db,"profiles",profiles);put(db,"profile",profile.optString("id"));put(db,"activeList",activeList);
        final String snapshot=db.toString();
        worker.submit(()->{try{vault.write("state.enc",snapshot);}catch(Exception e){runOnUiThread(()->toast("Não foi possível salvar os dados locais."));}});
    }
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setPadding(dp(4),dp(8),dp(4),dp(8));return t;}
    void page(String sub){
        screen=sub;root=column();root.setBackgroundColor(bg);root.setPadding(dp(16),dp(12),dp(16),dp(12));
        root.setOnApplyWindowInsetsListener((v,in)->{v.setPadding(dp(16)+in.getSystemWindowInsetLeft(),dp(12)+in.getSystemWindowInsetTop(),dp(16)+in.getSystemWindowInsetRight(),dp(12)+in.getSystemWindowInsetBottom());return in;});
        setContentView(root);root.requestApplyInsets();
        TextView title=text("WATV  PLAYER",24);title.setTypeface(null,Typeface.BOLD);title.setTextColor(accent);root.addView(title);
        root.addView(text(sub,14));
    }
    LinearLayout bar(LinearLayout parent){
        HorizontalScrollView scroll=new HorizontalScrollView(this);LinearLayout row=new LinearLayout(this);scroll.addView(row);parent.addView(scroll);return row;
    }
    Button button(LinearLayout l,String label,Runnable r){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setMinHeight(dp(48));b.setOnClickListener(v->r.run());l.addView(b);return b;}
    EditText input(LinearLayout l,String hint,String value,int type){
        EditText e=new EditText(this);e.setText(value);e.setHint(hint);e.setTextColor(Color.WHITE);e.setHintTextColor(Color.LTGRAY);e.setSingleLine();e.setInputType(type);e.setSaveEnabled(false);
        if(Build.VERSION.SDK_INT>=26)e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        l.addView(e);return e;
    }
    LinearLayout scrollBody(){ScrollView s=new ScrollView(this);LinearLayout l=column();s.addView(l);root.addView(s,new LinearLayout.LayoutParams(-1,0,1));return l;}
    void choices(String title,String[] labels,java.util.function.IntConsumer action){new AlertDialog.Builder(this).setTitle(title).setItems(labels,(d,i)->action.accept(i)).setNegativeButton("Cancelar",null).show();}
    interface TextAction{void run(String value);}
    void ask(String title,String old,TextAction action){
        LinearLayout l=column();l.setPadding(dp(20),dp(8),dp(20),dp(8));EditText e=input(l,title,old,1);
        new AlertDialog.Builder(this).setTitle(title).setView(l).setPositiveButton("Salvar",(d,w)->{String v=e.getText().toString().trim();if(!v.isEmpty())action.run(v);}).setNegativeButton("Cancelar",null).show();
    }
    JSONObject listMeta(String id){for(int i=0;i<lists.length();i++){JSONObject x=lists.optJSONObject(i);if(x!=null&&x.optString("id").equals(id))return x;}return null;}
    String listName(){JSONObject x=listMeta(activeList);return x==null?"Nenhuma lista ativa":x.optString("name");}
    interface Task{void run() throws Exception;}
    void task(String message,Task work){
        if(busy){toast("Aguarde a operação atual.");return;}busy=true;int token=++generation;
        ProgressDialog progress=new ProgressDialog(this);progress.setMessage(message);progress.setCancelable(false);progress.show();
        worker.submit(()->{try{work.run();}catch(Exception e){runOnUiThread(()->{if(!isDestroyed())new AlertDialog.Builder(this).setTitle("Não foi possível concluir").setMessage(Playlist.errorMessage(e)).setPositiveButton("OK",null).show();});}
        finally{runOnUiThread(()->{if(!isDestroyed())progress.dismiss();if(token==generation)busy=false;});}});
    }
    void acceptSource(JSONObject meta,String content,String base)throws Exception{
        List<Playlist.Channel> parsed=Playlist.parse(content,base);
        vault.write(meta.optString("id")+".enc",content);put(meta,"base",base==null?"":base);put(meta,"updated",System.currentTimeMillis());
        runOnUiThread(()->{
            if(isDestroyed())return;
            if(listMeta(meta.optString("id"))==null)lists.put(meta);
            activeList=meta.optString("id");channels.clear();channels.addAll(parsed);guide.clear();save();home();
        });
    }
    void loadSaved(String id){
        JSONObject meta=listMeta(id);if(meta==null)return;
        task("Abrindo lista salva…",()->{
            String content=vault.read(id+".enc");
            if(content==null)throw new FileNotFoundException();
            List<Playlist.Channel> parsed=Playlist.parse(content,meta.optString("base").isEmpty()?null:meta.optString("base"));
            runOnUiThread(()->{activeList=id;channels.clear();channels.addAll(parsed);guide.clear();save();home();});
        });
    }
    void importPage(){
        stopPlayer();page("Adicionar lista • M3U ou Xtream Codes");LinearLayout l=scrollBody();
        EditText name=input(l,"Nome da lista","",1);Spinner mode=new Spinner(this);mode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"URL M3U","Xtream Codes"}));l.addView(mode);
        EditText address=input(l,"URL completa / servidor","",17),user=input(l,"Usuário Xtream","",1),pass=input(l,"Senha Xtream","",129),epg=input(l,"URL XMLTV / XML.GZ (opcional)","",17);
        user.setVisibility(View.GONE);pass.setVisibility(View.GONE);
        mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int i,long id){user.setVisibility(i==1?View.VISIBLE:View.GONE);pass.setVisibility(i==1?View.VISIBLE:View.GONE);}});
        button(l,"Importar e salvar",()->{
            try{
                String url=mode.getSelectedItemPosition()==1?Playlist.xtream(address.getText().toString(),user.getText().toString(),pass.getText().toString()):Playlist.httpUri(address.getText().toString()).toString();
                JSONObject meta=new JSONObject();put(meta,"id",UUID.randomUUID().toString());put(meta,"name",name.getText().toString().trim().isEmpty()?"Minha lista":name.getText().toString().trim());put(meta,"url",url);put(meta,"epg",epg.getText().toString().trim());
                task("Baixando e verificando canais…",()->{Playlist.Source source=Playlist.fetch(url);if(meta.optString("epg").isEmpty())put(meta,"epg",Playlist.attribute(source.text.split("\n",2)[0],"url-tvg"));acceptSource(meta,source.text,source.url);});
            }catch(Exception e){toast(Playlist.errorMessage(e));}
        });
        button(l,"Importar arquivo M3U",()->{try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),10);}catch(Exception e){toast("Use a URL: este aparelho não tem seletor de arquivos.");}});
        l.addView(text("Listas e dados de acesso são salvos criptografados neste aparelho.",13));button(l,"Voltar",this::home);
    }
    @Override protected void onActivityResult(int code,int result,Intent data){
        super.onActivityResult(code,result,data);
        if(code==10 && result==RESULT_OK && data!=null && data.getData()!=null){
            Uri uri=data.getData();ask("Nome da lista","Lista importada",name->task("Lendo arquivo…",()->{
                try(InputStream in=getContentResolver().openInputStream(uri)){
                    if(in==null)throw new FileNotFoundException();JSONObject meta=new JSONObject();put(meta,"id",UUID.randomUUID().toString());put(meta,"name",name);put(meta,"url","");put(meta,"epg","");acceptSource(meta,Playlist.read(in),null);
                }
            }));
        }
    }
    void manageLists(){
        stopPlayer();page("Gerenciar listas");LinearLayout l=scrollBody();button(l,"Adicionar lista",this::importPage);
        for(int i=0;i<lists.length();i++){JSONObject m=lists.optJSONObject(i);if(m==null)continue;button(l,(activeList.equals(m.optString("id"))?"● ":"")+m.optString("name"),()->choices(m.optString("name"),new String[]{"Ativar","Atualizar canais","Renomear","Editar URL","Configurar EPG","Remover"},a->{
            if(a==0)loadSaved(m.optString("id"));
            if(a==1)refreshList(m);
            if(a==2)ask("Nome",m.optString("name"),v->{put(m,"name",v);save();manageLists();});
            if(a==3){if(m.optString("url").isEmpty())toast("Listas de arquivo devem ser importadas novamente.");else ask("URL M3U",m.optString("url"),v->{try{Playlist.httpUri(v);put(m,"url",v);save();}catch(Exception e){toast("URL inválida.");}});}
            if(a==4)ask("URL XMLTV / XML.GZ",m.optString("epg"),v->{try{Playlist.httpUri(v);put(m,"epg",v);guide.clear();save();}catch(Exception e){toast("URL inválida.");}});
            if(a==5)new AlertDialog.Builder(this).setTitle("Remover lista?").setMessage("Os canais desta lista serão removidos do aparelho.").setPositiveButton("Remover",(d,w)->{for(int j=0;j<lists.length();j++)if(lists.optJSONObject(j)==m){lists.remove(j);break;}vault.remove(m.optString("id")+".enc");if(activeList.equals(m.optString("id"))){activeList="";channels.clear();}save();manageLists();}).setNegativeButton("Cancelar",null).show();
        }));}
        button(l,"Voltar",this::home);
    }
    void refreshList(JSONObject m){
        if(m==null)return;if(m.optString("url").isEmpty()){toast("Importe novamente o arquivo para atualizar.");return;}
        JSONObject copy;try{copy=new JSONObject(m.toString());}catch(Exception e){return;}
        task("Atualizando lista…",()->{Playlist.Source s=Playlist.fetch(copy.optString("url"));List<Playlist.Channel> parsed=Playlist.parse(s.text,s.url);vault.write(copy.optString("id")+".enc",s.text);
            runOnUiThread(()->{put(m,"base",s.url);put(m,"updated",System.currentTimeMillis());activeList=m.optString("id");channels.clear();channels.addAll(parsed);guide.clear();save();home();});
        });
    }
    void home(){
        stopPlayer();section="Todos";category="";query="";customFilter="";seriesFilter="";catalog();
    }
    void catalog(){
        page(profile.optString("avatar","▶")+" "+profile.optString("name")+" • "+listName());screen="catalog";
        LinearLayout nav=bar(root);button(nav,"Listas",()->guardAdmin(this::manageLists));button(nav,"Perfis",()->guardAdmin(this::profilesPage));button(nav,"Configurações",()->guardAdmin(this::settings));button(nav,"Atualizar",()->refreshList(listMeta(activeList)));
        LinearLayout tabs=bar(root);
        for(String s:new String[]{"Todos","Ao vivo","Filmes","Séries","Favoritos","Minha lista","Histórico","Continuar"})button(tabs,s,()->{section=s;seriesFilter="";customFilter="";filter();});
        button(tabs,"Coleções",this::collections);
        search=input(root,"Buscar por título",query,1);
        LinearLayout tools=bar(root);button(tools,"Ordenar",()->choices("Ordenar",new String[]{"Ordem da lista","Nome A–Z","Nome Z–A","Mais assistidos","Assistidos recentemente"},i->{sort=i;put(profile,"sort",sort);save();filter();}));
        button(tools,"Limpar filtros",()->{section="Todos";customFilter="";seriesFilter="";groups.setSelection(0);search.setText("");filter();});
        groups=new Spinner(this);List<String> cats=new ArrayList<>();cats.add("Todas as categorias");TreeSet<String> cs=new TreeSet<>();for(Playlist.Channel c:channels)cs.add(c.group);cats.addAll(cs);
        groups.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,cats));root.addView(groups);
        status=text("",13);root.addView(status);listView=new ListView(this);root.addView(listView,new LinearLayout.LayoutParams(-1,0,1));
        listView.setOnItemClickListener((p,v,i,id)->{Playlist.Channel c=visible.get(i);if(section.equals("Séries")&&seriesFilter.isEmpty()){seriesFilter=c.seriesName();filter();}else detail(c);});
        listView.setOnItemLongClickListener((p,v,i,id)->{detail(visible.get(i));return true;});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){query=s.toString();filter();}});
        groups.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int i,long id){category=i==0?"":cats.get(i);filter();}});
        sort=profile.optInt("sort");filter();
        if(channels.isEmpty())button(root,"Importar sua lista",this::importPage);
    }
    void filter(){
        if(listView==null)return;visible.clear();String q=query.toLowerCase(Locale.ROOT);JSONObject fav=obj(profile,"favorites"),watch=obj(profile,"watchlist"),history=obj(profile,"history"),positions=obj(profile,"positions");Set<String> seriesSeen=new HashSet<>();
        for(Playlist.Channel c:channels){
            if(!c.name.toLowerCase(Locale.ROOT).contains(q)||(!category.isEmpty()&&!c.group.equals(category)))continue;
            if(!customFilter.isEmpty()&&!obj(obj(profile,"collections"),customFilter).optBoolean(c.id))continue;
            if(!seriesFilter.isEmpty()&&!c.seriesName().equals(seriesFilter))continue;
            if(section.equals("Favoritos")&&!fav.optBoolean(c.id))continue;
            if(section.equals("Minha lista")&&!watch.optBoolean(c.id))continue;
            if(section.equals("Histórico")&&!history.has(c.id))continue;
            if(section.equals("Continuar")&&(positions.optLong(c.id)<=0||c.kind().equals("Ao vivo")))continue;
            if(Arrays.asList("Ao vivo","Filmes","Séries").contains(section)&&!c.kind().equals(section))continue;
            if(section.equals("Séries")&&seriesFilter.isEmpty()&&!seriesSeen.add(c.seriesName()))continue;
            visible.add(c);
        }
        if(sort==1||sort==2)visible.sort((a,b)->(sort==1?1:-1)*a.name.compareToIgnoreCase(b.name));
        if(sort==3)visible.sort((a,b)->Integer.compare(obj(profile,"counts").optInt(b.id),obj(profile,"counts").optInt(a.id)));
        if(sort==4||section.equals("Histórico"))visible.sort((a,b)->Long.compare(history.optLong(b.id),history.optLong(a.id)));
        if(!seriesFilter.isEmpty())visible.sort(Comparator.comparingInt(Playlist.Channel::episodeOrder));
        List<String> labels=new ArrayList<>();
        for(Playlist.Channel c:visible){boolean series=section.equals("Séries")&&seriesFilter.isEmpty();labels.add((fav.optBoolean(c.id)?"★ ":"")+(blocked(c)?"🔒 ":"")+(series?c.seriesName():c.name)+"\n"+c.group+" • "+c.kind());}
        listView.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,labels){@Override public View getView(int pos,View view,android.view.ViewGroup parent){LinearLayout row;ImageView image;TextView title;
            if(view instanceof LinearLayout){row=(LinearLayout)view;image=(ImageView)row.getChildAt(0);title=(TextView)row.getChildAt(1);}
            else{row=new LinearLayout(MainActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(4),dp(4),dp(4),dp(4));image=new ImageView(MainActivity.this);image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);row.addView(image,new LinearLayout.LayoutParams(dp(52),dp(64)));title=text("",17);row.addView(title,new LinearLayout.LayoutParams(0,-2,1));}
            title.setText(labels.get(pos));artwork.show(image,visible.get(pos).logo);return row;}});
        status.setText(section+(seriesFilter.isEmpty()?"":" • "+seriesFilter)+" • "+visible.size()+" itens");
    }
    boolean blocked(Playlist.Channel c){return !db.optString("pin").isEmpty() && obj(profile,"blocked").optBoolean(c.group);}
    void unlock(Playlist.Channel c,Runnable r){if(blocked(c))pinPrompt(r);else r.run();}
    void guardAdmin(Runnable r){if(db.optString("pin").isEmpty())r.run();else pinPrompt(r);}
    void pinPrompt(Runnable r){
        if(System.currentTimeMillis()<db.optLong("pinUntil")){toast("Aguarde um minuto antes de tentar novamente.");return;}
        LinearLayout l=column();EditText e=input(l,"PIN de 4 dígitos","",18);
        new AlertDialog.Builder(this).setTitle("Controle parental").setView(l).setPositiveButton("Desbloquear",(d,w)->{
            try{if(Vault.pinHash(e.getText().toString(),db.optString("salt")).equals(db.optString("pin"))){put(db,"pinFails",0);save();r.run();}
            else {int n=db.optInt("pinFails")+1;put(db,"pinFails",n);if(n>=5){put(db,"pinUntil",System.currentTimeMillis()+60000);put(db,"pinFails",0);}save();toast("PIN incorreto.");}}catch(Exception ex){toast("Não foi possível validar o PIN.");}
        }).setNegativeButton("Cancelar",null).show();
    }
    void toggle(String field,Playlist.Channel c){JSONObject o=obj(profile,field);put(o,c.id,!o.optBoolean(c.id));save();if(screen.equals("catalog"))filter();}
    void detail(Playlist.Channel c){unlock(c,()->{
        String label=c.kind().equals("Séries")?"Episódio":"Conteúdo";
        new AlertDialog.Builder(this).setTitle(c.name).setItems(new String[]{"▶ Assistir",obj(profile,"favorites").optBoolean(c.id)?"Remover favorito":"Adicionar favorito",obj(profile,"watchlist").optBoolean(c.id)?"Remover da minha lista":"Adicionar à minha lista","Adicionar à coleção","Programação EPG","Sinopse e elenco (TMDB)",label+": "+c.kind()+" • "+c.group},(d,i)->{
            if(i==0)play(c);if(i==1)toggle("favorites",c);if(i==2)toggle("watchlist",c);if(i==3)addCollection(c);if(i==4)showEpg(c);if(i==5)metadata(c);
        }).setNegativeButton("Fechar",null).show();
    });}
    void metadata(Playlist.Channel c){
        if(c.kind().equals("Ao vivo")){toast("Metadados disponíveis para filmes e séries.");return;}
        String token=db.optString("tmdb");
        if(token.isEmpty()){toast("Configure seu token de leitura TMDB em Configurações.");return;}
        ask("Pesquisar título",c.seriesName(),title->task("Buscando metadados…",()->{
            JSONArray found=Metadata.search(title,c.kind().equals("Séries"),token);
            if(found==null||found.length()==0)throw new Playlist.LoadException("METADADOS: nenhum título encontrado.");
            List<String> labels=new ArrayList<>();for(int i=0;i<found.length();i++){JSONObject d=found.optJSONObject(i);labels.add(d.optString("title",d.optString("name"))+" • "+d.optString("release_date",d.optString("first_air_date")));}
            runOnUiThread(()->choices("Selecione o título",labels.toArray(new String[0]),i->task("Carregando detalhes…",()->{
                String details=Metadata.details(found.optJSONObject(i).optInt("id"),c.kind().equals("Séries"),token);
                runOnUiThread(()->new AlertDialog.Builder(this).setTitle(labels.get(i)).setMessage(details).setPositiveButton("Fechar",null).show());
            })));
        }));
    }
    void collections(){JSONObject all=obj(profile,"collections");List<String> keys=new ArrayList<>();all.keys().forEachRemaining(keys::add);if(keys.isEmpty()){toast("Abra um conteúdo e escolha Adicionar à coleção.");return;}
        choices("Coleções",keys.toArray(new String[0]),i->{customFilter=keys.get(i);section="Todos";filter();});
    }
    void addCollection(Playlist.Channel c){JSONObject all=obj(profile,"collections");List<String> keys=new ArrayList<>();keys.add("+ Nova coleção");all.keys().forEachRemaining(keys::add);
        choices("Adicionar à coleção",keys.toArray(new String[0]),i->{if(i==0)ask("Nome da coleção","",v->{put(obj(all,v),c.id,true);save();});else{put(obj(all,keys.get(i)),c.id,true);save();toast("Adicionado.");}});
    }
    void profilesPage(){
        stopPlayer();page("Perfis");LinearLayout l=scrollBody();
        for(int i=0;i<profiles.length();i++){JSONObject p=profiles.optJSONObject(i);button(l,p.optString("avatar","▶")+" "+p.optString("name"),()->choices(p.optString("name"),new String[]{"Usar perfil","Renomear","Avatar","Remover"},a->{
            if(a==0){profile=p;save();home();}if(a==1)ask("Nome",p.optString("name"),v->{put(p,"name",v);save();profilesPage();});
            if(a==2)choices("Avatar",new String[]{"▶","⭐","🎬","🌊","🚀","🌿"},j->{put(p,"avatar",new String[]{"▶","⭐","🎬","🌊","🚀","🌿"}[j]);save();profilesPage();});
            if(a==3){if(profiles.length()==1){toast("Mantenha ao menos um perfil.");return;}new AlertDialog.Builder(this).setTitle("Remover perfil e seu histórico?").setPositiveButton("Remover",(d,w)->{for(int j=0;j<profiles.length();j++)if(profiles.optJSONObject(j)==p){profiles.remove(j);break;}if(profile==p)profile=profiles.optJSONObject(0);save();profilesPage();}).setNegativeButton("Cancelar",null).show();}
        }));}
        button(l,"Novo perfil",()->ask("Nome","",v->{JSONObject p=new JSONObject();put(p,"id",UUID.randomUUID().toString());put(p,"name",v);put(p,"avatar","▶");profiles.put(p);save();profilesPage();}));button(l,"Voltar",this::home);
    }
    void settings(){
        stopPlayer();page("Configurações • v0.3");LinearLayout l=scrollBody();
        button(l,"Configurar token de leitura TMDB",()->{
            LinearLayout box=column();EditText e=input(box,"API Read Access Token","",129);
            new AlertDialog.Builder(this).setTitle("TMDB — conta própria").setMessage("Token usado somente em api.themoviedb.org para buscar sinopse e elenco. Deixe vazio para remover.").setView(box).setPositiveButton("Salvar",(d,w)->{put(db,"tmdb",e.getText().toString().trim());save();}).setNegativeButton("Cancelar",null).show();
        });
        button(l,"Idioma preferido do áudio",()->choices("Áudio",new String[]{"Português","English","Español","Automático"},i->{put(profile,"audio",new String[]{"pt","en","es",""}[i]);save();}));
        button(l,"Qualidade preferida",()->qualityDialog(false));
        button(l,"Criar ou alterar PIN",()->{
            LinearLayout box=column();EditText a=input(box,"4 dígitos","",18),b=input(box,"Repita o PIN","",18);
            new AlertDialog.Builder(this).setTitle("PIN do responsável").setView(box).setPositiveButton("Salvar",(d,w)->{
                String pin=a.getText().toString();if(!pin.matches("\\d{4}")||!pin.equals(b.getText().toString())){toast("Use quatro dígitos iguais nos dois campos.");return;}
                try{String salt=UUID.randomUUID().toString();put(db,"salt",salt);put(db,"pin",Vault.pinHash(pin,salt));save();toast("PIN salvo.");}catch(Exception e){toast("Não foi possível salvar o PIN.");}
            }).setNegativeButton("Cancelar",null).show();
        });
        button(l,"Bloquear categorias deste perfil",()->{
            if(db.optString("pin").isEmpty()){toast("Crie primeiro o PIN.");return;}TreeSet<String> set=new TreeSet<>();for(Playlist.Channel c:channels)set.add(c.group);String[] values=set.toArray(new String[0]);boolean[] checked=new boolean[values.length];JSONObject blocked=obj(profile,"blocked");
            for(int i=0;i<values.length;i++)checked[i]=blocked.optBoolean(values[i]);
            new AlertDialog.Builder(this).setTitle("Categorias bloqueadas").setMultiChoiceItems(values,checked,(d,i,v)->checked[i]=v).setPositiveButton("Salvar",(d,w)->{for(int i=0;i<values.length;i++)put(blocked,values[i],checked[i]);save();}).setNegativeButton("Cancelar",null).show();
        });
        button(l,"Desativar controle parental",()->{db.remove("pin");save();toast("Controle parental desativado.");});
        button(l,"Limpar histórico e progresso",()->new AlertDialog.Builder(this).setTitle("Limpar histórico deste perfil?").setPositiveButton("Limpar",(d,w)->{profile.remove("history");profile.remove("positions");profile.remove("counts");save();}).setNegativeButton("Cancelar",null).show());
        l.addView(text("Fontes e preferências ficam criptografadas neste aparelho. A ativação remota e os metadados externos precisam de serviços configurados.",14));
        button(l,"Voltar",this::home);
    }
    void qualityDialog(boolean apply){
        choices("Limite de qualidade",new String[]{"Automática / adaptativa","Até 480p","Até 720p","Até 1080p","Até 2160p"},i->{put(profile,"height",new int[]{0,480,720,1080,2160}[i]);save();if(apply&&player!=null)applyPreferences();});
    }
    void applyPreferences(){
        if(player==null)return;int h=profile.optInt("height");TrackSelectionParameters.Builder b=player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).setMaxVideoSize(Integer.MAX_VALUE,h==0?Integer.MAX_VALUE:h);
        String lang=profile.optString("audio");if(!lang.isEmpty())b.setPreferredAudioLanguage(lang);
        player.setTrackSelectionParameters(b.build());
    }
    void showEpg(Playlist.Channel c){
        if(c.epgId.isEmpty()){toast("Este canal não tem tvg-id na lista.");return;}JSONObject m=listMeta(activeList);if(m==null||m.optString("epg").isEmpty()){toast("Configure a URL XMLTV em Listas → Configurar EPG.");return;}
        if(!guide.isEmpty()){displayEpg(c);return;}
        task("Carregando programação XMLTV…",()->{List<Epg.Programme> parsed=Epg.parse(Playlist.downloadText(m.optString("epg")));runOnUiThread(()->{guide=parsed;displayEpg(c);});});
    }
    void displayEpg(Playlist.Channel c){List<String> labels=new ArrayList<>();for(Epg.Programme p:guide)if(p.channel.equals(c.epgId))labels.add(p.label());new AlertDialog.Builder(this).setTitle(c.name+" • Programação").setItems(labels.isEmpty()?new String[]{"Sem programação para este canal."}:labels.toArray(new String[0]),null).setPositiveButton("Fechar",null).show();}
    void play(Playlist.Channel c){
        stopPlayer();playing=c;resume=obj(profile,"positions").optLong(c.id);
        put(obj(profile,"history"),c.id,System.currentTimeMillis());put(obj(profile,"counts"),c.id,obj(profile,"counts").optInt(c.id)+1);save();
        page(c.name);screen="player";toolbar=bar(root);
        button(toolbar,"Voltar",this::home);button(toolbar,"★",()->{toggle("favorites",c);toast("Favoritos atualizados.");});
        button(toolbar,"Tela cheia",this::fullScreen);button(toolbar,"Mini player",this::pip);
        button(toolbar,"EPG",()->showEpg(c));button(toolbar,"Opções",this::playerOptions);
        video=new PlayerView(this);video.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);root.addView(video,new LinearLayout.LayoutParams(-1,0,1));
        status=text("Conectando…",13);root.addView(status);startPlayer();
    }
    void startPlayer(){
        if(playing==null)return;
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent("WatvPlayer/0.3").setConnectTimeoutMs(30000).setReadTimeoutMs(60000).setAllowCrossProtocolRedirects(true).setDefaultRequestProperties(playing.headers);
        player=new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(http)).build();video.setPlayer(player);applyPreferences();
        player.addListener(new Player.Listener(){
            @Override public void onPlaybackStateChanged(int state){
                if(state==Player.STATE_READY)status.setText("OK: controles • Voltar: canais");
                if(state==Player.STATE_BUFFERING)status.setText("Carregando vídeo…");
                if(state==Player.STATE_ENDED){obj(profile,"positions").remove(playing.id);save();nextEpisode();}
            }
            @Override public void onPlayerError(PlaybackException e){status.setText("Falha de reprodução ("+e.getErrorCodeName()+"). Use Opções → Tentar novamente.");}
        });
        player.setMediaItem(MediaItem.fromUri(playing.url));player.prepare();if(!playing.kind().equals("Ao vivo")&&resume>0)player.seekTo(resume);player.play();video.requestFocus();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);handler.removeCallbacks(ticker);handler.postDelayed(ticker,10000);
    }
    void remember(){if(player==null||playing==null||playing.kind().equals("Ao vivo"))return;long duration=player.getDuration(),pos=player.getCurrentPosition();resume=pos;
        if(duration>0&&duration-pos<10000){obj(profile,"positions").remove(playing.id);resume=0;}else if(pos>0)put(obj(profile,"positions"),playing.id,pos);save();}
    void release(){handler.removeCallbacks(ticker);remember();if(video!=null)video.setPlayer(null);if(player!=null){player.release();player=null;}getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    void stopPlayer(){release();playing=null;video=null;full=false;getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);}
    void fullScreen(){full=!full;for(int i=0;i<root.getChildCount();i++){View v=root.getChildAt(i);if(v!=video)v.setVisibility(full?View.GONE:View.VISIBLE);}getWindow().getDecorView().setSystemUiVisibility(full?View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY:View.SYSTEM_UI_FLAG_VISIBLE);}
    void pip(){
        if(Build.VERSION.SDK_INT>=26&&getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)){
            try{enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(16,9)).build());}catch(Exception e){toast("Mini player indisponível neste aparelho.");}
        }else toast("Mini player requer suporte do Android 8 ou superior.");
    }
    @Override public void onPictureInPictureModeChanged(boolean isPip,Configuration config){super.onPictureInPictureModeChanged(isPip,config);if(video==null)return;video.setUseController(!isPip);for(int i=0;i<root.getChildCount();i++){View v=root.getChildAt(i);if(v!=video)v.setVisibility(isPip||full?View.GONE:View.VISIBLE);}}
    void playerOptions(){choices("Reprodução",new String[]{"Qualidade","Faixa de áudio","Legendas","Velocidade","Ajustar imagem","Outras fontes do canal","Próximo episódio","Tentar novamente"},i->{
        if(i==0)qualityDialog(true);if(i==1)tracks(C.TRACK_TYPE_AUDIO);if(i==2)tracks(C.TRACK_TYPE_TEXT);
        if(i==3)choices("Velocidade",new String[]{"0,5×","0,75×","1×","1,25×","1,5×","2×"},j->{if(player!=null)player.setPlaybackSpeed(new float[]{.5f,.75f,1,1.25f,1.5f,2}[j]);});
        if(i==4)choices("Imagem",new String[]{"Ajustar","Preencher","Zoom"},j->video.setResizeMode(new int[]{AspectRatioFrameLayout.RESIZE_MODE_FIT,AspectRatioFrameLayout.RESIZE_MODE_FILL,AspectRatioFrameLayout.RESIZE_MODE_ZOOM}[j]));
        if(i==5)alternatives();if(i==6)nextEpisode();if(i==7){release();startPlayer();}
    });}
    void tracks(int type){
        if(player==null)return;List<String> names=new ArrayList<>();List<TrackSelectionOverride> choices=new ArrayList<>();names.add("Automático");choices.add(null);
        if(type==C.TRACK_TYPE_TEXT){names.add("Desativar");choices.add(null);}
        for(Tracks.Group g:player.getCurrentTracks().getGroups())if(g.getType()==type)for(int i=0;i<g.length;i++)if(g.isTrackSupported(i)){
            Format f=g.getTrackFormat(i);names.add((f.label==null?"Faixa "+(i+1):f.label)+" • "+(f.language==null?"idioma não informado":f.language));choices.add(new TrackSelectionOverride(g.getMediaTrackGroup(),i));
        }
        choices("Faixas disponíveis",names.toArray(new String[0]),i->{if(player==null)return;TrackSelectionParameters.Builder b=player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(type).setTrackTypeDisabled(type,type==C.TRACK_TYPE_TEXT&&i==1);if(choices.get(i)!=null)b.setOverrideForType(choices.get(i));player.setTrackSelectionParameters(b.build());});
    }
    String baseName(Playlist.Channel c){return c.name.replaceAll("(?i)\\b(4K|UHD|FHD|HD|SD|H265|HEVC)\\b","").replaceAll("\\s+"," ").trim();}
    void alternatives(){if(playing==null)return;List<Playlist.Channel> options=new ArrayList<>();for(Playlist.Channel c:channels)if(baseName(c).equalsIgnoreCase(baseName(playing)))options.add(c);choices("Fontes disponíveis",options.stream().map(c->c.name).toArray(String[]::new),i->unlock(options.get(i),()->play(options.get(i))));}
    void nextEpisode(){if(playing==null||!playing.kind().equals("Séries")){toast("Sem próximo episódio identificado.");return;}Playlist.Channel next=null;for(Playlist.Channel c:channels)if(c.seriesName().equals(playing.seriesName())&&c.episodeOrder()>playing.episodeOrder()&&(next==null||c.episodeOrder()<next.episodeOrder()))next=c;
        if(next==null){toast("Último episódio disponível.");return;}final Playlist.Channel n=next;unlock(n,()->play(n));
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e){if(video!=null&&player!=null&&video.hasFocus()&&video.dispatchKeyEvent(e))return true;return super.dispatchKeyEvent(e);}
    @Override public void onBackPressed(){if(playing!=null){if(full)fullScreen();else home();}else if(!seriesFilter.isEmpty()){seriesFilter="";filter();}else if(!screen.equals("catalog"))home();else super.onBackPressed();}
    @Override protected void onStop(){release();super.onStop();}
    @Override protected void onStart(){super.onStart();if(playing!=null&&player==null)startPlayer();}
    @Override protected void onDestroy(){release();artwork.close();worker.shutdown();super.onDestroy();}
}
