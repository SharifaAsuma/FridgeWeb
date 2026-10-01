package com.example.fridge;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Webサーバー本体。JDK標準の HttpServer を使っているので、追加ライブラリは不要。
 * 画面(HTML/CSS/JS)は src/main/resources/static の静的ファイルをそのまま返し、
 * データのやり取りは /api/... のJSON APIで行う(ブラウザ側の script.js が fetch() で呼び出す)。
 *
 * APIの一覧:
 * GET /api/items 一覧取得 (?category=肉類 で絞り込み、?includeDisposed=true で使い切み含む)
 * GET /api/items/{id} 1件取得
 * POST /api/items 追加
 * PUT /api/items/{id} 更新
 * POST /api/items/{id}/dispose 使い切った日を今日にする(一覧から隠れる)
 * POST /api/items/{id}/restore 使い切った状態を取り消す(一覧に戻す)
 * DELETE /api/items/{id} 削除
 * GET /api/meta ジャンル一覧・購入店舗の候補・設定値(警告日数など)
 */
public class ApiServer {

  private final Config cfg;
  private final FoodRepository repo;
  private final HttpServer server;
  private final Path staticDir; // HTML/CSS/JS を置いているフォルダ

  /** URLの中の {id} 部分を取り出すための正規表現。例: /api/items/12/dispose */
  private static final Pattern ITEM_PATH =
      Pattern.compile("^/api/items/(\\d+)(?:/(dispose|restore))?$");

  public ApiServer(Config cfg, FoodRepository repo, Path staticDir) throws IOException {
    this.cfg = cfg;
    this.repo = repo;
    this.staticDir = staticDir;
    this.server = HttpServer.create(new InetSocketAddress(cfg.host, cfg.port), 0);
    this.server.createContext("/", this::handle);
    this.server.setExecutor(Executors.newFixedThreadPool(4));
  }

  public void start() {
    server.start();
  }

  // =====================================================================
  // リクエストの振り分け
  // =====================================================================

  private void handle(HttpExchange ex) throws IOException {
    try {
      String path = ex.getRequestURI().getPath();
      String method = ex.getRequestMethod();

      if (path.equals("/api/items") && method.equals("GET")) {
        listItems(ex);
      } else if (path.equals("/api/items") && method.equals("POST")) {
        createItem(ex);
      } else if (path.equals("/api/meta") && method.equals("GET")) {
        sendMeta(ex);
      } else if (path.startsWith("/api/items/")) {
        handleItemPath(ex, path, method);
      } else if (path.startsWith("/api/")) {
        sendJson(ex, 404, "{\"error\":" + Json.str("APIが見つかりません") + "}");
      } else {
        serveStatic(ex, path); // それ以外は静的ファイル(HTML/CSS/JS)を返す
      }
    } catch (SQLException e) {
      e.printStackTrace();
      sendJson(ex, 500, "{\"error\":" + Json.str("DBエラー: " + e.getMessage()) + "}");
    } catch (Exception e) {
      e.printStackTrace();
      sendJson(ex, 500, "{\"error\":" + Json.str("エラー: " + e.getMessage()) + "}");
    } finally {
      ex.close();
    }
  }

  /** /api/items/{id} 、/api/items/{id}/dispose 、/api/items/{id}/restore の振り分け */
  private void handleItemPath(HttpExchange ex, String path, String method)
      throws IOException, SQLException {
    Matcher m = ITEM_PATH.matcher(path);
    if (!m.matches()) {
      sendJson(ex, 404, "{\"error\":" + Json.str("APIが見つかりません") + "}");
      return;
    }
    int id = Integer.parseInt(m.group(1));
    String action = m.group(2); // "dispose" / "restore" / null

    if (action == null && method.equals("GET")) {
      getItem(ex, id);
    } else if (action == null && method.equals("PUT")) {
      updateItem(ex, id);
    } else if (action == null && method.equals("DELETE")) {
      deleteItem(ex, id);
    } else if ("dispose".equals(action) && method.equals("POST")) {
      disposeItem(ex, id);
    } else if ("restore".equals(action) && method.equals("POST")) {
      restoreItem(ex, id);
    } else {
      sendJson(ex, 404, "{\"error\":" + Json.str("APIが見つかりません") + "}");
    }
  }

  // =====================================================================
  // 各APIの処理
  // =====================================================================

  /** GET /api/items : 一覧をJSON配列で返す */
  private void listItems(HttpExchange ex) throws SQLException, IOException {
    Map<String, String> query = parseQuery(ex.getRequestURI().getRawQuery());
    String category = query.get("category");
    if (category != null && category.isEmpty()) {
      category = null;
    }
    boolean includeDisposed = "true".equals(query.get("includeDisposed"));

    List<FoodItem> items = repo.findAll(category, includeDisposed);
    StringBuilder b = new StringBuilder("[");
    for (int i = 0; i < items.size(); i++) {
      if (i > 0) {
        b.append(",");
      }
      b.append(toJson(items.get(i)));
    }
    b.append("]");
    sendJson(ex, 200, b.toString());
  }

