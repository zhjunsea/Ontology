package com.ocean.ontopobdahandler;

import com.ocean.ontopobdahandler.OBDAHandler;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vkg")
public class VkgController {

    // 直接获取单例实例，无需 @Autowired 或 Lombok
    private final OBDAHandler obdaHandler = OBDAHandler.getInstance();

    @GetMapping("/properties/{prefix}/{instanceUri}")
    public List<Map<String, String>> getProperties(
            @PathVariable String prefix,
            @PathVariable String instanceUri) {
        return obdaHandler.getInstanceProperties(prefix, instanceUri);
    }
    @GetMapping("/inference")
    public List<Map<String, String>> inference(
            @RequestParam(defaultValue = "http://example.org/pizza/components/classes/") String prefix,
            @RequestParam(defaultValue = "http://example.org/pizza/components/classes/PizzaComponent") String className,
            @RequestParam(defaultValue = "20") int limit) {
        return obdaHandler.queryWithInference(prefix, className, limit);
    }

    @GetMapping("/aggregation")
    public List<Map<String, Object>> aggregation(
            @RequestParam(defaultValue = "http://example.org/pizza/components/classes/") String prefix,
            @RequestParam(defaultValue = "PizzaComponent") String className,
            @RequestParam(defaultValue = "supplier") String groupByProp,
            @RequestParam(defaultValue = "price") String aggProp,
            @RequestParam(defaultValue = "0") int limit) {
        return obdaHandler.queryAggregation(prefix, className, groupByProp, aggProp, limit);
    }

    @PostMapping("/component")
    public Map<String, Object> addComponent(@RequestBody Map<String, Object> body) {
        int rows = obdaHandler.addComponent(
                "pizza_components",
                List.of("name", "supplier", "price", "type"),
                List.of(
                        body.get("name"),
                        body.get("supplier"),
                        ((Number) body.get("price")).doubleValue(),
                        body.get("type")
                )
        );
        return Map.of("affectedRows", rows, "message", "组件添加成功");
    }

    @PutMapping("/component/{name}/price")
    public Map<String, Object> updatePrice(@PathVariable String name, @RequestParam double price) {
        int rows = obdaHandler.updateComponent(
                "pizza_components",
                List.of("price"), List.of(price),
                "name", name
        );
        return Map.of("affectedRows", rows);
    }

    @DeleteMapping("/component/{name}")
    public Map<String, Object> deleteComponent(@PathVariable String name) {
        int rows = obdaHandler.deleteComponent("pizza_components", "name", name);
        return Map.of("affectedRows", rows);
    }
}