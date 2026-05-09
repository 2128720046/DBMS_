package com.dbms.backend.modules.index.infrastructure;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Simple B+ tree index file reader/writer used by the index gateway and record scans.
 * The tree is stored as fixed node records in a binary .ix file.
 */
public final class BPlusTreeIndex {

    private static final int MAGIC = 0x42505431; // "BPT1"
    private static final int VERSION = 1;

    public static final class FieldMeta {
        public final String name;
        public final int type;
        public final int param;

        public FieldMeta(String name, int type, int param) {
            this.name = name;
            this.type = type;
            this.param = param;
        }
    }

    public static final class IndexEntry {
        public final List<Object> keys;
        public final long recordOffset;

        public IndexEntry(List<Object> keys, long recordOffset) {
            this.keys = keys;
            this.recordOffset = recordOffset;
        }
    }

    private static final class Node {
        long id;
        boolean leaf;
        List<List<Object>> keys = new ArrayList<>();
        List<Long> children = new ArrayList<>();
        List<Long> offsets = new ArrayList<>();
        long nextLeafId = -1;
    }

    private final int order;
    private final boolean unique;
    private final boolean ascending;
    private final List<FieldMeta> keyMetas;
    private long rootId;
    private final Map<Long, Node> nodes = new HashMap<>();

    private BPlusTreeIndex(int order, boolean unique, boolean ascending, List<FieldMeta> keyMetas) {
        this.order = order;
        this.unique = unique;
        this.ascending = ascending;
        this.keyMetas = keyMetas;
    }

    public static BPlusTreeIndex build(List<IndexEntry> entries,
                                       int order,
                                       boolean unique,
                                       boolean ascending,
                                       List<FieldMeta> keyMetas) {
        BPlusTreeIndex tree = new BPlusTreeIndex(order, unique, ascending, keyMetas);
        tree.bulkLoad(entries);
        return tree;
    }

    public static BPlusTreeIndex load(Path file, List<FieldMeta> keyMetas) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            int magic = in.readInt();
            if (magic != MAGIC) {
                throw new IOException("Invalid index file header");
            }
            int version = in.readInt();
            if (version != VERSION) {
                throw new IOException("Unsupported index version: " + version);
            }
            int order = in.readInt();
            boolean unique = in.readBoolean();
            boolean ascending = in.readBoolean();
            long rootId = in.readLong();
            int nodeCount = in.readInt();
            int metaCount = in.readInt();
            List<FieldMeta> metas = new ArrayList<>();
            for (int i = 0; i < metaCount; i++) {
                int type = in.readInt();
                int param = in.readInt();
                metas.add(new FieldMeta("", type, param));
            }
            List<FieldMeta> finalMetas = keyMetas != null && !keyMetas.isEmpty() ? keyMetas : metas;

