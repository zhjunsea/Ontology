package com.ocean.utilities;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 目录归档通用工具（与业务无关）。
 *
 * <p>把一组文件按相对路径打包为 zip 写入输出流。
 */
public final class ArchiveSupport {

    private ArchiveSupport() {
    }

    /**
     * 将 {@code files} 相对于 {@code root} 打包为 zip 写入 {@code out}。
     *
     * @param root  基准目录（用于计算 zip 内相对路径）
     * @param files 要打包的文件列表
     * @param out   输出流（调用方负责关闭）
     */
    public static void zip(Path root, List<Path> files, OutputStream out) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (Path f : files) {
                String rel = root.relativize(f).toString().replace('\\', '/');
                zos.putNextEntry(new ZipEntry(rel));
                Files.copy(f, zos);
                zos.closeEntry();
            }
            zos.finish();
        }
    }
}
