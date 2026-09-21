import com.ocean.ontologyframework.tcm.JingfangTestSupport;

/** 直接调用测试框架的初始化（部署 BPMN），把真实异常打出来。 */
public class DeployProbe {
    public static void main(String[] args) {
        try {
            JingfangTestSupport.ensureInitialized();
            System.out.println("DEPLOY_OK");
        } catch (Throwable t) {
            System.out.println("DEPLOY_FAIL: " + t);
            t.printStackTrace(System.out);
        }
    }
}
