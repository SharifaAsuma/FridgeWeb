package com.example.fridge;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 食材1件分のデータ(DBの1行に対応)。
 * DBの読み書き(JdbcFoodRepository)とAPI(ApiServer)の間の受け渡しに使う。
 */
public class FoodItem {

    private final int id;                 // 食材ID。新規登録前は 0
    private final String name;            // 食材名
    private final String category;        // ジャンル(肉類 など)
    private final LocalDate expiryDate;   // 賞味期限(未設定なら null)
    private final LocalDate purchaseDate; // 購入日
    private final int price;              // 値段(円)
    private final String store;           // 購入したスーパー
    private final String note;            // 備考
    private final LocalDate disposalDate; // 使い切った日(まだ使い切っていなければ null)

    public FoodItem(int id, String name, String category, LocalDate expiryDate,
                    LocalDate purchaseDate, int price, String store, String note,
                    LocalDate disposalDate) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.expiryDate = expiryDate;
        this.purchaseDate = purchaseDate;
        this.price = price;
        this.store = store;
        this.note = note;
        this.disposalDate = disposalDate;
    }

    /**
     * 今日から賞味期限までの残り日数を返す。
     * 今日が期限なら 0、期限切れならマイナス。賞味期限が未設定なら null。
     */
    public Long daysUntilExpiry() {
        if (expiryDate == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
    }

    /** 使い切った(使い切った日が入っている)かどうか */
    public boolean isDisposed() {
        return disposalDate != null;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public int getPrice() { return price; }
    public String getStore() { return store; }
    public String getNote() { return note; }
    public LocalDate getDisposalDate() { return disposalDate; }
}
