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

}
