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

}