  /** GET /api/items/{id} : 1件をJSONで返す */
  private void getItem(HttpExchange ex, int id) throws SQLException, IOException {
    FoodItem item = repo.findById(id);
    if (item == null) {
      sendJson(ex, 404, "{\"error\":" + Json.str("食材が見つかりません") + "}");
      return;
    }
    sendJson(ex, 200, toJson(item));
  }

  /** POST /api/items : 追加。成功したら作成後の一覧を返す */
  private void createItem(HttpExchange ex) throws SQLException, IOException {
    Map<String, String> body = Json.parseFlatObject(readBody(ex));
    ValidationResult r = parseAndValidate(body, 0);
    if (!r.errors.isEmpty()) {
      sendJson(ex, 400, errorsJson(r.errors));
      return;
    }
    repo.insert(r.item);
    sendJson(ex, 200, "{\"ok\":true}");
  }

  /** PUT /api/items/{id} : 更新 */
  private void updateItem(HttpExchange ex, int id) throws SQLException, IOException {
    Map<String, String> body = Json.parseFlatObject(readBody(ex));
    ValidationResult r = parseAndValidate(body, id);
    if (!r.errors.isEmpty()) {
      sendJson(ex, 400, errorsJson(r.errors));
      return;
    }
    int n = repo.update(r.item);
    if (n == 0) {
      sendJson(ex, 404, "{\"error\":" + Json.str("更新対象が見つかりませんでした") + "}");
      return;
    }
    sendJson(ex, 200, "{\"ok\":true}");
  }

  /** DELETE /api/items/{id} : 削除 */
  private void deleteItem(HttpExchange ex, int id) throws SQLException, IOException {
    int n = repo.delete(id);
    sendJson(ex, n > 0 ? 200 : 404,
        n > 0 ? "{\"ok\":true}" : "{\"error\":" + Json.str("削除対象が見つかりませんでした") + "}");
  }

  /** POST /api/items/{id}/dispose : 「使い切った日」を今日にする → 一覧から隠れる */
  private void disposeItem(HttpExchange ex, int id) throws SQLException, IOException {
    int n = repo.markDisposed(id);
    sendJson(ex, n > 0 ? 200 : 404,
        n > 0 ? "{\"ok\":true}" : "{\"error\":" + Json.str("対象が見つかりませんでした") + "}");
  }

  /** POST /api/items/{id}/restore : 使い切った状態を取り消す(一覧に戻す) */
  private void restoreItem(HttpExchange ex, int id) throws SQLException, IOException {
    int n = repo.unmarkDisposed(id);
    sendJson(ex, n > 0 ? 200 : 404,
        n > 0 ? "{\"ok\":true}" : "{\"error\":" + Json.str("対象が見つかりませんでした") + "}");
  }

  /** GET /api/meta : ジャンル一覧、購入店舗の候補、警告日数を返す(画面の初期化に使う) */
  private void sendMeta(HttpExchange ex) throws SQLException, IOException {
    StringBuilder b = new StringBuilder("{");
    b.append("\"categories\":[");
    for (int i = 0; i < cfg.categories.size(); i++) {
      if (i > 0)
        b.append(",");
      b.append(Json.str(cfg.categories.get(i)));
    }
    b.append("],\"stores\":[");
    // あらかじめ決めたリスト(config.properties の store.presets)を先に、
    // 実際にDBで使われている店名のうち、まだ無いものを後ろに足す(重複は除く)
    List<String> stores = new java.util.ArrayList<>(cfg.storePresets);
    for (String s : repo.distinctStores()) {
      if (!stores.contains(s)) {
        stores.add(s);
      }
    }
    for (int i = 0; i < stores.size(); i++) {
      if (i > 0)
        b.append(",");
      b.append(Json.str(stores.get(i)));
    }
    b.append("],\"warningDays\":").append(cfg.warningDays);
    b.append("}");
    sendJson(ex, 200, b.toString());
  }

  // =====================================================================
  // 入力チェック(サーバー側でも必ず検証する。ブラウザ側の入力チェックだけに頼らない)
  // =====================================================================

  private static class ValidationResult {
    FoodItem item;
    java.util.List<String> errors = new java.util.ArrayList<>();
  }

  private ValidationResult parseAndValidate(Map<String, String> f, int id) {
    ValidationResult r = new ValidationResult();

    String name = trimOrEmpty(f.get("name"));
    if (name.isEmpty()) {
      r.errors.add("食材名を入力してください。");
    }

    String category = trimOrEmpty(f.get("category"));
    if (category.isEmpty()) {
      r.errors.add("ジャンルを選んでください。");
    }

    // 変更前
    // LocalDate expiry = parseDate("賞味期限", f.get("expiry"), r.errors);

    // 変更後（賞味期限を必須項目ではなく任意入力に変更）
    LocalDate expiry = parseOptionalDate("賞味期限", f.get("expiry"), r.errors);

    LocalDate purchase = parseOptionalDate("購入日", f.get("purchase"), r.errors);

    int price = 0;
    String priceText = trimOrEmpty(f.get("price"));
    if (!priceText.isEmpty()) {
      try {
        price = Integer.parseInt(priceText);
        if (price < 0) {
          r.errors.add("値段は0以上で入力してください。");
        }
      } catch (NumberFormatException e) {
        r.errors.add("値段は半角の整数で入力してください。");
      }
    }

    r.item = new FoodItem(id, name, category, expiry, purchase, price, trimOrEmpty(f.get("store")),
        trimOrEmpty(f.get("note")), null);
    return r;
  }

