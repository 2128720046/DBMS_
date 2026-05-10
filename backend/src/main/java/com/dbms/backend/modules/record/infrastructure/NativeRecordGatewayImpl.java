package com.dbms.backend.modules.record.infrastructure;

import com.dbms.backend.modules.record.domain.RecordGateway;
import com.dbms.backend.core.storage.config.StorageEngineConfig;
import com.dbms.backend.core.storage.io.BinaryIoUtils;
import com.dbms.backend.modules.integrity.domain.IntegrityGateway;
import com.dbms.backend.modules.index.domain.IndexGateway;
import com.dbms.backend.modules.index.infrastructure.BPlusTreeIndex;
import com.dbms.backend.modules.index.infrastructure.IndexTidIo;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;

/**
 * Binary storage implementation of record CRUD operations.
 */
@Primary
@Repository
public class NativeRecordGatewayImpl implements RecordGateway {

    private final IntegrityGateway integrityGateway;
    private final IndexGateway indexGateway;

    public NativeRecordGatewayImpl(IntegrityGateway integrityGateway, IndexGateway indexGateway) {
        this.integrityGateway = integrityGateway;
        this.indexGateway = indexGateway;
    }

    // 严苛地按照《验收要求》约定的160字节 FieldBlock
    private static final int FIELD_BLOCK_SIZE = 160;

    // TableBlock: 128(name)+4(record_num)+4(field_num)+256*4+16+4 = 1180 bytes
    private static final int TABLE_BLOCK_SIZE = 1180;

    /**
     * 内部类：缓存解析出的字段元数据。
     * 从 .tdf 载入，指导我们在读写 .trd 时该如何切分字节。
     */
    private static class FieldMeta {
        String name;
        int type;    // 1=INT, 2=BOOL, 3=DOUBLE, 4=VARCHAR, 5=DATETIME
        int param;   // size constraint
        int length;  // byte count consumed in .trd record
        int offset;  // byte offset inside a record (after row-deleted flag)
    }

    private List<FieldMeta> parseTdf(String schemaName, String tableName) {
        String tdfPath = StorageEngineConfig.getDATA_DIR() + File.separator + schemaName + File.separator + tableName + ".tdf";
        File tdfFile = new File(tdfPath);
        if (!tdfFile.exists()) {
            throw new IllegalArgumentException("表不存在: " + tableName);
        }

        List<FieldMeta> metas = new ArrayList<>();
        int currentOffset = 0; // 0 是删号保留位，但字段本身的 offset 是相对这之后

        try (RandomAccessFile raf = new RandomAccessFile(tdfFile, "r")) {
            long fileLength = raf.length();
            long pos = 0;
            while (pos < fileLength) {
                raf.seek(pos);
                raf.readInt(); // order
                String name = BinaryIoUtils.readFixedString(raf, 128);
                int type = raf.readInt();
                int param = raf.readInt();
                /* skip mtime 16b, integrity 4b */

                FieldMeta meta = new FieldMeta();
                meta.name = name;
                meta.type = type;
                meta.param = param;
                meta.offset = currentOffset;

                // 按照需求 3.12.1 规定的存储大小
                switch (type) {
                    case 1: meta.length = 4; break; // INTEGER
                    case 2: meta.length = 1; break; // BOOL
                    case 3: meta.length = 8; break; // DOUBLE (文档说是 2 bytes, 可能是笔误，为防止精度丢失我们采用标准 Java 8 bytes 处理，若必须 2 bytes 则需转换)
                    case 4: meta.length = param + 1; break; // VARCHAR (n+1) byte
                    case 5: meta.length = 16; break; // DATETIME
                    default: meta.length = param + 1; break;
                }
                
                // 3.12.7 提到：为提高效率，所有块和字段大小在存储时调整成 4 的倍数 (Padding)
                int padding = meta.length % 4;
                if (padding != 0) {
                    meta.length += (4 - padding);
                }

                currentOffset += meta.length;
                metas.add(meta);
                pos += FIELD_BLOCK_SIZE;
            }
        } catch (IOException e) {
            throw new RuntimeException("解析表定义失败 (.tdf)", e);
        }
        return metas;
    }

