# 冷蔵庫の食材管理(Web版 / MySQL / HTML・CSS・JS)

ブラウザで使う食材管理アプリです。
- サーバー側: Java(JDK標準のWebサーバー) + MySQL(スキーマ i2c の food テーブル)
- 画面側: HTML・CSS・JavaScript(`src/main/resources/static` に分けて置いてあります)

サーバーはデータをJSON形式でやり取りするAPI(`/api/items` など)を提供するだけで、
画面の見た目や動きはすべて `index.html` / `style.css` / `script.js` の中にあります。
VS Code や Eclipse で、これらのファイルを直接開いて中身を確認・編集できます
(ただしブラウザでちゃんと動かすには、下の手順でサーバーを起動する必要があります。
 `index.html` をダブルクリックするだけでは、データの読み書きができません)。

## 必要なもの
- JDK 11 以上 (`java -version` で確認)
- Maven (`mvn -v` で確認)
- MySQL が起動していること

## 準備(初回だけ)
1. **MySQL Workbench で `setup.sql` を実行する**
   (food テーブルに CATEGORY 列を追加し、DATE OF DISPOSAL を NULL 可にします)
2. **`config.properties` の `db.password` に、MySQL のパスワードを書く**

## 起動手順
`pom.xml` があるフォルダ(このフォルダ)で、ターミナルを開いて:

```
mvn package
java -jar target/fridge-web.jar
```

「★ 起動しました」と出たら、ブラウザで http://localhost:8080/ を開きます。終了は Ctrl+C。

### VS Code の Run ボタンで動かす場合
1. 「フォルダを開く」で、`pom.xml` があるこのフォルダを直接開く
2. 右下の Maven 読み込みが終わるのを待つ
3. `src/main/java/com/example/fridge/Main.java` の `main` の上の **Run** をクリック
4. ブラウザで http://localhost:8080/ を開く

## 使い切った食材を隠す機能
「使い切った」ボタンを押すと、その食材の `DATE OF DISPOSAL` に今日の日付が入り、一覧から自動で隠れます。
「使い切った食材も表示する」にチェックを入れると、一覧の一番下にグレーで表示され、
「使い切りを取消」ボタンでいつでも元に戻せます(データが消えるわけではありません)。

## 購入日・購入店舗について
- **購入日は未入力でも登録できます**(賞味期限は引き続き必須です)。
- **購入店舗の候補**は、`config.properties` の `store.presets` に書いた店名が最初から出ます。
  それ以外の店名も、一度入力すれば次回から自動で候補に加わります。

## ファイルの構成
```
src/main/java/com/example/fridge/
  Main.java              起動処理
  Config.java             config.properties の読み込み
  FoodItem.java            食材1件分のデータ
  FoodRepository.java      DB操作の窓口(インターフェース)
  JdbcFoodRepository.java  実際のSQL(MySQL/PostgreSQL/SQL Server/SQLiteに対応)
  ApiServer.java           /api/... のJSON API と 画面ファイルの配信
  Json.java                簡易JSON変換(追加ライブラリ無しで実装)
  StaticFiles.java         jar実行時に画面ファイルを取り出す処理
src/main/resources/static/
  index.html               画面のHTML
  style.css                画面の見た目
  script.js                画面の動き(API呼び出し・一覧の描画・並び替え・検索・ジャンルの色分けなど)
```

## 既存の food テーブルとの対応
| 画面の項目 | food テーブルの列 |
|---|---|
| 食材名 | ITEM_NAME |
| ジャンル | CATEGORY (新規追加) |
| 賞味期限 | EXPIRATION DATE |
| 購入日 | PURCHASE_DATE |
| 値段 | PRACE (文字列型のまま使えます) |
| 購入店舗 | SHOP_NAME |
| 備考 | ITEM_MEMO |
| 使い切った日 | DATE OF DISPOSAL |

## うまくいかないとき
コンソールに出る `[ERROR]` の行を確認してください。
- 「Access denied」 → `db.user` / `db.password` を確認
- 「列 CATEGORY が見つかりません」「列 DATE OF DISPOSAL が見つかりません」 → `setup.sql` を実行する
- 「Communications link failure」 → MySQL が起動しているか、`localhost:3306` で合っているか確認
- ポートが使用中 → `server.port` を 8081 などに変更
