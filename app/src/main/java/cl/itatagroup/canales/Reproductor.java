package cl.itatagroup.canales;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;

import cl.itatagroup.actualizacion.Actualizador;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * La app entera es esta pantalla: el canal a pantalla completa y, encima, la lista lateral, el
 * rótulo y la tarjeta central (bienvenida, búsqueda, «Qué hace esta app»).
 *
 * <p>Control remoto: ↑ ↓ canal anterior y siguiente · números: ir a ese canal · OK o ←: lista ·
 * Atrás dos veces: salir (la primera solo avisa, para no cerrar la app por accidente).
 */
public class Reproductor extends Activity {
  private static final String TAG = "Canales";
  private static final long ROTULO_MS = 3500;
  private static final long TECLEO_MS = 1500;

  /** Filas de la columna de categorías: las de canales y, al final, las acciones. */
  private static final String FAVORITOS = "★ Favoritos";
  private static final String ACCION_BUSCAR = "Buscar canales";
  private static final String ACCION_ACERCA = "Qué hace esta app";
  private static final String ACCION_ACTUALIZAR = "Buscar actualización";
  private static final String ACCION_LISTAS = "Agregar listas";
  /** Manifiesto firmado del último release (mismo contrato que launcher-tv, ver docs/actualizaciones.md). */
  private static final String MANIFIESTO = "https://github.com/itatagroup/canales-tv/releases/latest/download/version.json";

  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable ocultarRotulo = () -> this.rotulo.setVisibility(View.GONE);
  private final Runnable irATecleado = this::irATecleado;

  private Almacen almacen;
  private ExoPlayer player;
  private Escaner escaner;
  private Actualizador actualizador;
  private ServidorListas servidor;
  private String userAgent;

  private SurfaceView video;
  private View rotulo, panel, tarjeta;
  private TextView rotuloNumero, rotuloNombre, rotuloEstado, tecleado, listaVacia;
  private TextView tarjetaTitulo, tarjetaTexto;
  private ProgressBar tarjetaProgreso;
  private Button tarjetaBoton, tarjetaBoton2;
  private ListView listaCategorias, listaCanales;

  /** Todos los canales, ordenados por número (el orden del zapping). */
  private List<Canal> todos = new ArrayList<>();
  private final List<String> categorias = new ArrayList<>();
  private final List<Canal> visibles = new ArrayList<>();
  private final FilasAdapter adapterCategorias = new FilasAdapter(true);
  private final FilasAdapter adapterCanales = new FilasAdapter(false);
  private Set<String> favoritos;
  /** Programación por id de canal, de la última búsqueda. */
  private java.util.Map<String, List<Guia.Programa>> guia = new java.util.HashMap<>();

