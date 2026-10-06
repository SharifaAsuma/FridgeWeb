package com.example.fridge;

import java.net.BindException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;

/**
 * アプリの起動クラス。
 * 1. config.properties を読む
 * 2. DBに接続して準備する
 * 3. Webサーバー(API + 画面のファイル配信)を起動する
 * 起動後、ブラウザで http://localhost:8080/ を開く。
 */

public class Main {

  public static void main(String[] args) {
    // 設定ファイルは、実行したフォルダの config.properties(引数で別の場所も指定可能)
    Path configPath = Paths.get(args.length > 0 ? args[0] : "config.properties");
    // 画面のファイル(index.html など)の場所。実行フォルダ直下の static を優先し、
    // 無ければ jar に同梱されているものを一時フォルダに展開して使う。
    Path staticDir = Paths.get("src/main/resources/static");

    try {
      // 1. 設定の読み込み
      Config config = new Config(configPath);

      // 2. DBの準備(接続確認・必要ならテーブル作成・列名チェック)
      JdbcFoodRepository repository = new JdbcFoodRepository(config);
      repository.initialize();

      if (!staticDir.toFile().isDirectory()) {
        staticDir = StaticFiles.extractToTempDir();
      }

      // 3. Webサーバーの起動
      ApiServer server = new ApiServer(config, repository, staticDir);
      server.start();
      System.out.println();
      System.out.println("★ 起動しました / Started: http://" + config.host + ":" + config.port + "/");
      System.out.println("  ブラウザで上のURLを開いてください。終了は Ctrl+C。");

    } catch (NoSuchFileException e) {
      System.err.println(
          "[ERROR] 設定ファイルが見つかりません / Config file not found: " + configPath.toAbsolutePath());
      System.err.println("  → config.properties があるフォルダで実行してください。");
    } catch (IllegalArgumentException e) {
      System.err.println("[ERROR] 設定ファイルの内容に問題があります / Invalid config: " + e.getMessage());
    } catch (SQLException e) {
      System.err.println("[ERROR] DBの準備に失敗しました / Database error: " + e.getMessage());
      System.err
          .println("  → db.url / db.user / db.password / table / col.* の設定と、DBが起動しているかを確認してください。");
    } catch (BindException e) {
      System.err.println("[ERROR] ポートが使用中です / Port already in use: " + e.getMessage());
      System.err.println("  → config.properties の server.port を別の番号(例: 8081)に変えてください。");
    } catch (Exception e) {
      System.err.println("[ERROR] 起動に失敗しました / Failed to start:");
      e.printStackTrace();
    }
  }
}
