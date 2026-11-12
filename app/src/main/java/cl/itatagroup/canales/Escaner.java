package cl.itatagroup.canales;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * «Buscar canales»: baja las listas públicas de iptv-org, aplica las reglas de {@link Catalogo} y
 * prueba cada canal pidiendo los primeros bytes de su lista HLS. Corre solo cuando se pulsa el botón.
 * Las únicas conexiones son a {@link #FUENTES} y a los enlaces de cada canal.
 */
public final class Escaner {
  private static final String TAG = "Canales";

  /** Listas que se consultan: Chile, y en español e inglés solo para sacar las internacionales abiertas. */
  static final String CHILE = "https://iptv-org.github.io/iptv/countries/cl.m3u";
  static final String[] IDIOMAS = {
    "https://iptv-org.github.io/iptv/languages/spa.m3u",
    "https://iptv-org.github.io/iptv/languages/eng.m3u",
  };
  static final String[] FUENTES = {Catalogo.TVN_EN_VIVO, CHILE, IDIOMAS[0], IDIOMAS[1], Guia.CHILE};

  private static final int ESPERA_MS = 8000;
  private static final int MAX_LISTA = 4 * 1024 * 1024;

  public interface Avance {
    void probando(int hechos, int total, int vivos);

    /** {@code fuera}: los canales de la lista de Chile que no se incluyeron, con su motivo. */
    void listo(List<Canal> vivos, List<Catalogo.Excluido> fuera);

    void fallo(String motivo);
  }

  private final String userAgent;
  private final Handler principal = new Handler(Looper.getMainLooper());
  private volatile boolean cancelado;

  public Escaner(String userAgent) {
    this.userAgent = userAgent;
  }

  public void cancelar() {
    cancelado = true;
  }

  public void buscar(Avance avance) {
    new Thread(() -> {
      try {
        List<Catalogo.Excluido> fuera = new ArrayList<>();
        List<Canal> candidatos = new ArrayList<>();
        // Primero las señales que publica el propio canal en su web: ganan a las copias de las listas.
        try {
          candidatos.addAll(Catalogo.deTvn(bajarTexto(Catalogo.TVN_EN_VIVO)));
        } catch (IOException e) {
          Log.w(TAG, "no se pudo leer tvn.cl", e);
        }
        candidatos.addAll(Catalogo.deChile(bajarLista(CHILE), fuera));
        for (String idioma : IDIOMAS) {
          try {
            candidatos.addAll(Catalogo.internacionales(bajarLista(idioma)));
          } catch (IOException e) {
            Log.w(TAG, "no se pudo bajar " + idioma, e); // sin internacionales se sigue con Chile
          }
        }
        candidatos = sinRepetidos(candidatos);
        List<Canal> vivos = probarTodos(candidatos, avance);
        // TVN, 13 y CHV sin enlace abierto: van siempre, y abren su app oficial.
        vivos.addAll(Catalogo.conAppPropia());
        guia = bajarGuia(vivos);
        if (!cancelado) principal.post(() -> avance.listo(vivos, fuera));
      } catch (IOException e) {
        Log.w(TAG, "búsqueda fallida", e);
        principal.post(() -> avance.fallo("No se pudo bajar la lista de canales. Revisa la conexión."));
      }
    }, "buscar-canales").start();
  }

  public interface AlCargarListas {
    void listo(List<Canal> canales, int fallidas);
  }

  /** Baja las listas del usuario (sin filtrar ni probar) en un hilo aparte. */
  public void cargarListas(List<String[]> listas, AlCargarListas oyente) {
    new Thread(() -> {
      List<Canal> canales = new ArrayList<>();
      int fallidas = 0;
      for (String[] l : listas) {
        try {
          canales.addAll(Catalogo.deListaPropia(bajarLista(l[1]), l[0]));
        } catch (IOException | RuntimeException e) {
          Log.w(TAG, "no se pudo cargar la lista " + l[0], e);
          fallidas++;
        }
      }
      int f = fallidas;
      principal.post(() -> oyente.listo(canales, f));
    }, "mis-listas").start();
  }

  /** Un mismo canal puede venir en dos listas o en dos calidades: queda el primero. */
  static List<Canal> sinRepetidos(List<Canal> canales) {
    Map<String, Canal> porNombre = new LinkedHashMap<>();
    for (Canal c : canales) {
      String clave = c.nombre.toLowerCase(java.util.Locale.ROOT).replace(" hd", "").replace(" ", "");
      if (!porNombre.containsKey(clave)) porNombre.put(clave, c);
    }
    return new ArrayList<>(porNombre.values());
  }

  /** Guía de la última búsqueda (por id de canal), o vacía si no se pudo bajar. */
  public volatile Map<String, List<Guia.Programa>> guia = new java.util.HashMap<>();

  /** La guía es un extra: si falla, la búsqueda sigue sin ella. */
  private Map<String, List<Guia.Programa>> bajarGuia(List<Canal> canales) {
    try {
      HttpURLConnection con = abrir(Guia.CHILE);
      try {
        if (con.getResponseCode() != 200) throw new IOException("HTTP " + con.getResponseCode());
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
            new java.util.zip.GZIPInputStream(con.getInputStream()), StandardCharsets.UTF_8))) {
          return Guia.leer(r, canales, System.currentTimeMillis());
        }
      } finally {
        con.disconnect();
      }
    } catch (IOException | RuntimeException e) {
      Log.w(TAG, "sin guía de programación", e);
      return new java.util.HashMap<>();
    }
  }

  private String bajarTexto(String url) throws IOException {
    HttpURLConnection con = abrir(url);
    try {
      if (con.getResponseCode() != 200) throw new IOException("HTTP " + con.getResponseCode());
      StringBuilder sb = new StringBuilder();
      try (BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
        String l;
        while ((l = r.readLine()) != null && sb.length() < MAX_LISTA) sb.append(l).append('\n');
      }
      return sb.toString();
    } finally {
      con.disconnect();
    }
  }

  private List<Catalogo.Entrada> bajarLista(String url) throws IOException {
    HttpURLConnection con = abrir(url);
    try {
      if (con.getResponseCode() != 200) throw new IOException("HTTP " + con.getResponseCode());
      if (con.getContentLength() > MAX_LISTA) throw new IOException("lista demasiado grande");
      try (BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
        return Catalogo.leer(r);
      }
    } finally {
      con.disconnect();
    }
  }

  private List<Canal> probarTodos(List<Canal> candidatos, Avance avance) {
    ExecutorService pool = Executors.newFixedThreadPool(16);
    List<Canal> vivos = Collections.synchronizedList(new ArrayList<>());
    AtomicInteger hechos = new AtomicInteger();
    int total = candidatos.size();
    for (Canal c : candidatos) {
      pool.execute(() -> {
        if (!cancelado && responde(c.url)) vivos.add(c);
        int h = hechos.incrementAndGet();
        int v = vivos.size();
        principal.post(() -> avance.probando(h, total, v));
      });
    }
    pool.shutdown();
    try {
      pool.awaitTermination(3, TimeUnit.MINUTES);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    // Mismo orden que en la lista original, no el de llegada.
    List<Canal> ordenados = new ArrayList<>();
    for (Canal c : candidatos) if (vivos.contains(c)) ordenados.add(c);
    return ordenados;
  }

  /** Responde si el enlace devuelve una lista HLS (empieza con #EXTM3U). */
  boolean responde(String url) {
    HttpURLConnection con = null;
    try {
      con = abrir(url);
      if (con.getResponseCode() != 200) return false;
      byte[] buf = new byte[512];
      int n;
      try (InputStream in = con.getInputStream()) {
        n = in.read(buf);
      }
      return n > 0 && new String(buf, 0, n, StandardCharsets.UTF_8).contains("#EXTM3U");
    } catch (IOException | RuntimeException e) {
      return false;
    } finally {
      if (con != null) con.disconnect();
    }
  }

  private HttpURLConnection abrir(String url) throws IOException {
    HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
    con.setConnectTimeout(ESPERA_MS);
    con.setReadTimeout(ESPERA_MS);
    con.setInstanceFollowRedirects(true);
    con.setUseCaches(false);
    con.setRequestProperty("User-Agent", userAgent);
    return con;
  }
}
