package com.ocean.openlletresolver;

import com.ocean.ontopobdahandler.OntopMappingResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * JOIN 键自动分发器。
 * 封装 Insert/Update/Delete 三种写入操作中完全相同的 JOIN 键解析、去重、反向查找与填充逻辑。
 */
public final class JoinKeyDistributor {

    private static final Logger log = LoggerFactory.getLogger(JoinKeyDistributor.class);

    private JoinKeyDistributor() {} // 纯工具类，禁止实例化

    /**
     * JOIN 键分发结果。
     */
    public record DistributionResult(int fillCount, int deduplicatedConfigCount) {}

    /**
     * 执行 JOIN 键分发。
     *
     * @param joinKeys          全局 JOIN 键配置列表
     * @param mappingCache      OBDA 映射缓存（用于 IRI → table.column 反向查找）
     * @param identifierValues  原始标识符 IRI→Value 映射（UPDATE/DELETE 使用；INSERT 传 null）
     * @param tableDataMap      待写入数据按表分组（INSERT 用作值源 + 涉及表判定；UPDATE/DELETE 仅作涉及表判定守卫）
     * @param tableIdentifierMap UPDATE/DELETE 的 WHERE 条件 Map（INSERT 传 null）
     * @param operationName     操作名称，用于日志和异常消息（如 "UPDATE"/"DELETE"/"INSERT"）
     * @return 分发结果统计
     * @throws IllegalStateException 当多表共享 JOIN 键但值缺失时
     */
    public static DistributionResult distribute(
            List<OntopMappingResolver.JoinKeyInfo> joinKeys,
            Map<String, OntopMappingResolver.ColumnMapping> mappingCache,
            Map<String, String> identifierValues,
            Map<String, Map<String, String>> tableDataMap,
            Map<String, Map<String, String>> tableIdentifierMap,
            String operationName) {

        int joinKeyFillCount = 0;
        Set<String> processedSignatures = new HashSet<>();

        for (OntopMappingResolver.JoinKeyInfo jk : joinKeys) {
            // ⭐ 去重
            String signature = String.join("|", jk.tableColumns());
            if (!processedSignatures.add(signature)) continue;

            // ⭐ Step A: 筛选本次操作实际涉及的表
            List<String[]> involvedTableCols = filterInvolvedTables(
                    jk.tableColumns(), tableDataMap, tableIdentifierMap);

            if (involvedTableCols.size() < 2) continue;

            // ⭐ Step B: 安全反向查找 JOIN 键值
            String joinValue = resolveJoinValue(
                    involvedTableCols, mappingCache, identifierValues, tableDataMap);

            // ⭐ Step C: 填充或报错
            if (joinValue != null) {
                joinKeyFillCount += fillJoinKey(
                        involvedTableCols, joinValue, tableDataMap, tableIdentifierMap);
            } else {
                throwMissingJoinKeyException(involvedTableCols, operationName);
            }
        }

        if (joinKeyFillCount > 0) {
            log.info("🔗 {} JOIN 键分发完成: {}个字段已填充 | 去重前={} | 去重后={}",
                    operationName, joinKeyFillCount, joinKeys.size(), processedSignatures.size());
        }

        return new DistributionResult(joinKeyFillCount, processedSignatures.size());
    }

