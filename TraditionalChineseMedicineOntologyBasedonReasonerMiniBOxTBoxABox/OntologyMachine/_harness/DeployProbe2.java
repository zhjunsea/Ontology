import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.DeploymentEvent;

import java.time.Duration;

public class DeployProbe2 {
    public static void main(String[] args) {
        ZeebeClient c = ZeebeClient.newClientBuilder()
                .gatewayAddress("localhost:26500")
                .usePlaintext()
                .defaultRequestTimeout(Duration.ofSeconds(60))
                .build();
        try {
            DeploymentEvent d = c.newDeployResourceCommand()
                    .addResourceFile("D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology/Jingfang_Diagnosis.bpmn")
                    .send().join();
            System.out.println("DEPLOY_OK processes=" + d.getProcesses().size() + " key=" + d.getKey());
            d.getProcesses().forEach(p -> System.out.println("  pid=" + p.getBpmnProcessId() + " ver=" + p.getVersion()));
        } catch (Throwable t) {
            System.out.println("DEPLOY_FAIL: " + t.getClass().getName() + " :: " + t.getMessage());
            t.printStackTrace(System.out);
        }
        System.exit(0);
    }
}
