package com.dbms.backend.modules.index.infrastructure;

import com.dbms.backend.modules.index.domain.IndexGateway;
import com.dbms.backend.core.storage.config.StorageEngineConfig;
import com.dbms.backend.core.storage.io.BinaryIoUtils;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

/**
 * 索引网关占位实现。
 * <p>
 * 文件已提前创建。后续实现时在本类内补充 .tid 索引描述文件和 .ix 索引数据文件的
 * 读写逻辑即可，不需要新增接口或改 SQL 执行层。
 * </p>
 */
@Repository
public class TodoIndexGatewayImpl implements IndexGateway {

    private static final int FIELD_BLOCK_SIZE = 160;
    private static final int TID_BLOCK_SIZE = 904;

    private static class FieldMeta {
        String name;
        int type;
        int param;
        int length;
        int offset;
    }

    private static class IndexEntry {
        List<Comparable<?>> keys;
        long recordOffset;

        IndexEntry(List<Comparable<?>> keys, long recordOffset) {
            this.keys = keys;
            this.recordOffset = recordOffset;
        }
    }

    @Override
    public void createIndex(String schemaName, String tableName, String indexName,
                            List<String> columns, boolean unique, boolean ascending) {
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("索引字段不能为空");
        }
        if (columns.size() > 2) {
            throw new IllegalArgumentException("当前最多支持两个字段索引");
        }

        File schemaDir = new File(StorageEngineConfig.getDATA_DIR() + File.separator + schemaName);
        if (!schemaDir.exists()) {
            throw new IllegalArgumentException("数据库不存在: " + schemaName);
        }

        File tidFile = new File(schemaDir, tableName + ".tid");
        if (!tidFile.exists()) {
            throw new IllegalArgumentException("索引描述文件不存在: " + tidFile.getAbsolutePath());
        }
        File trdFile = new File(schemaDir, tableName + ".trd");
        if (!trdFile.exists()) {
            throw new IllegalArgumentException("记录文件不存在: " + trdFile.getAbsolutePath());
        }

        String ixPath = new File(schemaDir, indexName + ".ix").getAbsolutePath();
        try {
            writeTidEntry(tidFile, indexName, unique, ascending, columns, trdFile.getAbsolutePath(), ixPath);
        } catch (IOException e) {
            throw new RuntimeException("写入 .tid 失败: " + e.getMessage(), e);
        }