    @Override
    public int insert(String schemaName, String tableName, Map<String, Object> values) {
        Map<String, Object> safeValues = values == null ? Map.of() : values;
        List<Map<String, Object>> issues = integrityGateway.validateRow(schemaName, tableName, safeValues);
        if (issues != null && !issues.isEmpty()) {
            Map<String, Object> first = issues.get(0);
            throw new IllegalArgumentException("违反完整性约束: " + first);
        }

        // 先去磁盘读 .tdf 拿到各个列字节应占的大小，知道往这本字典里该怎么“断句”
        List<FieldMeta> metas = parseTdf(schemaName, tableName);
        String trdPath = StorageEngineConfig.getDATA_DIR() + File.separator + schemaName + File.separator + tableName + ".trd";

        try (RandomAccessFile raf = new RandomAccessFile(new File(trdPath), "rw")) {
            // 采用追加写，所以把光标移动到当前文件最末尾
            raf.seek(raf.length()); 
            
            // 按照文档 3.12.7，所有定长写入必须满足 4 的倍数
            // “行状态标志位”：用这个 int=1 告诉系统，这行记录还活着，没有被删除
            raf.writeInt(1); 

            // 按照列顺写入
            for (FieldMeta meta : metas) {
                Object val = safeValues.get(meta.name);
                long posBefore = raf.getFilePointer();

                if (meta.type == 1) { // INTEGER
                    int v = convertToInt(val);
                    raf.writeInt(v);
                } else if (meta.type == 2) { // BOOL
                    boolean v = convertToBoolean(val);
                    raf.writeByte(v ? 1 : 0);
                } else if (meta.type == 3) { // DOUBLE
                    double v = convertToDouble(val);
                    raf.writeDouble(v);
                } else if (meta.type == 5) { // DATETIME
                    long v = (val instanceof Number) ? ((Number) val).longValue() : System.currentTimeMillis();
                    BinaryIoUtils.writeDateTime(raf, v);
                } else if (meta.type == 4) { // VARCHAR
                    String v = convertToString(val);
                    BinaryIoUtils.writeFixedString(raf, v, meta.param + 1);
                } else {
                    // 未知类型处理
                    String v = convertToString(val);
                    BinaryIoUtils.writeFixedString(raf, v, meta.param + 1);
                }

                // 计算实际写入长度，按 TDF 约定补齐到 meta.length
                long written = raf.getFilePointer() - posBefore;
                int padding = (int) (meta.length - written);
                if (padding > 0) {
                    BinaryIoUtils.writeZeroPadding(raf, padding);
                }
            }
            updateTableRecordCount(schemaName, tableName, 1);
            rebuildIndexes(schemaName, tableName);
            return 1;
        } catch (Exception e) {
            throw new RuntimeException("写入数据文件 (.trd) 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> query(String schemaName, String tableName, Map<String, Object> filters, int limit, int offset) {
        // 由于是原生文件查询（没有索引），这里的思路是：全表扫描 (Sequential Scan).
        // 算出“这一整条记录到底该占据多少字节”，然后 while 循环把文件挨行扫描比对过滤条件
        List<FieldMeta> metas = parseTdf(schemaName, tableName);
        List<Map<String, Object>> results = new ArrayList<>();
        String trdPath = StorageEngineConfig.getDATA_DIR() + File.separator + schemaName + File.separator + tableName + ".trd";
        File trdFile = new File(trdPath);
        if (!trdFile.exists()) return results;

        // Prefer index-based lookup when equality filters match an index.
        List<Map<String, Object>> indexed = tryQueryByIndex(schemaName, tableName, filters, metas, limit, offset);
        if (indexed != null) {
            return indexed;
        }

        int recordLength = 4; // status flag header aligned to 4 bytes
        for (FieldMeta m : metas) {
            // meta.length 在 parseTdf 已经按 4 字节对齐
            recordLength += m.length;
        }
        try (RandomAccessFile raf = new RandomAccessFile(trdFile, "r")) {
            long totalBytes = raf.length();
            long pos = 0;
            int matchedFound = 0;

            while (pos < totalBytes) {
                raf.seek(pos);
                int status = raf.readInt();
                if (status == 1) { // 仅解析有效记录
                    Map<String, Object> row = readRow(raf, metas, pos + 4);
                    
                    // 匹配过滤条件 (中期：精确匹配)
                    boolean match = true;
                    if (filters != null && !filters.isEmpty()) {
                        for (Map.Entry<String, Object> e : filters.entrySet()) {
                            Object fv = e.getValue();
                            Object rv = row.get(e.getKey());
                            if (fv != null && !fv.toString().equals(String.valueOf(rv))) {
                                match = false;
                                break;
                            }
                        }
                    }
                    
                    if (match) {
                        if (matchedFound >= offset) {
                            results.add(row);
                            if (limit > 0 && results.size() >= limit) break;
                        }
                        matchedFound++;
                    }
                }
                pos += recordLength;
            }
        } catch (Exception e) {
            throw new RuntimeException("读取数据文件 (.trd) 失败", e);
        }
        return results;
    }

    @Override
    public int update(String schemaName, String tableName, Map<String, Object> filters, Map<String, Object> values) {
        // 中期方案：查询符合条件的 offset，然后直接定位逐个覆盖 (不支持自增)
        List<FieldMeta> metas = parseTdf(schemaName, tableName);
        String trdPath = StorageEngineConfig.getDATA_DIR() + File.separator + schemaName + File.separator + tableName + ".trd";
        int recordLength = 4;
        for (FieldMeta m : metas) recordLength += m.length;

        List<Long> targets = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(new File(trdPath), "rw")) {
            long pos = 0;
            while (pos < raf.length()) {
                raf.seek(pos);
                int status = raf.readInt();
                if (status == 1) {
                    Map<String, Object> row = readRow(raf, metas, pos + 4);
                    boolean match = true;
                    if (filters != null && !filters.isEmpty()) {
                        for (Map.Entry<String, Object> e : filters.entrySet()) {
                            Object fv = e.getValue();
                            Object rv = row.get(e.getKey());
                            if (fv != null && !fv.toString().equals(String.valueOf(rv))) {
                                match = false;
                                break;
                            }
                        }
                    }
                    if (match) {
                        Map<String, Object> merged = new HashMap<>(row);
                        if (values != null && !values.isEmpty()) {
                            merged.putAll(values);
                        }
                        merged.put("__dbms_ignoreOffset", pos);
                        List<Map<String, Object>> issues = integrityGateway.validateRow(schemaName, tableName, merged);
                        if (issues != null && !issues.isEmpty()) {
                            Map<String, Object> first = issues.get(0);
                            throw new IllegalArgumentException("违反完整性约束: " + first);
                        }
                        targets.add(pos);
                    }
                }
                pos += recordLength;
            }

            // 二阶段：全部校验通过后再写入
            if (targets.size() > 1 && values != null && !values.isEmpty()) {
                // 若批量更新命中多行，并且要更新 unique/pk 列，直接拒绝，避免多行被写成同一个值。
                List<Map<String, Object>> constraints = integrityGateway.listConstraints(schemaName, tableName);
                for (Map<String, Object> c : constraints) {
                    String type = String.valueOf(c.getOrDefault("type", "")).trim().toUpperCase().replace(' ', '_');
                    String col = String.valueOf(c.getOrDefault("column", "")).trim();
                    if (col.isBlank()) {
                        continue;
                    }
                    if (("PRIMARY_KEY".equals(type) || "UNIQUE".equals(type)) && values.containsKey(col)) {
                        throw new IllegalArgumentException("批量更新不允许同时修改 UNIQUE/PRIMARY KEY 字段: " + col);
                    }
                }
            }

            int updated = 0;
            for (Long targetPos : targets) {
                for (FieldMeta meta : metas) {
                    if (values != null && values.containsKey(meta.name)) {
                        raf.seek(targetPos + 4 + meta.offset);
                        Object val = values.get(meta.name);
                        if (meta.type == 1) raf.writeInt((val instanceof Number) ? ((Number) val).intValue() : convertToInt(val));
                        else if (meta.type == 2) raf.writeByte(convertToBoolean(val) ? 1 : 0);
                        else if (meta.type == 3) raf.writeDouble((val instanceof Number) ? ((Number) val).doubleValue() : convertToDouble(val));
                        else if (meta.type == 5) BinaryIoUtils.writeDateTime(raf, (val instanceof Number) ? ((Number) val).longValue() : System.currentTimeMillis());
                        else BinaryIoUtils.writeFixedString(raf, convertToString(val), meta.param + 1);
                    }
                }
                updated++;
            }
            if (updated > 0) {
                rebuildIndexes(schemaName, tableName);
            }
            return updated;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("更新失败 (.trd)", e);
        }
    }

    @Override
    public int delete(String schemaName, String tableName, Map<String, Object> filters) {
        List<Map<String, Object>> issues = integrityGateway.validateDelete(schemaName, tableName, filters);
        if (issues != null && !issues.isEmpty()) {
            Map<String, Object> first = issues.get(0);
            throw new IllegalArgumentException("违反参照完整性: " + first);
        }
        List<FieldMeta> metas = parseTdf(schemaName, tableName);
        String trdPath = StorageEngineConfig.getDATA_DIR() + File.separator + schemaName + File.separator + tableName + ".trd";
        int recordLength = 4;
        for (FieldMeta m : metas) recordLength += m.length;

        int deleted = 0;
        try (RandomAccessFile raf = new RandomAccessFile(new File(trdPath), "rw")) {
            long pos = 0;
            while (pos < raf.length()) {
                raf.seek(pos);
                int status = raf.readInt();
                if (status == 1) {
                    Map<String, Object> row = readRow(raf, metas, pos + 4);
                    boolean match = true;
                    if (filters != null && !filters.isEmpty()) {
                        for (Map.Entry<String, Object> e : filters.entrySet()) {
                            Object fv = e.getValue();
                            Object rv = row.get(e.getKey());
                            if (fv != null && !fv.toString().equals(String.valueOf(rv))) {
                                match = false; break;
                            }
                        }
                    }
                    if (match) {
                        // 覆盖 4字节的头为 0 (逻辑删除本行记录)
                        raf.seek(pos);
                        raf.writeInt(0);
                        deleted++;
                    }
                }
                pos += recordLength;
            }
        } catch (Exception e) {
             throw new RuntimeException("删除失败 (.trd)", e);
        }
        if (deleted > 0) {
            updateTableRecordCount(schemaName, tableName, -deleted);
            rebuildIndexes(schemaName, tableName);
        }
        return deleted;
    }

    private void rebuildIndexes(String schemaName, String tableName) {
        try {
            List<Map<String, Object>> indexes = indexGateway.listIndexes(schemaName, tableName);
            for (Map<String, Object> row : indexes) {
                Object name = row.get("name");
                if (name != null) {
                    indexGateway.rebuildIndex(schemaName, tableName, String.valueOf(name));
                }
            }
        } catch (Exception ignored) {
            // Index rebuild failure should not block record writes.
        }
    }

    private List<Map<String, Object>> tryQueryByIndex(String schemaName, String tableName,
                                                      Map<String, Object> filters,
                                                      List<FieldMeta> metas,
                                                      int limit,
                                                      int offset) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }
        File schemaDir = new File(StorageEngineConfig.getDATA_DIR() + File.separator + schemaName);
        File tidFile = new File(schemaDir, tableName + ".tid");
        if (!tidFile.exists()) {
            return null;
        }
        IndexTidIo.IndexDefinition chosen = null;
        List<IndexTidIo.IndexDefinition> entries;
        try {
            entries = IndexTidIo.read(tidFile);
        } catch (IOException e) {
            return null;
        }
        for (IndexTidIo.IndexDefinition entry : entries) {
            boolean allMatch = true;
            for (String col : entry.columns) {
                if (!filters.containsKey(col)) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) {
                if (chosen == null || entry.columns.size() > chosen.columns.size()) {
                    chosen = entry;
                }
            }
        }
        if (chosen == null || chosen.indexFile == null || chosen.indexFile.isBlank()) {
            return null;
        }

