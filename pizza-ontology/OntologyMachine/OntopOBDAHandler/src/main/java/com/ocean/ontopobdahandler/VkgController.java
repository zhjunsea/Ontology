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

    @GetMapping("/properties/{instanceUri}")
    public List<Map<String, String>> getProperties(@PathVariable String instanceUri) {
        return obdaHandler.getInstanceProperties(instanceUri);
    }

    @GetMapping("/inference")
    public List<Map<String, String>> inference(
            @RequestParam(defaultValue = ":PizzaComponent") String className,
            @RequestParam(defaultValue = "20") int limit) {
        return obdaHandler.queryWithInference(className, limit);
    }

    @GetMapping("/aggregation")
    public List<Map<String, Object>> aggregation() {
        return obdaHandler.queryAggregation();
    }

    @PostMapping("/component")
    public Map<String, Object> addComponent(@RequestBody Map<String, Object> body) {
        int rows = obdaHandler.addComponent(
                (String) body.get("name"),
                (String) body.get("supplier"),
                ((Number) body.get("price")).doubleValue(),
                (String) body.get("type"));
        return Map.of("affectedRows", rows, "message", "组件添加成功");
    }

    @PutMapping("/component/{name}/price")
    public Map<String, Object> updatePrice(@PathVariable String name, @RequestParam double price) {
        int rows = obdaHandler.updatePrice(name, price);
        return Map.of("affectedRows", rows);
    }

    @DeleteMapping("/component/{name}")
    public Map<String, Object> deleteComponent(@PathVariable String name) {
        int rows = obdaHandler.deleteComponent(name);
        return Map.of("affectedRows", rows);
    }
}