package com.example.fridge;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSONの読み書きを行う小さなユーティリティ。
 * 外部ライブラリ(Jackson等)を使わず標準機能だけで作るため、
 * このアプリで必要な範囲(文字列・数値・null からなるフラットな1階層のオブジェクト)だけに対応している。
 * フォームの入力欄はすべて文字列・数値・nullで表現できるので、これで十分。
 */
public final class Json {

    private Json() {
    }

    /**
     * {"a":"b","c":1} のような、ネストの無いJSONオブジェクトの文字列を
     * "キー→値の文字列" の Map に変換する(値がnullなら空文字列にする)。
     */
    public static Map<String, String> parseFlatObject(String text) {
        Map<String, String> map = new LinkedHashMap<>();
        int i = skipWs(text, 0);
        if (i >= text.length() || text.charAt(i) != '{') {
            return map; // 空や壊れたJSONは、空のMapとして扱う
        }
        i++;
        while (true) {
            i = skipWs(text, i);
            if (i >= text.length() || text.charAt(i) == '}') {
                break;
            }
            // --- キー(必ず文字列)を読む ---
            int[] pos = {i};
            String key = readString(text, pos);
            i = skipWs(text, pos[0]);
            if (i >= text.length() || text.charAt(i) != ':') {
                break;
            }
            i = skipWs(text, i + 1);

            // --- 値(文字列 / 数値 / true,false / null)を読む ---
            String value;
            if (i < text.length() && text.charAt(i) == '"') {
                pos[0] = i;
                value = readString(text, pos);
                i = pos[0];
            } else {
                int start = i;
                while (i < text.length() && text.charAt(i) != ',' && text.charAt(i) != '}') {
                    i++;
                }
                String raw = text.substring(start, i).trim();
                value = "null".equals(raw) ? null : raw;
            }
            map.put(key, value);

            i = skipWs(text, i);
            if (i < text.length() && text.charAt(i) == ',') {
                i++;
            }
        }
        return map;
    }

    /** ダブルクォートで囲まれた文字列を読み取り、pos[0] を読み終えた位置に進める */
    private static String readString(String text, int[] pos) {
        int i = pos[0];
        if (i >= text.length() || text.charAt(i) != '"') {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        i++;
        while (i < text.length() && text.charAt(i) != '"') {
            char ch = text.charAt(i);
            if (ch == '\\' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                switch (next) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    default: sb.append(next);
                }
                i += 2;
            } else {
                sb.append(ch);
                i++;
            }
        }
        pos[0] = i + 1; // 閉じの " の次へ
        return sb.toString();
    }

    private static int skipWs(String text, int i) {
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    /** 文字列をJSONの文字列リテラルとして安全に出力する(前後の " も含む) */
    public static String str(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.append('"').toString();
    }
}
