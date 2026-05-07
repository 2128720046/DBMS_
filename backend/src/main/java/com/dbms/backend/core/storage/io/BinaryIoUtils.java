package com.dbms.backend.core.storage.io;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Binary read/write helpers for fixed-length storage files.
 */
/**
 * DBMS 二进制底层读写工具类
 * 封装定长数据类型的写入与读取
 */
public class BinaryIoUtils {

    /**
     * 写入定长字符串，不足补 \0，截断超长部分
     */
    public static void writeFixedString(RandomAccessFile raf, String str, int length) throws IOException {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) 0);
        if (str != null) {
            byte[] strBytes = str.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(strBytes, 0, bytes, 0, Math.min(strBytes.length, length - 1));
        }
        raf.write(bytes);
    }

    /**
     * 读取定长字符串，以 \0 结尾
     */
    public static String readFixedString(RandomAccessFile raf, int length) throws IOException {
        byte[] bytes = new byte[length];
        raf.readFully(bytes);
        int end = 0;
        while (end < length && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, 0, end, StandardCharsets.UTF_8);
    }

    /**
     * 写入布尔值 (1 byte)
     */
    public static void writeBool(RandomAccessFile raf, boolean value) throws IOException {
        raf.writeByte(value ? 1 : 0);
    }

    /**
     * 读取布尔值
     */
    public static boolean readBool(RandomAccessFile raf) throws IOException {
        return raf.readByte() != 0;
    }

    /**
     * 写入 SystemTime (16 bytes，为了简化中期，暂存时间戳低高位 或 直接写 16字节字符串)
     * 系统验收要求 DATETIME 16 bytes
     */
    public static void writeDateTime(RandomAccessFile raf, long millis) throws IOException {
        raf.writeLong(millis); // 8 bytes
        raf.writeLong(0);      // dummy 8 bytes 补齐 16 bytes
    }

    /**
     * 读取 SystemTime 返回时间戳
     */
    public static long readDateTime(RandomAccessFile raf) throws IOException {
        long millis = raf.readLong();
        raf.readLong(); // 取出剩下的 8 bytes dummy
        return millis;
    }

    /**
     * 写入指定字节数的 0 填充，用于对齐到 4 的倍数。
     */
    public static void writeZeroPadding(RandomAccessFile raf, int bytes) throws IOException {
        if (bytes <= 0) return;
        raf.write(new byte[bytes]);
    }
}