    /**
     * 前置完整性校验：确保每个涉及的表都有完整的标识符/WHERE 条件。
     * INSERT 校验 tableDataMap 非空；UPDATE/DELETE 校验 tableIdentifierMap 有 WHERE 条件。
     */
    public static void validateCompleteness(
            Map<String, Map<String, String>> tableDataMap,
            Map<String, Map<String, String>> tableIdentifierMap,
            String operationName) {

        boolean isInsert = (tableIdentifierMap == null);

        if (isInsert) {
            for (var entry : tableDataMap.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    throw new IllegalStateException(String.format(
                            "❌ 表 [%s] 在JOIN键填充后仍无有效写入数据，%s已中止",
                            entry.getKey(), operationName));
                }
            }
        } else {
            for (String table : tableDataMap.keySet()) {
                Map<String, String> idData = tableIdentifierMap.get(table);
                if (idData == null || idData.isEmpty()) {
                    throw new IllegalStateException(String.format(
                            "❌ 跨表%s预检失败: 表 [%s] 缺少必要的标识符(JOIN键)。" +
                                    "请检查 OntopMappingResolver.JOIN_KEYS 配置或传入完整的 identifierValues",
                            operationName, table));
                }
            }
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 根据操作类型筛选实际涉及的表列组合。
     * INSERT: 以 tableDataMap 为基准
     * UPDATE/DELETE: 以 tableIdentifierMap 为基准（DELETE），或 tableDataMap 为基准（UPDATE）
     */
    // ⭐ 修改点：将 List<String> 改为 Collection<String>
    private static List<String[]> filterInvolvedTables(
            Collection<String> tableColumns,
            Map<String, Map<String, String>> tableDataMap,
            Map<String, Map<String, String>> tableIdentifierMap) {

        List<String[]> result = new ArrayList<>();
        for (String tableCol : tableColumns) {
            String[] parts = tableCol.split("\\.", 2);
            String tbl = parts[0];

            boolean involved = (tableIdentifierMap == null)
                    ? tableDataMap.containsKey(tbl)
                    : (tableDataMap != null && tableDataMap.containsKey(tbl))

                      || tableIdentifierMap.containsKey(tbl);

            if (involved) {
                result.add(parts);
            }
        }
        return result;
    }

    /**
     * 从 identifierValues（反向查 MAPPING_CACHE）或 tableDataMap 中提取 JOIN 键值。
     */
    private static String resolveJoinValue(
            List<String[]> involvedTableCols,
            Map<String, OntopMappingResolver.ColumnMapping> mappingCache,
            Map<String, String> identifierValues,
            Map<String, Map<String, String>> tableDataMap) {

        for (String[] parts : involvedTableCols) {
            String tbl = parts[0];
            String col = parts[1];

            // 优先从 tableDataMap 取（INSERT 场景，或 UPDATE 中 SET 列恰好是 JOIN 键）
            if (tableDataMap != null) {
                Map<String, String> data = tableDataMap.get(tbl);
                if (data != null && data.containsKey(col)) {
                    return data.get(col);
                }
            }

            // 回退：从 identifierValues 反向查 MAPPING_CACHE（UPDATE/DELETE 场景）
            if (identifierValues != null && mappingCache != null) {
                for (var cacheEntry : mappingCache.entrySet()) {
                    OntopMappingResolver.ColumnMapping cm = cacheEntry.getValue();
                    if (tbl.equals(cm.tableName()) && col.equals(cm.columnName())) {
                        String val = identifierValues.get(cacheEntry.getKey());
                        if (val != null) return val;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 将 JOIN 键值填充到目标 Map。
     * INSERT → 填充 tableDataMap
     * UPDATE/DELETE → 填充 tableIdentifierMap（必要时补建条目）
     */
    private static int fillJoinKey(
            List<String[]> involvedTableCols,
            String joinValue,
            Map<String, Map<String, String>> tableDataMap,
            Map<String, Map<String, String>> tableIdentifierMap) {

        int count = 0;
        for (String[] parts : involvedTableCols) {
            String tbl = parts[0];
            String col = parts[1];

            if (tableIdentifierMap == null) {
                // INSERT 模式：直接填充 tableDataMap
                Map<String, String> data = tableDataMap.get(tbl);
                if (data != null && data.putIfAbsent(col, joinValue) == null) {
                    count++;
                }
            } else {
                // UPDATE/DELETE 模式：填充 tableIdentifierMap
                Map<String, String> ids = tableIdentifierMap.get(tbl);
                // UPDATE 专属：表参与了更新但尚无标识符条目 → 补建
                if (ids == null && tableDataMap != null && tableDataMap.containsKey(tbl)) {
                    ids = new LinkedHashMap<>();
                    tableIdentifierMap.put(tbl, ids);
                }
                if (ids != null && ids.putIfAbsent(col, joinValue) == null) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void throwMissingJoinKeyException(
            List<String[]> involvedTableCols, String operationName) {
        List<String> names = involvedTableCols.stream()
                .map(p -> p[0] + "." + p[1])
                .collect(Collectors.toList());
        throw new IllegalStateException(String.format(
                "❌ %s JOIN键 [%s] 缺失，无法保证跨表引用完整性，操作已中止",
                operationName, String.join(", ", names)));
    }
}