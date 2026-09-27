-- =====================================================================
--  food テーブルを、このアプリで使える形にします(1回だけ実行)
--
--  【実行のしかた】
--   1. MySQL Workbench で、このファイルの中身を Query タブに貼り付ける
--   2. 稲妻マーク(⚡)の「Execute」ボタンで実行する
--
--  ・既存のデータは消えません。
--  ・すでに CATEGORY 列がある場合や、DATE OF DISPOSAL が NULL 可の場合は、
--    該当する行がエラーになりますが、無視して次の行を実行してください。
-- =====================================================================
USE i2c;

-- 1) ジャンル用の CATEGORY 列を追加する(既存の行には「未分類」が自動で入ります)
ALTER TABLE food ADD COLUMN CATEGORY VARCHAR(50) NOT NULL DEFAULT '未分類';

-- 2) 「使い切った日」の列(DATE OF DISPOSAL)が、まだ使い切っていないことを表せるよう
--    NULL を許可する形にする(すでに NULL 可なら、このままでも問題ありません)
ALTER TABLE food MODIFY `DATE OF DISPOSAL` DATE NULL;

-- 3) 購入日(PURCHASE_DATE)を未入力でも保存できるよう、NULL を許可する形にする
--    (もとの型が TIMESTAMP のため、型はそのままにして NULL 許可だけ変更します)
ALTER TABLE food MODIFY PURCHASE_DATE TIMESTAMP NULL DEFAULT NULL;

-- 確認用(CATEGORY 列が増え、DATE OF DISPOSAL・PURCHASE_DATE が Null=YES になっていればOK)
SELECT * FROM food;
DESCRIBE food;

-- 【元に戻すとき用】通常は実行しないでください
-- ALTER TABLE food DROP COLUMN CATEGORY;
