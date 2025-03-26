import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class JmComicCrawler {

    //配置类
    public static class JmConfig {
        public String domain = "https://jmcomic1.org";
        public String downloadPath = "./downloads";
        public Proxy proxy = Proxy.NO_PROXY;
        public int threads = 3;

        public static JmConfig createDefault() {
            return new JmConfig();
        }
    }

    //数据实体
    public static class Album {
        public String id;
        public String title;
        public List<Chapter> chapters = new ArrayList<>();
    }

    public static class Chapter {
        public String id;
        public String title;
    }

    public static class Image {
        public String id;
        public String url;
    }

    //异常类
    public static class JmException extends RuntimeException {
        public JmException(String message) {
            super(message);
        }
    }

    //核心客户端
    public static class JmClient {
        private final JmConfig config;
        private final ExecutorService executor;

        public JmClient(JmConfig config) {
            this.config = config;
            this.executor = Executors.newFixedThreadPool(config.threads);
        }

        public void downloadAlbum(String albumId) {
            Album album = fetchAlbum(albumId);
            List<Future<?>> futures = new ArrayList<>();

            for (Chapter chapter : album.chapters) {
                futures.add(executor.submit(() -> {
                    List<Image> images = fetchChapterImages(chapter.id);
                    images.forEach(image -> downloadImage(image, buildSavePath(album, chapter, image)));
                }));
            }

            waitAll(futures);
        }

        private String buildSavePath(Album album, Chapter chapter, Image image) {
            return String.format("%s/%s/%s/%s.jpg",
                    config.downloadPath,
                    sanitizeFilename(album.title),
                    sanitizeFilename(chapter.title),
                    image.id);
        }

        private String sanitizeFilename(String name) {
            return name.replaceAll("[\\\\/:*?\"<>|]", "_");
        }

        private Album fetchAlbum(String albumId) {
            String json = httpGet(String.format("%s/album/%s?t=%d", 
                config.domain, albumId, System.currentTimeMillis()));
            
            // 模拟解析JSON响应
            Album album = new Album();
            album.id = albumId;
            album.title = "Sample Album";
            
            Chapter ch = new Chapter();
            ch.id = "1";
            ch.title = "Chapter 1";
            album.chapters.add(ch);
            
            return album;
        }

        private List<Image> fetchChapterImages(String chapterId) {
            String json = httpGet(String.format("%s/chapter/%s?t=%d", 
                config.domain, chapterId, System.currentTimeMillis()));
            
            // 模拟解析响应
            List<Image> images = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                Image img = new Image();
                img.id = String.valueOf(i);
                img.url = String.format("%s/images/%s/%02d.jpg", 
                    config.domain, chapterId, i);
                images.add(img);
            }
            return images;
        }

        private String httpGet(String url) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection(config.proxy);
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10_000);
                
                if (conn.getResponseCode() == 200) {
                    try (var is = conn.getInputStream()) {
                        return new String(is.readAllBytes());
                    }
                }
            } catch (Exception e) {
                throw new JmException("HTTP请求失败: " + e.getMessage());
            }
            return "";
        }

        //图片处理
        private void downloadImage(Image image, String savePath) {
            try {
                byte[] data = httpGetBytes(image.url);
                byte[] decrypted = decryptImage(data);
                saveImage(decrypted, savePath);
            } catch (Exception e) {
                System.err.println("下载失败: " + image.url + " - " + e.getMessage());
            }
        }

        private byte[] httpGetBytes(String url) throws IOException {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection(config.proxy);
            try (var is = conn.getInputStream()) {
                return is.readAllBytes();
            }
        }

        private byte[] decryptImage(byte[] data) {
            // 解密逻辑
            byte[] key = {0x6A, 0x6D, 0x63, 0x64}; // 密钥
            byte[] decrypted = new byte[data.length];
            for (int i = 0; i < data.length; i++) {
                decrypted[i] = (byte) (data[i] ^ key[i % key.length]);
            }
            return decrypted;
        }

        private void saveImage(byte[] data, String path) throws IOException {
            Path savePath = Path.of(path);
            Files.createDirectories(savePath.getParent());
            
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
            String format = path.endsWith(".png") ? "png" : "jpg";
            ImageIO.write(img, format, savePath.toFile());
        }

        // 方法
        private void waitAll(List<Future<?>> futures) {
            futures.forEach(f -> {
                try {
                    f.get();
                } catch (InterruptedException | ExecutionException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        public void shutdown() {
            executor.shutdown();
        }
    }

    // 使用实例
    public static void main(String[] args) {
        JmConfig config = new JmConfig();
        config.threads = 5;
        config.downloadPath = "./jm_downloads";

        JmClient client = new JmClient(config);
        try {
            client.downloadAlbum("114514");
        } finally {
            client.shutdown();
        }
        System.out.println("下载完成!");
    }
}