  private LocalDate parseDate(String label, String text, List<String> errors) {
    try {
      return LocalDate.parse(trimOrEmpty(text));
    } catch (DateTimeParseException e) {
      errors.add(label + "を正しい日付(例: 2026-09-30)で入力してください。");
      return null;
    }
  }

  /** parseDate の「未入力を許す」版。空欄なら null を返し、エラーにはしない(購入日など任意項目用)。 */
  private LocalDate parseOptionalDate(String label, String text, List<String> errors) {
    String trimmed = trimOrEmpty(text);
    if (trimmed.isEmpty()) {
      return null;
    }
    try {
      return LocalDate.parse(trimmed);
    } catch (DateTimeParseException e) {
      errors.add(label + "を正しい日付(例: 2026-09-30)で入力してください。");
      return null;
    }
  }

  private String trimOrEmpty(String s) {
    return s == null ? "" : s.trim();
  }

  // =====================================================================
  // JSON変換・通信の補助
  // =====================================================================

  /** FoodItem 1件をJSONオブジェクトの文字列に変換する */
  private String toJson(FoodItem it) {
    Long days = it.daysUntilExpiry();
    StringBuilder b = new StringBuilder("{");
    b.append("\"id\":").append(it.getId());
    b.append(",\"name\":").append(Json.str(it.getName()));
    b.append(",\"category\":").append(Json.str(it.getCategory()));
    b.append(",\"expiry\":")
        .append(it.getExpiryDate() == null ? "null" : Json.str(it.getExpiryDate().toString()));
    b.append(",\"purchase\":")
        .append(it.getPurchaseDate() == null ? "null" : Json.str(it.getPurchaseDate().toString()));
    b.append(",\"price\":").append(it.getPrice());
    b.append(",\"store\":").append(Json.str(it.getStore()));
    b.append(",\"note\":").append(Json.str(it.getNote()));
    b.append(",\"disposed\":").append(it.isDisposed());
    b.append(",\"daysUntilExpiry\":").append(days == null ? "null" : days);
    b.append("}");
    return b.toString();
  }

  private String errorsJson(List<String> errors) {
    StringBuilder b = new StringBuilder("{\"errors\":[");
    for (int i = 0; i < errors.size(); i++) {
      if (i > 0)
        b.append(",");
      b.append(Json.str(errors.get(i)));
    }
    b.append("]}");
    return b.toString();
  }

  /** "a=1&b=2" 形式の文字列(URLのクエリ)を Map に変換する */
  private Map<String, String> parseQuery(String raw) {
    Map<String, String> map = new LinkedHashMap<>();
    if (raw == null || raw.isEmpty()) {
      return map;
    }
    for (String pair : raw.split("&")) {
      int i = pair.indexOf('=');
      String key = i < 0 ? pair : pair.substring(0, i);
      String value = i < 0 ? "" : pair.substring(i + 1);
      map.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
          URLDecoder.decode(value, StandardCharsets.UTF_8));
    }
    return map;
  }

  private String readBody(HttpExchange ex) throws IOException {
    return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
  }

  private void sendJson(HttpExchange ex, int status, String json) throws IOException {
    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
    ex.sendResponseHeaders(status, bytes.length);
    try (OutputStream os = ex.getResponseBody()) {
      os.write(bytes);
    }
  }

  // =====================================================================
  // 静的ファイル(HTML/CSS/JS)の配信
  // =====================================================================

  /** static フォルダの中身をそのまま返す。 "/" は index.html にする */
  private void serveStatic(HttpExchange ex, String path) throws IOException {
    if (path.equals("/") || path.isEmpty()) {
      path = "/index.html";
    }
    // ".." を使ったフォルダ抜け出しを防ぐ
    Path file = staticDir.resolve("." + path).normalize();
    if (!file.startsWith(staticDir) || !Files.isRegularFile(file)) {
      sendJson(ex, 404, "{\"error\":\"not found\"}");
      return;
    }
    byte[] bytes = Files.readAllBytes(file);
    ex.getResponseHeaders().set("Content-Type", contentType(file.toString()));
    ex.sendResponseHeaders(200, bytes.length);
    try (OutputStream os = ex.getResponseBody()) {
      os.write(bytes);
    }
  }

  private String contentType(String filename) {
    if (filename.endsWith(".html"))
      return "text/html; charset=UTF-8";
    if (filename.endsWith(".css"))
      return "text/css; charset=UTF-8";
    if (filename.endsWith(".js"))
      return "application/javascript; charset=UTF-8";
    return "application/octet-stream";
  }
}