        List<FieldMeta> keyMetas = new ArrayList<>();
        List<Object> keyParts = new ArrayList<>();
        for (String col : chosen.columns) {
            FieldMeta meta = metas.stream().filter(m -> m.name.equalsIgnoreCase(col)).findFirst().orElse(null);
            if (meta == null) {
                return null;
            }
            keyMetas.add(meta);
            keyParts.add(normalizeFilterValue(filters.get(col), meta));
        }

        List<BPlusTreeIndex.FieldMeta> indexMetas = new ArrayList<>();
        for (FieldMeta meta : keyMetas) {
            indexMetas.add(new BPlusTreeIndex.FieldMeta(meta.name, meta.type, meta.param));
        }
        try {
            BPlusTreeIndex tree = BPlusTreeIndex.load(new File(chosen.indexFile).toPath(), indexMetas);
            List<Long> offsets = tree.searchEquals(BPlusTreeIndex.normalizeKeyParts(keyParts, indexMetas));
            if (offsets.isEmpty()) {
                return List.of();
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Long recordOffset : offsets) {
                Map<String, Object> row = readRowAtOffset(schemaName, tableName, metas, recordOffset);
                if (row != null && matchesFilters(row, filters)) {
                    rows.add(row);
                }
            }
            int start = Math.max(0, offset);
            int end = limit > 0 ? Math.min(rows.size(), start + limit) : rows.size();
            if (start >= rows.size()) {
                return List.of();
            }
            return new ArrayList<>(rows.subList(start, end));
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> readRowAtOffset(String schemaName, String tableName,
                                                List<FieldMeta> metas, long recordOffset) {
        String trdPath = StorageEngineConfig.getDATA_DIR() + File.separator + schemaName + File.separator + tableName + ".trd";
        File trdFile = new File(trdPath);
        if (!trdFile.exists()) {
            return null;
        }
        try (RandomAccessFile raf = new RandomAccessFile(trdFile, "r")) {
            raf.seek(recordOffset);
            int status = raf.readInt();
            if (status != 1) {
                return null;
            }
            return readRow(raf, metas, recordOffset + 4);
        } catch (IOException e) {
            return null;
        }
    }

    private boolean matchesFilters(Map<String, Object> row, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            Object expected = entry.getValue();
            Object actual = row.get(entry.getKey());
            if (expected != null && !expected.toString().equals(String.valueOf(actual))) {
                return false;
            }
        }
        return true;
    }

