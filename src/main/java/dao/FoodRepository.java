package com.example.fridge;

import java.sql.SQLException;
import java.util.List;

/**
 * 食材データの保存・取得の窓口(インターフェース)。
 * ApiServer は「どのDBか」を意識せず、この窓口だけを使う。
 * 実際のDB処理は JdbcFoodRepository が担当する。
 */
public interface FoodRepository {

    /**
     * 食材一覧を賞味期限が近い順に取得する。
     * @param category         絞り込むジャンル。null なら全ジャンル
     * @param includeDisposed  true なら「使い切った」食材も含める。false なら隠す
     */
    List<FoodItem> findAll(String category, boolean includeDisposed) throws SQLException;

    /** IDで1件取得する。無ければ null */
    FoodItem findById(int id) throws SQLException;

    /** 新規登録する */
    void insert(FoodItem item) throws SQLException;

    /** 更新する。更新した件数(0なら対象なし)を返す */
    int update(FoodItem item) throws SQLException;

    /** 削除する。削除した件数(0なら対象なし)を返す */
    int delete(int id) throws SQLException;

    /** 指定のIDを「使い切った」状態にする(使い切った日 = 今日)。更新した件数を返す */
    int markDisposed(int id) throws SQLException;

    /** 「使い切った」を取り消す(使い切った日を空に戻す)。更新した件数を返す */
    int unmarkDisposed(int id) throws SQLException;

    /** 既存データの購入店舗の候補(入力補助のドロップダウン用) */
    List<String> distinctStores() throws SQLException;
}
