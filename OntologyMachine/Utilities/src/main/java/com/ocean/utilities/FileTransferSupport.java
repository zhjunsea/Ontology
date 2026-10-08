package com.ocean.utilities;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 文件上传/传输通用工具（与业务无关）。
 *
 * <p>提供 {@link MultipartFile} 存盘、文件名安全清洗、Content-Disposition 头生成等静态方法。
 */
public final class FileTransferSupport {

    private FileTransferSupport() {
    }

    /** 将上传文件保存到目标路径（覆盖已有文件），返回目标路径。 */
    public static Path save(MultipartFile file, Path target) throws IOException {
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    /** 去除文件系统非法字符（{@code \ / : * ? " < > |} → {@code _}）；{@code null} 返回 {@code "case"}。 */
    public static String safeName(String s) {
        return s == null ? "case" : s.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    /** 兼容中文文件名的 Content-Disposition（RFC 5987）。 */
    public static String contentDisposition(String filename) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"download.zip\"; filename*=UTF-8''" + encoded;
    }
}
