package com.ocean.ontologyframework.pizza;

import com.ocean.openlletresolver.BackendService;
import com.ocean.openlletresolver.GenericAxiomBuilder;
import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.ontopobdahandler.OntopMappingResolver;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class PizzaOntologyValidator implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PizzaOntologyValidator.class);

    // ==================== 命名空间常量 ====================
    private static final String NS_PIZZA_CLS  = "http://example.org/pizza/classes/";
    private static final String NS_PIZZA_IND  = "http://example.org/pizza/individuals/";
    private static final String NS_COMP_IND   = "http://example.org/pizza/components/individuals/";
    private static final String NS_COMP_PROP  = "http://example.org/pizza/components/classes/";

    private final BackendService backendService;
    private final OWLOntology tbox;
    private final OWLReasoner reasoner;

    private final GenericAxiomBuilder pizzaAxiomBuilder;
    private final GenericAxiomBuilder componentAxiomBuilder;

    // ⭐ 缓存：从 OBDA 映射文件中解析出的三张表的属性 IRI
    private final Set<String> myPizzaPropertyIris;
    private final Set<String> pizzaComponentPropertyIris;
    private final Set<String> crustComponentPropertyIris;
    // ⭐ 新增：DB列全限定名 → 本体属性IRI 的反向查找表
    // Key 格式: "tableName.columnName" (小写)，Value: 本体属性 IRI
    private final Map<String, String> columnToIriLookup;

    public PizzaOntologyValidator(BackendService backendService) {
        try {
            this.backendService = backendService;
            this.tbox = backendService.getOntologyService().gettBoxOntology();
            this.reasoner = backendService.getReasonerService().getReasoner();

            this.pizzaAxiomBuilder = new GenericAxiomBuilder(backendService, NS_PIZZA_CLS, NS_PIZZA_IND);
            this.componentAxiomBuilder = new GenericAxiomBuilder(backendService, NS_COMP_PROP, NS_COMP_IND);

            // ⭐ 核心：复用 OBDAHandler 和 OntopMappingResolver 获取所有表的字段映射
            Map<String, OntopMappingResolver.ColumnMapping> mappingCache = OBDAHandler.Holder.MAPPING_CACHE;

            this.columnToIriLookup = mappingCache.entrySet().stream()
                    .collect(Collectors.toMap(
                            e -> (e.getValue().tableName() + "." + e.getValue().columnName()).toLowerCase(),
                            Map.Entry::getKey,
                            (existing, replacement) -> existing  // 冲突时保留第一个
                    ));

            log.info("🔄 反向映射表构建完成 | 总条目数: {}", columnToIriLookup.size());

            // 1. 提取 myPizza 表的属性 IRI (对应 mypizza_complete 映射)
            this.myPizzaPropertyIris = mappingCache.entrySet().stream()
                    .filter(e -> "mypizza".equalsIgnoreCase(e.getValue().tableName()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            // 2. 提取 pizza_components 表的属性 IRI
            this.pizzaComponentPropertyIris = mappingCache.entrySet().stream()
                    .filter(e -> "pizza_components".equalsIgnoreCase(e.getValue().tableName()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            // 3. 提取 crust_components 表的属性 IRI
            this.crustComponentPropertyIris = mappingCache.entrySet().stream()
                    .filter(e -> "crust_components".equalsIgnoreCase(e.getValue().tableName()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            log.info("🏗️ 本体验证器初始化成功 | myPizza 映射属性数: {} | pizza_components 映射属性数: {} | crust_components 映射属性数: {}",
                    myPizzaPropertyIris.size(), pizzaComponentPropertyIris.size(), crustComponentPropertyIris.size());
        } catch (Exception e) {
            throw new IllegalStateException("PizzaOntologyValidator 初始化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 验证单条披萨记录，并动态拉取组件的附加属性
     */
    public ValidationResult validate(Map<String, Object> pizzaInstance) {
        List<String> violations = new ArrayList<>();
        Set<OWLAxiom> tempAxioms = new HashSet<>();

        // ===================== 1. 基础字段校验 (基于传入的 Map) =====================
        String name = getStr(pizzaInstance, "name");
        String type = getStr(pizzaInstance, "type");
        String crustName = getStr(pizzaInstance, "crust_name");

        if (name.isBlank())      violations.add("name 不能为空");
        if (type.isBlank())      violations.add("type 不能为空");
        if (crustName.isBlank()) violations.add("crust_name 不能为空 (FunctionalProperty)");

        Object priceObj = pizzaInstance.get("price");
        if (priceObj != null) {
            try {
                double price = Double.parseDouble(priceObj.toString());
                if (price < 0) violations.add("price 不能为负数: " + price);
            } catch (NumberFormatException e) {
                violations.add("price 格式非法: " + priceObj);
            }
        }

        Object prodDate = pizzaInstance.get("production_date");
        if (prodDate != null && !prodDate.toString().matches("\\d{4}-\\d{2}-\\d{2}")) {
            violations.add("production_date 格式应为 YYYY-MM-DD: " + prodDate);
        }

        if (!violations.isEmpty()) {
            return ValidationResult.invalid(violations, pizzaInstance);
        }

        // ===================== 2. 构建披萨主体公理 =====================
        // ⭐ 这里我们依然使用传入的 Map 构建，因为 myPizza 的数据已经在 Map 里了
        // 如果你想从 DB 重新查一遍 myPizza 的其他隐藏字段，可以调用 queryComponentProperties(name, myPizzaPropertyIris)
        Map<String, String> pizzaProps = new LinkedHashMap<>();
        pizzaProps.put("type", type);
        if (priceObj != null)   pizzaProps.put("price", priceObj.toString());
        if (prodDate != null)   pizzaProps.put("productionDate", prodDate.toString());

        if (!crustName.isBlank()) pizzaProps.put("hasCrust", crustName);

        String cheeseName = getStr(pizzaInstance, "cheese_name");
        String sauceName = getStr(pizzaInstance, "sauce_name");
        String toppingName = getStr(pizzaInstance, "topping_name");

        if (!cheeseName.isBlank())  pizzaProps.put("hasCheese", cheeseName);
        if (!sauceName.isBlank())   pizzaProps.put("hasSauce", sauceName);
        if (!toppingName.isBlank()) pizzaProps.put("hasTopping", toppingName);

        tempAxioms.addAll(pizzaAxiomBuilder.buildAxioms(name, pizzaProps));

        // ===================== 3. ⭐ 动态查询组件附加属性 (复用 OBDAHandler) =====================
        List<String> componentNames = Arrays.asList(crustName, cheeseName, sauceName, toppingName);

        for (String compName : componentNames) {
            if (compName.isBlank()) continue;

            String compIRI = NS_COMP_IND + compName;

            // 3.1 从 pizza_components 查询基础属性
            Map<String, String> compBaseProps = queryComponentProperties(compIRI, pizzaComponentPropertyIris, "pizza_components");

            if (compBaseProps.isEmpty()) {
                log.warn("🚫 [{}] 库里没有这个组件 (pizza_components 查询为空)，跳过附加属性查询", compName);
                violations.add("组件 " + compName + " 在 pizza_components 表中不存在");
                continue;
            }

            tempAxioms.addAll(componentAxiomBuilder.buildAxioms(compName, compBaseProps));

            // 3.2 ⭐ 针对 crust_name，额外从 crust_components 查询附加属性
            if (compName.equals(crustName)) {
                Map<String, String> crustExtraProps = queryComponentProperties(compIRI, crustComponentPropertyIris, "crust_components");

                if (crustExtraProps.isEmpty()) {
                    log.warn("ℹ️ [{}] 这个组件的附加属性没定义 (crust_components 查询为空)", compName);
                } else {
                    crustExtraProps.forEach((k, v) -> compBaseProps.merge(k, v, (oldVal, newVal) -> newVal));
                    tempAxioms.addAll(componentAxiomBuilder.buildAxioms(compName, crustExtraProps));
                }
            }
        }

        // ===================== 4. 一致性检测 + 安全回滚 =====================
        try {
            backendService.safeVerifyAndDBExecution(tempAxioms, null);
        } catch (Exception e) {
            violations.add("本体一致性校验失败: " + e.getMessage());
        } finally {
            try {
                tbox.removeAxioms(tempAxioms);
                reasoner.flush();
            } catch (Exception e) {
                log.error("🧹 临时公理回滚失败，验证器状态可能已污染", e);
            }
        }

        return violations.isEmpty()
                ? ValidationResult.valid(pizzaInstance)
                : ValidationResult.invalid(violations, pizzaInstance);
    }

    // ==================== ⭐ 复用 OBDAHandler 的辅助方法 ====================

    /**
     * 通过 OBDAHandler 查询组件在 DB 中的属性，并根据 OBDA 映射的表字段进行过滤
     */
    /**
     * @param componentIRI      组件实例 IRI
     * @param targetPropertyIris 目标本体属性 IRI 集合
     * @param tableName         ⭐ 新增：该组件对应的数据库表名（用于拼接反向查找键）
     */
    private Map<String, String> queryComponentProperties(
            String componentIRI,
            Set<String> targetPropertyIris,
            String tableName
    ) {
        Map<String, String> properties = new LinkedHashMap<>();

        try {
            List<Map<String, String>> allProps = OBDAHandler.getInstance()
                    .getInstanceProperties(NS_COMP_PROP, componentIRI);

            for (Map<String, String> row : allProps) {
                String dbColumn = row.get("property");
                String value = row.get("value");

                if (dbColumn == null || value == null) continue;

                // ⭐ 核心修复：用传入的表名 + DB列名 拼接出完整的查找键
                String lookupKey = (tableName + "." + dbColumn).toLowerCase();
                String propIRI = columnToIriLookup.get(lookupKey);

                if (propIRI != null && targetPropertyIris.contains(propIRI)) {
                    String localName = propIRI.substring(propIRI.lastIndexOf('/') + 1);
                    if (localName.contains("#")) {
                        localName = localName.substring(localName.lastIndexOf('#') + 1);
                    }
                    properties.put(localName, value);
                }
            }
        } catch (Exception e) {
            log.error("❌ [{}] 查询 OBDA 属性失败: {}", componentIRI, e.getMessage());
        }

        return properties;
    }

    private String getStr(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val == null ? "" : val.toString().trim();
    }

    @Override
    public void close() {
        if (reasoner != null) {
            reasoner.dispose();
            log.debug("🧹 OpenLlet 推理机资源已释放");
        }
    }
}