        // 立即重建索引文件
        rebuildIndex(schemaName, tableName, indexName);
    }

    @Override
    public void dropIndex(String schemaName, String tableName, String indexName) {
        File schemaDir = new File(StorageEngineConfig.getDATA_DIR() + File.separator + schemaName);
        File tidFile = new File(schemaDir, tableName + ".tid");
        if (!tidFile.exists()) {
            return;
        }
        IndexTidIo.IndexDefinition entry;
        try {
            entry = IndexTidIo.find(tidFile, indexName);
        } catch (IOException e) {
            throw new RuntimeException("读取 .tid 失败: " + e.getMessage(), e);
        }
        if (entry == null) {
            return;
        }
        try {
            clearTidEntry(tidFile, entry.offset);
        } catch (IOException e) {
            throw new RuntimeException("更新 .tid 失败: " + e.getMessage(), e);
        }
        if (entry.indexFile != null && !entry.indexFile.isBlank()) {
            new File(entry.indexFile).delete();
        } else {
            new File(schemaDir, indexName + ".ix").delete();
        }
    }

    @Override
    public List<Map<String, Object>> listIndexes(String schemaName, String tableName) {
        File schemaDir = new File(StorageEngineConfig.getDATA_DIR() + File.separator + schemaName);
        File tidFile = new File(schemaDir, tableName + ".tid");
        if (!tidFile.exists()) {
            return List.of();
        }
        try {
            List<IndexTidIo.IndexDefinition> entries = IndexTidIo.read(tidFile);
            List<Map<String, Object>> results = new ArrayList<>();
            for (IndexTidIo.IndexDefinition entry : entries) {
                Map<String, Object> row = new HashMap<>();
                row.put("name", entry.name);
                row.put("columns", String.join(",", entry.columns));
                row.put("unique", entry.unique);
                row.put("ascending", entry.ascending);
                row.put("indexFile", entry.indexFile);
                results.add(row);
            }
            return results;
        } catch (IOException e) {
            throw new RuntimeException("读取索引列表失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void rebuildIndex(String schemaName, String tableName, String indexName) {
        File schemaDir = new File(StorageEngineConfig.getDATA_DIR() + File.separator + schemaName);
        File tidFile = new File(schemaDir, tableName + ".tid");
        if (!tidFile.exists()) {
            throw new IllegalArgumentException("索引描述文件不存在");
        }
        IndexTidIo.IndexDefinition entry;
        try {
            entry = IndexTidIo.find(tidFile, indexName);
        } catch (IOException e) {
            throw new RuntimeException("读取 .tid 失败: " + e.getMessage(), e);
        }
        if (entry == null) {
            throw new IllegalArgumentException("索引不存在: " + indexName);
        }

        File trdFile = new File(entry.recordFile);
        if (!trdFile.exists()) {
            throw new IllegalArgumentException("记录文件不存在");
        }
        File tdfFile = new File(schemaDir, tableName + ".tdf");
        if (!tdfFile.exists()) {
            throw new IllegalArgumentException("表定义文件不存在");
        }
        List<FieldMeta> metas;
        try {
            metas = parseTdf(tdfFile);
        } catch (IOException e) {
            throw new RuntimeException("解析 .tdf 失败: " + e.getMessage(), e);
        }

        List<FieldMeta> keyMetas = new ArrayList<>();
        for (String col : entry.columns) {
            FieldMeta meta = metas.stream().filter(m -> m.name.equalsIgnoreCase(col)).findFirst().orElse(null);
            if (meta == null) {
                throw new IllegalArgumentException("索引字段不存在: " + col);
            }
            keyMetas.add(meta);
        }

        int recordLength = 4;
        for (FieldMeta meta : metas) {
            recordLength += meta.length;
        }

        List<IndexEntry> indexEntries = new ArrayList<>();
        Set<String> uniqueKeys = entry.unique ? new HashSet<>() : null;
        try (RandomAccessFile raf = new RandomAccessFile(trdFile, "r")) {
            long fileLength = raf.length();
            long pos = 0;
            while (pos + recordLength <= fileLength) {
                raf.seek(pos);
                int status = raf.readInt();
                if (status == 1) {
                    List<Comparable<?>> keys = new ArrayList<>();
                    for (FieldMeta km : keyMetas) {
                        raf.seek(pos + 4L + km.offset);
                        keys.add(readComparable(raf, km));
                    }
                    String uniq = toUniqueKey(keys);
                    if (uniqueKeys != null && !uniqueKeys.add(uniq)) {
                        throw new IllegalStateException("唯一索引冲突: " + uniq);
                    }
                    indexEntries.add(new IndexEntry(keys, pos));
                }
                pos += recordLength;
            }
        } catch (IOException e) {
            throw new RuntimeException("扫描 .trd 失败: " + e.getMessage(), e);
        }

        Comparator<IndexEntry> comparator = (a, b) -> compareKeys(a.keys, b.keys);
        if (!entry.ascending) {
            comparator = comparator.reversed();
        }
        indexEntries.sort(comparator);

        File ixFile = entry.indexFile != null && !entry.indexFile.isBlank()
                ? new File(entry.indexFile)
                : new File(schemaDir, indexName + ".ix");
        try {
            if (!ixFile.exists()) {
                ixFile.getParentFile().mkdirs();
                ixFile.createNewFile();
            }
            // Build and persist a B+ tree index file for fast equality lookups.
            List<BPlusTreeIndex.FieldMeta> keyMetas = new ArrayList<>();
            for (FieldMeta meta : keyMetasFrom(entry.columns, metas)) {
                keyMetas.add(new BPlusTreeIndex.FieldMeta(meta.name, meta.type, meta.param));
            }
            List<BPlusTreeIndex.IndexEntry> bptEntries = new ArrayList<>();
            for (IndexEntry ie : indexEntries) {
                bptEntries.add(new BPlusTreeIndex.IndexEntry(new ArrayList<>(ie.keys), ie.recordOffset));
            }
            BPlusTreeIndex tree = BPlusTreeIndex.build(bptEntries, 64, entry.unique, entry.ascending, keyMetas);
            tree.save(ixFile.toPath());
        } catch (IOException e) {
            throw new RuntimeException("写入 .ix 失败: " + e.getMessage(), e);
        }
    }

    private void writeTidEntry(File tidFile,
                              String indexName,
                              boolean unique,
                              boolean ascending,
                              List<String> columns,
                              String recordFile,
                              String indexFile) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(tidFile, "rw")) {
            long length = raf.length();
            long pos = 0;
            long emptyPos = -1;
            while (pos + TID_BLOCK_SIZE <= length) {
                raf.seek(pos);
                String name = BinaryIoUtils.readFixedString(raf, 128);
                if (!name.isEmpty() && name.equalsIgnoreCase(indexName)) {
                    throw new IllegalArgumentException("索引已存在: " + indexName);
                }
                if (name.isEmpty() && emptyPos == -1) {
                    emptyPos = pos;
                }
                pos += TID_BLOCK_SIZE;
            }
            long writePos = emptyPos != -1 ? emptyPos : length;
            raf.seek(writePos);
            BinaryIoUtils.writeFixedString(raf, indexName, 128);
            BinaryIoUtils.writeBool(raf, unique);
            BinaryIoUtils.writeBool(raf, ascending);
            raf.writeInt(columns.size());
            BinaryIoUtils.writeFixedString(raf, columns.size() > 0 ? columns.get(0) : "", 128);
            BinaryIoUtils.writeFixedString(raf, columns.size() > 1 ? columns.get(1) : "", 128);
            BinaryIoUtils.writeFixedString(raf, recordFile, 256);
            BinaryIoUtils.writeFixedString(raf, indexFile, 256);
            BinaryIoUtils.writeZeroPadding(raf, 2);
        }
    }

    private void clearTidEntry(File tidFile, long offset) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(tidFile, "rw")) {
            raf.seek(offset);
            BinaryIoUtils.writeFixedString(raf, "", 128);
            BinaryIoUtils.writeBool(raf, false);
            BinaryIoUtils.writeBool(raf, true);
            raf.writeInt(0);
            BinaryIoUtils.writeFixedString(raf, "", 128);
            BinaryIoUtils.writeFixedString(raf, "", 128);
            BinaryIoUtils.writeFixedString(raf, "", 256);
            BinaryIoUtils.writeFixedString(raf, "", 256);
            BinaryIoUtils.writeZeroPadding(raf, 2);
        }
    }

    private List<FieldMeta> parseTdf(File tdfFile) throws IOException {
        List<FieldMeta> metas = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(tdfFile, "r")) {
            long fileLength = raf.length();
            long pos = 0;
            int currentOffset = 0;
            while (pos + FIELD_BLOCK_SIZE <= fileLength) {
                raf.seek(pos);
                raf.readInt();
                String name = BinaryIoUtils.readFixedString(raf, 128);
                int type = raf.readInt();
                int param = raf.readInt();
                // skip mtime 16 + integrities 4

                FieldMeta meta = new FieldMeta();
                meta.name = name;
                meta.type = type;
                meta.param = param;
                meta.offset = currentOffset;
                int length;
                switch (type) {
                    case 1: length = 4; break;
                    case 2: length = 1; break;
                    case 3: length = 8; break;
                    case 5: length = 16; break;
                    case 4: default: length = param + 1; break;
                }
                int padding = length % 4;
                if (padding != 0) {
                    length += (4 - padding);
                }
                meta.length = length;
                currentOffset += length;
                metas.add(meta);
                pos += FIELD_BLOCK_SIZE;
            }
        }
        return metas;
    }

    private List<FieldMeta> keyMetasFrom(List<String> columns, List<FieldMeta> metas) {
        List<FieldMeta> result = new ArrayList<>();
        for (String col : columns) {
            FieldMeta meta = metas.stream().filter(m -> m.name.equalsIgnoreCase(col)).findFirst().orElse(null);
            if (meta != null) {
                result.add(meta);
            }
        }
        return result;
    }

    private Comparable<?> readComparable(RandomAccessFile raf, FieldMeta meta) throws IOException {
        switch (meta.type) {
            case 1:
                return raf.readInt();
            case 2:
                return raf.readByte() != 0;
            case 3:
                return raf.readDouble();
            case 5:
                return BinaryIoUtils.readDateTime(raf);
            case 4:
            default:
                return BinaryIoUtils.readFixedString(raf, meta.param + 1);
        }
    }

    private String toUniqueKey(List<Comparable<?>> keys) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) sb.append('|');
            sb.append(Objects.toString(keys.get(i), ""));
        }
        return sb.toString();
    }

    private int compareKeys(List<Comparable<?>> left, List<Comparable<?>> right) {
        int size = Math.min(left.size(), right.size());
        for (int i = 0; i < size; i++) {
            int cmp = compareNullable(left.get(i), right.get(i));
            if (cmp != 0) {
                return cmp;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private int compareNullable(Comparable left, Comparable right) {
        if (left == null && right == null) return 0;
        if (left == null) return -1;
        if (right == null) return 1;
        if (left.getClass().isInstance(right)) {
            return left.compareTo(right);
        }
        return String.valueOf(left).compareTo(String.valueOf(right));
    }
}
