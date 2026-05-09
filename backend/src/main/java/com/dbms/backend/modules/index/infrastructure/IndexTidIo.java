package com.dbms.backend.modules.index.infrastructure;

import com.dbms.backend.core.storage.io.BinaryIoUtils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility for reading .tid index definition files.
 */
public final class IndexTidIo {

    private static final int TID_BLOCK_SIZE = 904;

    private IndexTidIo() {
    }

    public static final class IndexDefinition {
        public long offset;
        public String name;
        public boolean unique;
        public boolean ascending;
        public List<String> columns = new ArrayList<>();
        public String recordFile;
        public String indexFile;
    }

    public static List<IndexDefinition> read(File tidFile) throws IOException {
        List<IndexDefinition> entries = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(tidFile, "r")) {
            long length = raf.length();
            long pos = 0;
            while (pos + TID_BLOCK_SIZE <= length) {
                raf.seek(pos);
                String name = BinaryIoUtils.readFixedString(raf, 128);
                boolean unique = BinaryIoUtils.readBool(raf);
                boolean asc = BinaryIoUtils.readBool(raf);
                int fieldNum = raf.readInt();
                String f1 = BinaryIoUtils.readFixedString(raf, 128);
                String f2 = BinaryIoUtils.readFixedString(raf, 128);
                String recordFile = BinaryIoUtils.readFixedString(raf, 256);
                String indexFile = BinaryIoUtils.readFixedString(raf, 256);
                if (!name.isEmpty()) {
                    IndexDefinition entry = new IndexDefinition();
                    entry.offset = pos;
                    entry.name = name;
                    entry.unique = unique;
                    entry.ascending = asc;
                    if (fieldNum >= 1 && !f1.isBlank()) entry.columns.add(f1);
                    if (fieldNum >= 2 && !f2.isBlank()) entry.columns.add(f2);
                    entry.recordFile = recordFile;
                    entry.indexFile = indexFile;
                    entries.add(entry);
                }
                pos += TID_BLOCK_SIZE;
            }
        }
        return entries;
    }

    public static IndexDefinition find(File tidFile, String indexName) throws IOException {
        for (IndexDefinition entry : read(tidFile)) {
            if (entry.name.equalsIgnoreCase(indexName)) {
                return entry;
            }
        }
        return null;
    }
}
