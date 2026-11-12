package cl.itatagroup.canales;

import org.json.JSONException;
import org.json.JSONObject;

/** Un canal: lo que se muestra y el enlace de donde viene su señal. */
public final class Canal {
  /** Identificador estable: el tvg-id de la lista o, si no trae, el nombre normalizado. */
  public final String id;
  public final String nombre;
  public final String categoria;
  public final String url;
  /** Resolución que declara la lista ("720p"), o vacío. */
  public final String calidad;
  /** La lista avisa que no transmite todo el día. */
  public final boolean noSiempre;
  /** De dónde salió: "iptv-org" o el nombre de una lista propia. */
  public final String fuente;
  public int numero;

  public Canal(String id, String nombre, String categoria, String url, String calidad, boolean noSiempre, String fuente) {
    this.id = id;
    this.nombre = nombre;
    this.categoria = categoria;
    this.url = url;
    this.calidad = calidad;
    this.noSiempre = noSiempre;
    this.fuente = fuente;
  }

  public Canal conCategoria(String otra) {
    Canal c = new Canal(id, nombre, otra, url, calidad, noSiempre, fuente);
    c.numero = numero;
    return c;
  }

  /** Dos canales son el mismo si tienen el mismo id, aunque vengan de búsquedas distintas. */
  @Override
  public boolean equals(Object o) {
    return o instanceof Canal && ((Canal) o).id.equals(id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  public JSONObject json() throws JSONException {
    return new JSONObject()
        .put("id", id).put("nombre", nombre).put("categoria", categoria).put("url", url)
        .put("calidad", calidad).put("noSiempre", noSiempre).put("fuente", fuente).put("numero", numero);
  }

  public static Canal desde(JSONObject o) {
    Canal c = new Canal(o.optString("id"), o.optString("nombre"), o.optString("categoria"), o.optString("url"),
        o.optString("calidad"), o.optBoolean("noSiempre"), o.optString("fuente"));
    c.numero = o.optInt("numero");
    return c;
  }
}
