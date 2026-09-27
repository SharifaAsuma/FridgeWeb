package com.example.fridge;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * jar として実行したとき(mvn package で作った fridge-web.jar)用のクラス。
 * 開発中は src/main/resources/static のファイルをそのまま使うが、
 * jar化すると static フォルダは jar の中に入ってしまうので、
 * 中身を一時フォルダに複製してから ApiServer に渡す。
 */
final class StaticFiles {

    private StaticFiles() {
    }

    static Path extractToTempDir() throws IOException, URISyntaxException {
        Path tempDir = Files.createTempDirectory("fridge-static-");
        URI uri = StaticFiles.class.getResource("/static").toURI();

        Map<String, String> env = new HashMap<>();
        try (FileSystem fs = "jar".equals(uri.getScheme()) ? FileSystems.newFileSystem(uri, env) : null) {
            Path source = fs != null ? fs.getPath("/static") : Path.of(uri);
            copyDir(source, tempDir);
        }
        return tempDir;
    }

    private static void copyDir(Path source, Path target) throws IOException {
        try (var stream = Files.walk(source)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                Path dest = target.resolve(source.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    try (InputStream in = Files.newInputStream(p)) {
                        Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }
}