            BPlusTreeIndex tree = new BPlusTreeIndex(order, unique, ascending, finalMetas);
            tree.rootId = rootId;
            for (int i = 0; i < nodeCount; i++) {
                Node node = new Node();
                node.id = in.readLong();
                node.leaf = in.readBoolean();
                int keyCount = in.readInt();
                node.nextLeafId = in.readLong();
                for (int k = 0; k < keyCount; k++) {
                    node.keys.add(readKey(in, tree.keyMetas));
                }
                if (node.leaf) {
                    for (int k = 0; k < keyCount; k++) {
                        node.offsets.add(in.readLong());
                    }
                } else {
                    int childCount = in.readInt();
                    for (int c = 0; c < childCount; c++) {
                        node.children.add(in.readLong());
                    }
                }
                tree.nodes.put(node.id, node);
            }
            return tree;
        }
    }

    public void save(Path file) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(order);
            out.writeBoolean(unique);
            out.writeBoolean(ascending);
            out.writeLong(rootId);
            out.writeInt(nodes.size());
            out.writeInt(keyMetas.size());
            for (FieldMeta meta : keyMetas) {
                out.writeInt(meta.type);
                out.writeInt(meta.param);
            }
            List<Long> ids = new ArrayList<>(nodes.keySet());
            Collections.sort(ids);
            for (Long id : ids) {
                Node node = nodes.get(id);
                out.writeLong(node.id);
                out.writeBoolean(node.leaf);
                out.writeInt(node.keys.size());
                out.writeLong(node.nextLeafId);
                for (List<Object> key : node.keys) {
                    writeKey(out, keyMetas, key);
                }
                if (node.leaf) {
                    for (Long offset : node.offsets) {
                        out.writeLong(offset);
                    }
                } else {
                    out.writeInt(node.children.size());
                    for (Long child : node.children) {
                        out.writeLong(child);
                    }
                }
            }
        }
    }

    public List<Long> searchEquals(List<Object> keyParts) {
        if (nodes.isEmpty()) {
            return List.of();
        }
        Node node = nodes.get(rootId);
        while (node != null && !node.leaf) {
            int idx = locateChild(node, keyParts);
            if (idx < 0 || idx >= node.children.size()) {
                return List.of();
            }
            node = nodes.get(node.children.get(idx));
        }
        if (node == null) {
            return List.of();
        }
        List<Long> results = new ArrayList<>();
        Node current = node;
        while (current != null) {
            for (int i = 0; i < current.keys.size(); i++) {
                int cmp = compareKeys(current.keys.get(i), keyParts);
                if (cmp == 0) {
                    results.add(current.offsets.get(i));
                } else if (cmp > 0 && ascending) {
                    return results;
                } else if (cmp < 0 && !ascending) {
                    return results;
                }
            }
            if (current.nextLeafId <= 0) {
                break;
            }
            current = nodes.get(current.nextLeafId);
        }
        return results;
    }

    private void bulkLoad(List<IndexEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            rootId = 0;
            return;
        }
        int maxKeys = Math.max(3, order - 1);
        if (unique) {
            for (int i = 1; i < entries.size(); i++) {
                if (compareKeys(entries.get(i - 1).keys, entries.get(i).keys) == 0) {
                    throw new IllegalStateException("Unique index violation while rebuilding");
                }
            }
        }
        long idCounter = 1;
        List<Node> leaves = new ArrayList<>();
        int index = 0;
        while (index < entries.size()) {
            Node leaf = new Node();
            leaf.id = idCounter++;
            leaf.leaf = true;
            int end = Math.min(entries.size(), index + maxKeys);
            for (int i = index; i < end; i++) {
                IndexEntry entry = entries.get(i);
                leaf.keys.add(entry.keys);
                leaf.offsets.add(entry.recordOffset);
            }
            leaves.add(leaf);
            index = end;
        }
        for (int i = 0; i < leaves.size() - 1; i++) {
            leaves.get(i).nextLeafId = leaves.get(i + 1).id;
        }
        for (Node leaf : leaves) {
            nodes.put(leaf.id, leaf);
        }
        List<Node> current = leaves;
        while (current.size() > 1) {
            List<Node> parents = new ArrayList<>();
            int pos = 0;
            while (pos < current.size()) {
                Node parent = new Node();
                parent.id = idCounter++;
                parent.leaf = false;
                int end = Math.min(current.size(), pos + order);
                for (int i = pos; i < end; i++) {
                    Node child = current.get(i);
                    parent.children.add(child.id);
                    if (i > pos) {
                        parent.keys.add(child.keys.get(0));
                    }
                }
                nodes.put(parent.id, parent);
                parents.add(parent);
                pos = end;
            }
            current = parents;
        }
        rootId = current.get(0).id;
    }

    private int locateChild(Node node, List<Object> keyParts) {
        int idx = 0;
        while (idx < node.keys.size()) {
            int cmp = compareKeys(keyParts, node.keys.get(idx));
            if (ascending) {
                if (cmp < 0) {
                    return idx;
                }
            } else {
                if (cmp > 0) {
                    return idx;
                }
            }
            idx++;
        }
        return idx;
    }

    private int compareKeys(List<Object> left, List<Object> right) {
        int size = Math.min(left.size(), right.size());
        for (int i = 0; i < size; i++) {
            int cmp = compareValue(left.get(i), right.get(i), keyMetas.get(i));
            if (cmp != 0) {
                return cmp;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    private int compareValue(Object left, Object right, FieldMeta meta) {
        if (left == null && right == null) return 0;
        if (left == null) return -1;
        if (right == null) return 1;
        switch (meta.type) {
            case 1:
            case 5:
                return Long.compare(((Number) left).longValue(), ((Number) right).longValue());
            case 2:
                return Boolean.compare((Boolean) left, (Boolean) right);
            case 3:
                return Double.compare(((Number) left).doubleValue(), ((Number) right).doubleValue());
            case 4:
            default:
                return String.valueOf(left).compareTo(String.valueOf(right));
        }
    }

    private static void writeKey(DataOutputStream out, List<FieldMeta> metas, List<Object> key) throws IOException {
        for (int i = 0; i < metas.size(); i++) {
            FieldMeta meta = metas.get(i);
            Object val = key.get(i);
            out.writeBoolean(val == null);
            if (val == null) {
                continue;
            }
            switch (meta.type) {
                case 1:
                case 5:
                    out.writeLong(((Number) val).longValue());
                    break;
                case 2:
                    out.writeBoolean((Boolean) val);
                    break;
                case 3:
                    out.writeDouble(((Number) val).doubleValue());
                    break;
                case 4:
                default:
                    byte[] bytes = String.valueOf(val).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    out.writeInt(bytes.length);
                    out.write(bytes);
                    break;
            }
        }
    }

    private static List<Object> readKey(DataInputStream in, List<FieldMeta> metas) throws IOException {
        List<Object> parts = new ArrayList<>();
        for (FieldMeta meta : metas) {
            boolean isNull = in.readBoolean();
            if (isNull) {
                parts.add(null);
                continue;
            }
            switch (meta.type) {
                case 1:
                case 5:
                    parts.add(in.readLong());
                    break;
                case 2:
                    parts.add(in.readBoolean());
                    break;
                case 3:
                    parts.add(in.readDouble());
                    break;
                case 4:
                default:
                    int len = in.readInt();
                    byte[] bytes = new byte[len];
                    in.readFully(bytes);
                    parts.add(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                    break;
            }
        }
        return parts;
    }

    public static List<Object> normalizeKeyParts(List<Object> raw, List<FieldMeta> metas) {
        List<Object> normalized = new ArrayList<>();
        for (int i = 0; i < metas.size(); i++) {
            normalized.add(normalizeValue(raw.get(i), metas.get(i)));
        }
        return normalized;
    }

    private static Object normalizeValue(Object value, FieldMeta meta) {
        if (value == null) return null;
        switch (meta.type) {
            case 1:
                if (value instanceof Number) return ((Number) value).longValue();
                return Long.parseLong(String.valueOf(value));
            case 2:
                if (value instanceof Boolean) return value;
                if (value instanceof Number) return ((Number) value).intValue() != 0;
                return Boolean.parseBoolean(String.valueOf(value));
            case 3:
                if (value instanceof Number) return ((Number) value).doubleValue();
                return Double.parseDouble(String.valueOf(value));
            case 5:
                if (value instanceof Number) return ((Number) value).longValue();
                return Long.parseLong(String.valueOf(value));
            case 4:
            default:
                return String.valueOf(value);
        }
    }

    public boolean isUnique() {
        return unique;
    }

    public boolean isAscending() {
        return ascending;
    }

    public int getOrder() {
        return order;
    }

    public List<FieldMeta> getKeyMetas() {
        return keyMetas;
    }

    public Map<Long, Node> getNodes() {
        return nodes;
    }

    public long getRootId() {
        return rootId;
    }

    public void setRootId(long rootId) {
        this.rootId = rootId;
    }

    public static String keySignature(List<Object> keys) {
        return Objects.toString(keys, "");
    }
}
