package com.example.fridge;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * config.properties(設定ファイル)を読み込んで、値を保持するクラス。
 * DBの接続先やテーブル名・列名など、環境ごとに変わる値はすべてここから取得する。
 */
public class Config {

  // ---- データベース接続 ----
  public final String dbUrl;
  public final String dbUser;
  public final String dbPassword;

  // ---- テーブル名・列名 ----
  public final String table;
  public final String colId;
  public final String colName;
  public final String colCategory;
  public final String colExpiry;
  public final String colPurchase;
  public final String colPrice;
  public final String colStore;
  public final String colNote;
  public final String colDispose; // 使い切った日の列
  public final boolean autoCreateTable;
  public final boolean idAutoIncrement; // ID列がDB側で自動採番されるか

  // ---- ジャンル ----
  public final List<String> categories;

  // ---- 購入店舗の候補(あらかじめ決めておくリスト。実際にDBにある店名は別途自動で加わる) ----
  public final List<String> storePresets;

  // ---- Webサーバー ----
  public final String host;
  public final int port;
  public final int warningDays;

  /** テーブル名・列名に使ってよい文字(英数字・アンダースコア・スペース・日本語) */
  private static final Pattern IDENTIFIER = Pattern.compile("[\\p{L}\\p{N}_ ]+");

  public Config(Path file) throws IOException {
    Properties p = new Properties();
    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      p.load(reader);
    }

    dbUrl = required(p, "db.url");
    dbUser = p.getProperty("db.user", "").trim();
    dbPassword = p.getProperty("db.password", "").trim();

    table = identifier(p, "table", "food_item");
    colId = identifier(p, "col.id", "id");
    colName = identifier(p, "col.name", "name");
    colCategory = identifier(p, "col.category", "category");
    colExpiry = identifier(p, "col.expiry", "expiry_date");
    colPurchase = identifier(p, "col.purchase", "purchase_date");
    colPrice = identifier(p, "col.price", "price");
    colStore = identifier(p, "col.store", "store");
    colNote = identifier(p, "col.note", "note");
    colDispose = identifier(p, "col.dispose", "disposal_date");
    autoCreateTable = Boolean.parseBoolean(p.getProperty("auto.create.table", "true").trim());
    idAutoIncrement = Boolean.parseBoolean(p.getProperty("id.auto.increment", "true").trim());

    // カンマ区切りのジャンルを1つずつに分ける
    List<String> list = new ArrayList<>();
    for (String s : p.getProperty("categories", "肉類,野菜類,生鮮食品,調味料").split(",")) {
      if (!s.trim().isEmpty()) {
        list.add(s.trim());
      }
    }
    if (list.isEmpty()) {
      throw new IllegalArgumentException("設定 categories が空です。");
    }
    categories = Collections.unmodifiableList(list);

    // 購入店舗の候補(未設定でもエラーにしない。空でもよい)
    List<String> stores = new ArrayList<>();
    for (String s : p.getProperty("store.presets", "").split(",")) {
      if (!s.trim().isEmpty()) {
        stores.add(s.trim());
      }
    }
    storePresets = Collections.unmodifiableList(stores);

    host = p.getProperty("server.host", "localhost").trim();
    port = integer(p, "server.port", 8080);
    warningDays = integer(p, "warning.days", 3);
  }

  /** 必須の設定値を取り出す(無ければエラー) */
  private static String required(Properties p, String key) {
    String v = p.getProperty(key);
    if (v == null || v.trim().isEmpty()) {
      throw new IllegalArgumentException("設定 " + key + " が見つかりません。config.properties を確認してください。");
    }
    return v.trim();
  }

  /** テーブル名・列名を取り出し、使える文字だけか確認する */
  private static String identifier(Properties p, String key, String defaultValue) {
    String v = p.getProperty(key, defaultValue).trim();
    if (!IDENTIFIER.matcher(v).matches()) {
      throw new IllegalArgumentException("設定 " + key + " に使えない文字が含まれています: " + v);
    }
    return v;
  }

  /** 整数の設定値を取り出す */
  private static int integer(Properties p, String key, int defaultValue) {
    String v = p.getProperty(key);
    if (v == null || v.trim().isEmpty()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(v.trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("設定 " + key + " は整数で指定してください: " + v);
    }
  }
}
