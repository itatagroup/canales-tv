package cl.itatagroup.canales;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lo que la app guarda, todo dentro del equipo: los canales encontrados, sus números, los favoritos
 * y el último canal visto. Nada se envía a ninguna parte.
 */
public final class Almacen {
  private static final String TAG = "Canales";
  private final File archivo;
  private final File archivoListas;
  private final SharedPreferences prefs;

  public Almacen(Context ctx) {
    archivo = new File(ctx.getFilesDir(), "canales.json");
    archivoListas = new File(ctx.getFilesDir(), "mis-listas.json");
    prefs = ctx.getSharedPreferences("canales", Context.MODE_PRIVATE);
  }

  public List<Canal> canales() {
    return leer(archivo);
  }

  /** Canales de «Mis listas», tal como vienen en las listas del usuario. */
  public List<Canal> canalesDeListas() {
    return leer(archivoListas);
  }

  /** Mis listas empiezan en el 1000, para no chocar con los números de la búsqueda. */
  public void guardarCanalesDeListas(List<Canal> canales) {
    for (int i = 0; i < canales.size(); i++) canales.get(i).numero = 1000 + i;
    escribir(archivoListas, canales);
  }

  /** Listas del usuario: pares {nombre, url}, en el orden en que se agregaron. */
  public List<String[]> listas() {
    List<String[]> listas = new ArrayList<>();
    try {
      JSONArray arr = new JSONArray(prefs.getString("listas", "[]"));
      for (int i = 0; i < arr.length(); i++) {
        listas.add(new String[] {arr.getJSONArray(i).getString(0), arr.getJSONArray(i).getString(1)});
      }
    } catch (JSONException e) {
      Log.w(TAG, "listas ilegibles", e);
    }
    return listas;
  }

  public void guardarListas(List<String[]> listas) {
    JSONArray arr = new JSONArray();
    for (String[] l : listas) arr.put(new JSONArray().put(l[0]).put(l[1]));
    prefs.edit().putString("listas", arr.toString()).apply();
  }

  private List<Canal> leer(File archivo) {
    List<Canal> lista = new ArrayList<>();
    if (!archivo.exists()) return lista;
    try (FileInputStream in = new FileInputStream(archivo)) {
      byte[] datos = new byte[(int) archivo.length()];
      int leidos = 0;
      while (leidos < datos.length) {
        int n = in.read(datos, leidos, datos.length - leidos);
        if (n < 0) break;
        leidos += n;
      }
      JSONArray arr = new JSONArray(new String(datos, 0, leidos, StandardCharsets.UTF_8));
      for (int i = 0; i < arr.length(); i++) lista.add(Canal.desde(arr.getJSONObject(i)));
    } catch (IOException | JSONException e) {
      Log.w(TAG, "no se pudo leer " + archivo.getName(), e);
    }
    return lista;
  }

  /**
   * Guarda el resultado de una búsqueda. Los canales que ya existían conservan su número, para que
   * el 4 siga siendo el mismo canal después de buscar de nuevo; los nuevos toman los siguientes.
   */
  public List<Canal> guardar(List<Canal> encontrados) {
    List<Canal> anteriores = canales();
    int siguiente = 1;
    for (Canal a : anteriores) siguiente = Math.max(siguiente, a.numero + 1);
    List<Canal> resultado = new ArrayList<>();
    for (Canal c : ordenarPorCategoria(encontrados)) {
      for (Canal a : anteriores) if (a.id.equals(c.id)) c.numero = a.numero;
      if (c.numero == 0) c.numero = siguiente++;
      resultado.add(c);
    }
    escribir(archivo, resultado);
    return resultado;
  }

  private static void escribir(File archivo, List<Canal> canales) {
    try {
      JSONArray arr = new JSONArray();
      for (Canal c : canales) arr.put(c.json());
      File tmp = new File(archivo.getPath() + ".tmp");
      try (FileOutputStream out = new FileOutputStream(tmp)) {
        out.write(arr.toString().getBytes(StandardCharsets.UTF_8));
      }
      if (!tmp.renameTo(archivo)) throw new IOException("no se pudo reemplazar " + archivo.getName());
    } catch (IOException | JSONException e) {
      Log.w(TAG, "no se pudo guardar " + archivo.getName(), e);
    }
  }

  /** Primero las categorías en el orden de la grilla, para que la primera búsqueda numere 1, 2, 3… así. */
  static List<Canal> ordenarPorCategoria(List<Canal> canales) {
    List<Canal> orden = new ArrayList<>();
    for (String cat : Catalogo.CATEGORIAS) for (Canal c : canales) if (cat.equals(c.categoria)) orden.add(c);
    for (Canal c : canales) if (!orden.contains(c)) orden.add(c);
    return orden;
  }

  public Set<String> favoritos() {
    return new HashSet<>(prefs.getStringSet("favoritos", new HashSet<>()));
  }

  public void alternarFavorito(String id) {
    Set<String> f = favoritos();
    if (!f.remove(id)) f.add(id);
    prefs.edit().putStringSet("favoritos", f).apply();
  }

  /** Lo que la última búsqueda dejó fuera, como texto para mostrarlo en «Qué hace esta app». */
  public String fuera() {
    return prefs.getString("fuera", "");
  }

  public void guardarFuera(String texto) {
    prefs.edit().putString("fuera", texto).apply();
  }

  public String ultimo() {
    return prefs.getString("ultimo", null);
  }

  public void recordar(String id) {
    prefs.edit().putString("ultimo", id).apply();
  }
}