  private Canal actual;
  /** Cuándo se pulsó Atrás sobre el video; otro Atrás antes de ROTULO_MS cierra la app. */
  private long atrasEn;
  private long inicio;
  private boolean conError;
  /** Ya se reintentó este canal con su primera variante. */
  private boolean conVariante;
  private String numeroTecleado = "";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.reproductor);
    almacen = new Almacen(this);
    guia = almacen.guia();
    actualizador = new Actualizador(this, "itatagroup/canales-tv", "canales-android", "Canales");
    video = findViewById(R.id.video);
    rotulo = findViewById(R.id.rotulo);
    rotuloNumero = findViewById(R.id.rotulo_numero);
    rotuloNombre = findViewById(R.id.rotulo_nombre);
    rotuloEstado = findViewById(R.id.rotulo_estado);
    tecleado = findViewById(R.id.tecleado);
    panel = findViewById(R.id.panel);
    listaVacia = findViewById(R.id.lista_vacia);
    tarjeta = findViewById(R.id.tarjeta);
    tarjetaTitulo = findViewById(R.id.tarjeta_titulo);
    tarjetaTexto = findViewById(R.id.tarjeta_texto);
    tarjetaProgreso = findViewById(R.id.tarjeta_progreso);
    tarjetaBoton = findViewById(R.id.tarjeta_boton);
    tarjetaBoton2 = findViewById(R.id.tarjeta_boton2);
    listaCategorias = findViewById(R.id.categorias);
    listaCanales = findViewById(R.id.lista_canales);

    String version = "?";
    try {
      version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
    } catch (Exception e) {
      Log.w(TAG, "sin versión", e);
    }
    userAgent = "Canales/" + version;

    listaCategorias.setAdapter(adapterCategorias);
    listaCanales.setAdapter(adapterCanales);
    // Al moverse por las categorías, la columna de canales cambia en el acto (sin apretar OK).
    listaCategorias.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        mostrarCategoria(pos);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {}
    });
    listaCategorias.setOnItemClickListener((p, v, pos, id) -> {
      String cat = categorias.get(pos);
      if (cat.equals(ACCION_BUSCAR)) buscarCanales();
      else if (cat.equals(ACCION_ACERCA)) mostrarAcerca();
      else if (cat.equals(ACCION_ACTUALIZAR)) buscarActualizacion();
      else if (cat.equals(ACCION_LISTAS)) abrirMisListas();
      else if (!visibles.isEmpty()) listaCanales.requestFocus();
    });
    listaCanales.setOnItemClickListener((p, v, pos, id) -> {
      sintonizar(visibles.get(pos));
      cerrarPanel();
    });
    listaCanales.setOnItemLongClickListener((p, v, pos, id) -> {
      Canal c = visibles.get(pos);
      almacen.alternarFavorito(c.id);
      favoritos = almacen.favoritos();
      avisar(getString(favoritos.contains(c.id) ? R.string.favorito_si : R.string.favorito_no, c.nombre));
      adapterCanales.notifyDataSetChanged();
      return true;
    });

    cargar();
  }

  @Override
  protected void onStart() {
    super.onStart();
    player = new ExoPlayer.Builder(this).build();
    player.setVideoSurfaceView(video);
    player.addListener(new Player.Listener() {
      @Override
      public void onRenderedFirstFrame() {
        Log.i(TAG, (actual != null ? actual.nombre : "?") + ": primera imagen en " + (SystemClock.elapsedRealtime() - inicio) + " ms");
        conError = false;
        if (actual != null) rotuloEstado.setText(estadoDe(actual));
        programarOcultarRotulo();
      }

      @Override
      public void onPlayerError(PlaybackException error) {
        Log.w(TAG, (actual != null ? actual.nombre : "?") + ": " + error.getErrorCodeName(), error);
        // Listas con extensiones nuevas de HLS (p. ej. Content Steering) que Media3 no lee: se
        // reintenta una vez con la primera variante, que es HLS común.
        if (error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED && actual != null && !conVariante) {
          probarVariante(actual);
          return;
        }
        conError = true;
        // Sin señal: negro, no la última imagen del canal anterior congelada.
        video.setVisibility(View.INVISIBLE);
        // El rótulo completo (no solo el estado): pudo estar mostrando un aviso.
        if (actual != null) mostrarRotulo(actual, getString(R.string.sin_senal));
      }
    });
    if (actual != null) reproducir(actual);
  }

  @Override
  protected void onStop() {
    handler.removeCallbacksAndMessages(null);
    // Al salir de la app se cierra la página de Mis listas, y con ella su tarjeta. Las demás tarjetas
    // (la bienvenida, una búsqueda en curso) se quedan para cuando se vuelva.
    if (servidor != null) {
      detenerServidor();
      tarjeta.setVisibility(View.GONE);
    }
    if (player != null) {
      player.release();
      player = null;
    }
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    if (escaner != null) escaner.cancelar();
    actualizador.cancelar();
    super.onDestroy();
  }

  // --- datos ---

  private void cargar() {
    todos = almacen.canales();
    todos.addAll(almacen.canalesDeListas());
    Collections.sort(todos, (a, b) -> Integer.compare(a.numero, b.numero));
    favoritos = almacen.favoritos();
    categorias.clear();
    categorias.add(FAVORITOS);
    for (String cat : Catalogo.CATEGORIAS) for (Canal c : todos) if (cat.equals(c.categoria)) { categorias.add(cat); break; }
    categorias.add(ACCION_BUSCAR);
    categorias.add(ACCION_LISTAS);
    categorias.add(ACCION_ACTUALIZAR);
    categorias.add(ACCION_ACERCA);
    adapterCategorias.notifyDataSetChanged();

    if (todos.isEmpty()) {
      mostrarBienvenida();
      return;
    }
    String ultimo = almacen.ultimo();
    Canal elegido = null;
    for (Canal c : todos) {
      if (c.url.startsWith(Catalogo.APP)) continue; // se abre aparte, no se reproduce aquí
      if (elegido == null || c.id.equals(ultimo)) elegido = c;
    }
    if (elegido == null) return;
    if (actual == null || !todos.contains(actual)) {
      actual = elegido;
      if (player != null) reproducir(actual);
    }
  }

  private void mostrarCategoria(int pos) {
    visibles.clear();
    String cat = categorias.get(pos);
    if (cat.equals(FAVORITOS)) {
      for (Canal c : todos) if (favoritos.contains(c.id)) visibles.add(c);
      listaVacia.setText(R.string.sin_favoritos);
    } else {
      for (Canal c : todos) if (cat.equals(c.categoria)) visibles.add(c);
      listaVacia.setText("");
    }
    boolean esAccion = cat.equals(ACCION_BUSCAR) || cat.equals(ACCION_ACERCA) || cat.equals(ACCION_ACTUALIZAR)
        || cat.equals(ACCION_LISTAS);
    listaVacia.setVisibility(visibles.isEmpty() && !esAccion ? View.VISIBLE : View.GONE);
    listaCanales.setFocusable(!visibles.isEmpty());
    adapterCanales.notifyDataSetChanged();
    int i = visibles.indexOf(actual);
    listaCanales.setSelection(Math.max(i, 0));
  }

  // --- reproducción ---

  private void sintonizar(Canal c) {
    if (c == null) return;
    if (c.url.startsWith(Catalogo.APP)) {
      abrirAppOficial(c);
      return;
    }
    actual = c;
    almacen.recordar(c.id);
    reproducir(c);
  }

  private void reproducir(Canal c) {
    conVariante = false;
    mostrarRotulo(c, getString(R.string.cargando));
    reproducirUrl(c.url);
  }

  /** Baja la lista principal y reproduce su primera variante (ver onPlayerError). */
  private void probarVariante(Canal c) {
    conVariante = true;
    new Thread(() -> {
      String variante = null;
      java.net.HttpURLConnection con = null;
      try {
        con = (java.net.HttpURLConnection) new java.net.URL(c.url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        con.setRequestProperty("User-Agent", userAgent);
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(con.getInputStream(),
            java.nio.charset.StandardCharsets.UTF_8))) {
          String l;
          boolean siguienteEsVariante = false;
          int leidas = 0;
          while ((l = r.readLine()) != null && leidas++ < 500) {
            l = l.trim();
            if (l.startsWith("#EXT-X-STREAM-INF")) siguienteEsVariante = true;
            else if (siguienteEsVariante && !l.isEmpty() && !l.startsWith("#")) {
              variante = new java.net.URL(con.getURL(), l).toString();
              break;
            }
          }
        }
      } catch (java.io.IOException | RuntimeException e) {
        Log.w(TAG, "sin variante para " + c.nombre, e);
      } finally {
        if (con != null) con.disconnect();
      }
      String v = variante;
      handler.post(() -> {
        if (!c.equals(actual)) return; // ya se cambió de canal
        if (v == null) {
          conError = true;
          video.setVisibility(View.INVISIBLE);
          mostrarRotulo(c, getString(R.string.sin_senal));
        } else {
          Log.i(TAG, c.nombre + ": se reintenta con la variante " + v);
          reproducirUrl(v);
        }
      });
    }, "variante").start();
  }

  private void reproducirUrl(String url) {
    if (player == null) return;
    conError = false;
    video.setVisibility(View.VISIBLE);
    // User-Agent honesto: dice qué app es y su versión, nada del equipo ni del usuario.
    DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
        .setUserAgent(userAgent)
        .setAllowCrossProtocolRedirects(true);
    MediaItem item = new MediaItem.Builder().setUri(Uri.parse(url)).setMimeType(MimeTypes.APPLICATION_M3U8).build();
    inicio = SystemClock.elapsedRealtime();
    player.setMediaSource(new HlsMediaSource.Factory(http).createMediaSource(item));
    player.prepare();
    player.setPlayWhenReady(true);
  }

  /**
   * TVN, Canal 13 y Chilevisión no publican un enlace abierto: se busca su app oficial en la tienda
   * del equipo (si ya está instalada, la tienda ofrece «Abrir»). La app no adivina enlaces.
   */
  private void abrirAppOficial(Canal c) {
    String busqueda = c.url.substring(Catalogo.APP.length());
    try {
      startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
          Uri.parse("market://search?q=" + Uri.encode(busqueda))));
    } catch (android.content.ActivityNotFoundException e) {
      avisar(getString(R.string.sin_tienda, c.nombre));
    }
  }

  /** ↑ ↓ pasan por los canales que se ven aquí; los que abren otra app se saltan. */
  private void zapping(int paso) {
    int n = todos.size();
    int i = actual == null ? (paso > 0 ? -1 : 0) : todos.indexOf(actual);
    for (int intento = 0; intento < n; intento++) {
      i = ((i + paso) % n + n) % n;
      Canal c = todos.get(i);
      if (!c.url.startsWith(Catalogo.APP)) {
        sintonizar(c);
        return;
      }
    }
  }

  // --- rótulo y avisos ---

  /** Línea bajo el nombre: «Ahora: … · Después: …» si hay guía, y si no transmite todo el día. */
  private String estadoDe(Canal c) {
    StringBuilder sb = new StringBuilder();
    Guia.Programa[] ad = Guia.ahoraYDespues(guia.get(c.id), System.currentTimeMillis());
    if (ad != null) {
      java.text.DateFormat hora = android.text.format.DateFormat.getTimeFormat(this);
      if (ad[0] != null) sb.append(getString(R.string.guia_ahora, ad[0].titulo, hora.format(new java.util.Date(ad[0].fin))));
      if (ad[1] != null) {
        if (sb.length() > 0) sb.append('\n');
        sb.append(getString(R.string.guia_despues, hora.format(new java.util.Date(ad[1].inicio)), ad[1].titulo));
      }
    }
    if (c.noSiempre) {
      if (sb.length() > 0) sb.append('\n');
      sb.append(getString(R.string.no_siempre));
    }
    return sb.toString();
  }

  private void mostrarRotulo(Canal c, String estado) {
    handler.removeCallbacks(ocultarRotulo);
    rotuloNumero.setText(String.valueOf(c.numero));
    rotuloNombre.setText(favoritos.contains(c.id) ? "★ " + c.nombre : c.nombre);
    rotuloEstado.setText(estado);
    rotuloNumero.setVisibility(View.VISIBLE);
    rotuloEstado.setVisibility(View.VISIBLE);
    rotulo.setVisibility(View.VISIBLE);
  }

  private void programarOcultarRotulo() {
    handler.removeCallbacks(ocultarRotulo);
    if (!conError) handler.postDelayed(ocultarRotulo, ROTULO_MS);
  }

  /** Un aviso corto en el rótulo, sin tapar el video. */
  private void avisar(String texto) {
    handler.removeCallbacks(ocultarRotulo);
    rotuloNumero.setVisibility(View.GONE); // sin número ni estado: el recuadro queda justo al texto
    rotuloNombre.setText(texto);
    rotuloEstado.setVisibility(View.GONE);
    rotulo.setVisibility(View.VISIBLE);
    handler.postDelayed(ocultarRotulo, ROTULO_MS);
  }

  // --- números del control ---

  private void teclear(int digito) {
    if (numeroTecleado.length() >= 3) numeroTecleado = "";
    numeroTecleado += digito;
    tecleado.setText(numeroTecleado);
    tecleado.setVisibility(View.VISIBLE);
    handler.removeCallbacks(irATecleado);
    handler.postDelayed(irATecleado, TECLEO_MS);
  }

  private void irATecleado() {
    tecleado.setVisibility(View.GONE);
    if (numeroTecleado.isEmpty()) return;
    int n = Integer.parseInt(numeroTecleado);
    numeroTecleado = "";
    for (Canal c : todos) {
      if (c.numero == n) {
        sintonizar(c);
        return;
      }
    }
    avisar(getString(R.string.canal_no_existe, n));
  }

  // --- panel ---

  private void abrirPanel() {
    if (categorias.isEmpty()) return;
    panel.setVisibility(View.VISIBLE);
    rotulo.setVisibility(View.GONE);
    // Abre en la categoría del canal que se está viendo, con ese canal enfocado.
    int pos = actual == null ? 0 : Math.max(categorias.indexOf(actual.categoria), 0);
    listaCategorias.setItemChecked(pos, true);
    listaCategorias.setSelection(pos);
    mostrarCategoria(pos);
    if (!visibles.isEmpty()) listaCanales.requestFocus();
    else listaCategorias.requestFocus();
  }

  private void cerrarPanel() {
    panel.setVisibility(View.GONE);
    video.requestFocus();
  }

  // --- tarjeta central ---

  private void mostrarTarjeta(String titulo, String texto, String boton, View.OnClickListener alPulsar) {
    cerrarPanel();
    tarjetaTitulo.setText(titulo);
    tarjetaTexto.setText(texto);
    tarjetaProgreso.setVisibility(View.GONE);
    tarjetaBoton2.setVisibility(View.GONE);
    tarjetaBoton.setText(boton);
    tarjetaBoton.setOnClickListener(alPulsar);
    tarjeta.setVisibility(View.VISIBLE);
    tarjetaBoton.requestFocus();
    // Un texto largo (la lista de excluidos) no empuja los botones fuera de la pantalla: se recorre
    // con ↑ desde los botones.
    View scroll = findViewById(R.id.tarjeta_scroll);
    scroll.scrollTo(0, 0);
    ViewGroup.LayoutParams lp = scroll.getLayoutParams();
    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
    scroll.setLayoutParams(lp);
    scroll.post(() -> {
      int max = getWindow().getDecorView().getHeight() / 2;
      if (scroll.getHeight() > max) {
        lp.height = max;
        scroll.setLayoutParams(lp);
      }
    });
  }

  private void cerrarTarjeta() {
    tarjeta.setVisibility(View.GONE);
    detenerServidor();
  }

  private void detenerServidor() {
    if (servidor != null) servidor.detener();
    servidor = null;
  }

  // --- Mis listas: se administran desde el teléfono, en la red de la casa ---

  private void abrirMisListas() {
    detenerServidor();
    String direccion = ServidorListas.direccion();
    ServidorListas nuevo = new ServidorListas(new ServidorListas.Cambios() {
      @Override
      public void agregar(String nombre, String url) {
        List<String[]> listas = almacen.listas();
        for (String[] l : listas) if (l[1].equals(url)) return;
        listas.add(new String[] {nombre, url});
        almacen.guardarListas(listas);
        recargarListas();
      }

      @Override
      public void quitar(int indice) {
        List<String[]> listas = almacen.listas();
        if (indice < 0 || indice >= listas.size()) return;
        listas.remove(indice);
        almacen.guardarListas(listas);
        recargarListas();
      }

      @Override
      public List<String[]> listas() {
        return almacen.listas();
      }
    });
    String texto;
    try {
      if (direccion == null) throw new java.io.IOException("sin red local");
      nuevo.iniciar();
      texto = getString(R.string.listas_texto, direccion, nuevo.codigo);
    } catch (java.io.IOException e) {
      Log.w(TAG, "no se pudo abrir la página de listas", e);
      mostrarTarjeta(getString(R.string.listas_titulo), getString(R.string.listas_sin_red), getString(R.string.cerrar), v -> cerrarTarjeta());
      return;
    }
    mostrarTarjeta(getString(R.string.listas_titulo), texto + resumenListas(), getString(R.string.listo), v -> {
      cerrarTarjeta();
      if (!todos.isEmpty()) abrirPanel();
    });
    // Después de mostrarTarjeta (que cierra un servidor anterior), para que este quede abierto.
    servidor = nuevo;
  }

  private String resumenListas() {
    List<String[]> listas = almacen.listas();
    if (listas.isEmpty()) return "\n\n" + getString(R.string.listas_ninguna);
    StringBuilder sb = new StringBuilder("\n");
    for (String[] l : listas) sb.append("\n• ").append(l[0]).append("  (").append(hostDe(l[1])).append(")");
    return sb.toString();
  }

  /** Vuelve a bajar todas las listas del usuario; sin filtro ni prueba: se muestran tal cual. */
  private void recargarListas() {
    List<String[]> listas = almacen.listas();
    new Escaner(userAgent).cargarListas(listas, (canales, fallidas) -> {
      almacen.guardarCanalesDeListas(canales);
      cargar();
      if (servidor != null && tarjeta.getVisibility() == View.VISIBLE) {
        String estado = getString(R.string.listas_cargadas, canales.size())
            + (fallidas > 0 ? " " + getString(R.string.listas_fallidas, fallidas) : "");
        tarjetaTexto.setText(getString(R.string.listas_texto, ServidorListas.direccion(), servidor.codigo) + resumenListas() + "\n\n" + estado);
      }
    });
  }

  private void mostrarBienvenida() {
    mostrarTarjeta(getString(R.string.bienvenida_titulo), getString(R.string.bienvenida_texto), getString(R.string.buscar),
        v -> buscarCanales());
  }

  private void mostrarAcerca() {
    // Las fuentes van primero: son lo que cualquiera puede abrir para verificar.
    String texto = getString(R.string.fuentes, String.join("  ·  ", Escaner.FUENTES)) + "\n\n" + getString(R.string.acerca_texto);
    mostrarTarjeta(getString(R.string.acerca), texto, getString(R.string.cerrar), v -> cerrarTarjeta());
    segundoBoton(getString(R.string.fuera_titulo), v -> mostrarFuera());
  }

  private void mostrarFuera() {
    String texto = almacen.fuera();
    mostrarTarjeta(getString(R.string.fuera_titulo), texto.isEmpty() ? getString(R.string.fuera_nada) : texto,
        getString(R.string.ver_canales), v -> {
          cerrarTarjeta();
          if (!todos.isEmpty()) abrirPanel();
        });
  }

  /** «Canales de pago: Disney, ESPN… / No vienen del propio canal: Mega (desde vtrplay.com)…». */
  private String textoFuera(List<Catalogo.Excluido> fuera) {
    List<String> pago = new ArrayList<>();
    List<String> origen = new ArrayList<>();
    for (Catalogo.Excluido x : fuera) {
      if (x.motivo.equals(Catalogo.MOTIVO_PAGO)) pago.add(x.nombre);
      else origen.add(x.nombre + " (" + hostDe(x.url) + ")");
    }
    StringBuilder sb = new StringBuilder();
    if (!pago.isEmpty()) sb.append(getString(R.string.fuera_pago, String.join(", ", pago)));
    if (!origen.isEmpty()) {
      if (sb.length() > 0) sb.append("\n\n");
      sb.append(getString(R.string.fuera_origen, String.join(", ", origen)));
    }
    return sb.toString();
  }

  /** El servidor de un enlace, para mostrar de dónde viene una señal. */
  static String hostDe(String url) {
    try {
      String host = Uri.parse(url).getHost();
      return host == null ? url : host;
    } catch (RuntimeException e) {
      return url;
    }
  }

  private void segundoBoton(String texto, View.OnClickListener alPulsar) {
    tarjetaBoton2.setText(texto);
    tarjetaBoton2.setOnClickListener(alPulsar);
    tarjetaBoton2.setVisibility(View.VISIBLE);
  }

  private void buscarCanales() {
    if (escaner != null) return;
    mostrarTarjeta(getString(R.string.buscando), "", getString(R.string.cancelar), v -> cancelarBusqueda());
    tarjetaProgreso.setVisibility(View.VISIBLE);
    tarjetaProgreso.setProgress(0);
    Escaner buscador = new Escaner(userAgent);
    escaner = buscador;
    long t0 = SystemClock.elapsedRealtime();
    buscador.buscar(new Escaner.Avance() {
      @Override
      public void probando(int hechos, int total, int vivos) {
        tarjetaProgreso.setMax(total);
        tarjetaProgreso.setProgress(hechos);
        tarjetaTexto.setText(getString(R.string.probando, hechos, total, vivos));
      }

      @Override
      public void listo(List<Canal> vivos, List<Catalogo.Excluido> fuera) {
        escaner = null;
        Log.i(TAG, "búsqueda: " + vivos.size() + " canales, " + fuera.size() + " fuera, en "
            + (SystemClock.elapsedRealtime() - t0) + " ms");
        almacen.guardarFuera(textoFuera(fuera));
        if (vivos.isEmpty()) {
          mostrarTarjeta(getString(R.string.buscar), getString(R.string.ninguno), getString(R.string.buscar), v -> buscarCanales());
          return;
        }
        almacen.guardar(vivos);
        almacen.guardarGuia(buscador.guia);
        guia = buscador.guia;
        cargar();
        int pago = 0;
        for (Catalogo.Excluido x : fuera) if (x.motivo.equals(Catalogo.MOTIVO_PAGO)) pago++;
        int origen = fuera.size() - pago;
        if (fuera.isEmpty()) {
          cerrarTarjeta();
          avisar(getString(R.string.encontrados, vivos.size()));
          abrirPanel();
          return;
        }
        // Se dice siempre qué quedó fuera y se ofrece verlo: nada se esconde.
        mostrarTarjeta(getString(R.string.resultado_titulo, vivos.size()), getString(R.string.resultado_texto, pago, origen),
            getString(R.string.ver_canales), v -> {
              cerrarTarjeta();
              abrirPanel();
            });
        segundoBoton(getString(R.string.ver_cuales), v -> mostrarFuera());
      }

      @Override
      public void fallo(String motivo) {
        escaner = null;
        mostrarTarjeta(getString(R.string.buscar), motivo, getString(R.string.buscar), v -> buscarCanales());
      }
    });
  }

  // --- actualización de la propia app: solo cuando se pulsa, nunca sola ---

  private void buscarActualizacion() {
    mostrarTarjeta(getString(R.string.accion_actualizar), getString(R.string.act_buscando), getString(R.string.cancelar), v -> {
      actualizador.cancelar();
      cerrarTarjeta();
    });
    actualizador.comprobar(MANIFIESTO, new Actualizador.AlComprobar() {
      @Override
      public void hayVersion(Actualizador.Oferta oferta) {
        mostrarTarjeta(getString(R.string.act_hay, oferta.version),
            oferta.notas.isEmpty() ? getString(R.string.act_sin_notas) : oferta.notas,
            getString(R.string.act_actualizar), v -> actualizar(oferta));
        segundoBoton(getString(R.string.act_ahora_no), v -> cerrarTarjeta());
      }

      @Override
      public void alDia() {
        mostrarTarjeta(getString(R.string.accion_actualizar), getString(R.string.act_al_dia, actualizador.versionNombre()),
            getString(R.string.cerrar), v -> cerrarTarjeta());
      }

      @Override
      public void fallo() {
        // Sin detalles técnicos en pantalla: el motivo queda en adb logcat -s Actualizador.
        mostrarTarjeta(getString(R.string.accion_actualizar), getString(R.string.act_fallo), getString(R.string.cerrar),
            v -> cerrarTarjeta());
      }
    });
  }

  private void actualizar(Actualizador.Oferta oferta) {
    if (!actualizador.puedeInstalar()) {
      mostrarTarjeta(getString(R.string.act_permiso_titulo), getString(R.string.act_permiso), getString(R.string.act_abrir_ajuste),
          v -> {
            cerrarTarjeta();
            if (!Actualizador.abrirPermisoDeInstalacion(this)) avisar(getString(R.string.act_sin_ajuste));
          });
      segundoBoton(getString(R.string.act_ahora_no), v -> cerrarTarjeta());
      return;
    }
    mostrarTarjeta(getString(R.string.act_hay, oferta.version), getString(R.string.act_bajando, 0), getString(R.string.cancelar),
        v -> {
          actualizador.cancelar();
          cerrarTarjeta();
        });
    tarjetaProgreso.setVisibility(View.VISIBLE);
    tarjetaProgreso.setMax(100);
    actualizador.descargarEInstalar(oferta, new Actualizador.AlDescargar() {
      @Override
      public void avance(int porcentaje) {
        tarjetaProgreso.setProgress(porcentaje);
        tarjetaTexto.setText(getString(R.string.act_bajando, porcentaje));
      }

      @Override
      public void instalando() {
        mostrarTarjeta(getString(R.string.act_hay, oferta.version), getString(R.string.act_instalando), getString(R.string.cerrar),
            v -> cerrarTarjeta());
      }

      @Override
      public void fallo() {
        mostrarTarjeta(getString(R.string.accion_actualizar), getString(R.string.act_fallo), getString(R.string.cerrar),
            v -> cerrarTarjeta());
      }
    });
  }

  private void cancelarBusqueda() {
    if (escaner != null) escaner.cancelar();
    escaner = null;
    cerrarTarjeta();
    if (todos.isEmpty()) mostrarBienvenida();
  }

  // --- teclas ---

  @Override
  public void onBackPressed() {
    if (tarjeta.getVisibility() == View.VISIBLE) {
      if (escaner != null) cancelarBusqueda();
      else if (todos.isEmpty()) super.onBackPressed();
      else cerrarTarjeta();
    } else if (panel.getVisibility() == View.VISIBLE) {
      cerrarPanel();
    } else {
      // Dos veces para salir: un Atrás suelto no cierra la app (dura lo mismo que el aviso).
      long ahora = SystemClock.uptimeMillis();
      if (atrasEn != 0 && ahora - atrasEn < ROTULO_MS) {
        super.onBackPressed();
      } else {
        atrasEn = ahora;
        avisar(getString(R.string.atras_otra_vez));
      }
    }
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    // Otra tecla entre medio (zapping, lista) anula el primer Atrás: el segundo vuelve a avisar.
    if (keyCode != KeyEvent.KEYCODE_BACK) atrasEn = 0;
    if (panel.getVisibility() == View.VISIBLE || tarjeta.getVisibility() == View.VISIBLE) {
      return super.onKeyDown(keyCode, event);
    }
    if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
      teclear(keyCode - KeyEvent.KEYCODE_0);
      return true;
    }
    switch (keyCode) {
      case KeyEvent.KEYCODE_DPAD_UP:
      case KeyEvent.KEYCODE_CHANNEL_UP:
        zapping(-1);
        return true;
      case KeyEvent.KEYCODE_DPAD_DOWN:
      case KeyEvent.KEYCODE_CHANNEL_DOWN:
        zapping(1);
        return true;
      case KeyEvent.KEYCODE_DPAD_CENTER:
      case KeyEvent.KEYCODE_ENTER:
      case KeyEvent.KEYCODE_DPAD_LEFT:
      case KeyEvent.KEYCODE_MENU:
      case KeyEvent.KEYCODE_GUIDE:
        abrirPanel();
        return true;
      case KeyEvent.KEYCODE_DPAD_RIGHT:
      case KeyEvent.KEYCODE_INFO:
        if (actual != null) {
          // Detalles para verificar: de qué servidor viene la señal y de qué lista salió el canal.
          String calidad = actual.calidad.isEmpty() ? actual.categoria : actual.categoria + " · " + actual.calidad;
          String detalle = getString(R.string.detalle, calidad, hostDe(actual.url), actual.fuente);
          String estado = conError ? getString(R.string.sin_senal) : estadoDe(actual);
          if (!estado.isEmpty()) detalle = estado + "\n" + detalle;
          mostrarRotulo(actual, detalle);
          handler.removeCallbacks(ocultarRotulo);
          if (!conError) handler.postDelayed(ocultarRotulo, ROTULO_MS * 2);
        }
        return true;
      default:
        return super.onKeyDown(keyCode, event);
    }
  }

  /** Filas de las dos columnas del panel. */
  private final class FilasAdapter extends BaseAdapter {
    private final boolean deCategorias;

    FilasAdapter(boolean deCategorias) {
      this.deCategorias = deCategorias;
    }

    @Override
    public int getCount() {
      return deCategorias ? categorias.size() : visibles.size();
    }

    @Override
    public Object getItem(int i) {
      return deCategorias ? categorias.get(i) : visibles.get(i);
    }

    @Override
    public long getItemId(int i) {
      return i;
    }

    @Override
    public View getView(int i, View fila, ViewGroup parent) {
      if (fila == null) fila = LayoutInflater.from(Reproductor.this).inflate(R.layout.fila, parent, false);
      TextView numero = fila.findViewById(R.id.numero);
      TextView nombre = fila.findViewById(R.id.nombre);
      TextView marca = fila.findViewById(R.id.marca);
      fila.setBackgroundResource(deCategorias ? R.drawable.fila_activa : 0);
      if (deCategorias) {
        numero.setVisibility(View.GONE);
        nombre.setText(categorias.get(i));
        marca.setText("");
      } else {
        Canal c = visibles.get(i);
        numero.setVisibility(View.VISIBLE);
        numero.setText(String.valueOf(c.numero));
        nombre.setText(c.nombre);
        String m = c.equals(actual) ? "▶" : (c.url.startsWith(Catalogo.APP) ? getString(R.string.marca_app) : "");
        if (favoritos.contains(c.id)) m = m.isEmpty() ? "★" : m + " ★";
        marca.setText(m);
      }
      return fila;
    }
  }
}