    private Object normalizeFilterValue(Object value, FieldMeta meta) {
        if (value == null) return null;
        switch (meta.type) {
            case 1:
                return convertToInt(value);
            case 2:
                return convertToBoolean(value);
            case 3:
                return convertToDouble(value);
            case 5:
                if (value instanceof Number) return ((Number) value).longValue();
                try {
                    return Long.parseLong(String.valueOf(value));
                } catch (NumberFormatException e) {
                    return 0L;
                }
            case 4:
            default:
                return convertToString(value);
        }
    }

    private void updateTableRecordCount(String schemaName, String tableName, int delta) {
        String tbPath = StorageEngineConfig.getDATA_DIR() + File.separator + schemaName + File.separator + schemaName + ".tb";
        File tbFile = new File(tbPath);
        if (!tbFile.exists()) {
            return;
        }
        try (RandomAccessFile raf = new RandomAccessFile(tbFile, "rw")) {
            long length = raf.length();
            long pos = 0;
            while (pos + TABLE_BLOCK_SIZE <= length) {
                raf.seek(pos);
                String name = BinaryIoUtils.readFixedString(raf, 128);
                if (!name.isEmpty() && name.equalsIgnoreCase(tableName)) {
                    int current = raf.readInt();
                    int next = Math.max(0, current + delta);
                    raf.seek(pos + 128);
                    raf.writeInt(next);

                    // 同步更新 mtime（最后 4 字节）
                    raf.seek(pos + (TABLE_BLOCK_SIZE - 4));
                    raf.writeInt((int) (System.currentTimeMillis() / 1000));
                    return;
                }
                pos += TABLE_BLOCK_SIZE;
            }
        } catch (IOException ignored) {
            // 元数据更新失败不阻断主流程
        }
    }

    private Map<String, Object> readRow(RandomAccessFile raf, List<FieldMeta> metas, long startPos) throws IOException {
        Map<String, Object> row = new HashMap<>();
        for (FieldMeta meta : metas) {
            raf.seek(startPos + meta.offset);
            if (meta.type == 1) {
                row.put(meta.name, raf.readInt());
            } else if (meta.type == 2) {
                row.put(meta.name, raf.readByte() != 0);
            } else if (meta.type == 3) {
                row.put(meta.name, raf.readDouble());
            } else if (meta.type == 5) {
                row.put(meta.name, BinaryIoUtils.readDateTime(raf));
            } else if (meta.type == 4) {
                row.put(meta.name, BinaryIoUtils.readFixedString(raf, meta.param + 1));
            } else {
                row.put(meta.name, BinaryIoUtils.readFixedString(raf, meta.param + 1));
            }
        }
        return row;
    }
    
    // 新增类型转换方法
    private int convertToInt(Object val) {
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) {
            try {
                return Integer.parseInt((String) val);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
    
    private boolean convertToBoolean(Object val) {
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof String) return Boolean.parseBoolean((String) val);
        if (val instanceof Number) return ((Number) val).intValue() != 0;
        return false;
    }
    
    private double convertToDouble(Object val) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) {
            try {
                return Double.parseDouble((String) val);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }
    
    private String convertToString(Object val) {
        return val != null ? String.valueOf(val) : "";
    }
}


