import com.ocean.envprepare.EnvConfig;
import java.nio.file.Path;
var c = EnvConfig.load(Path.of("OntologyFramework/src/main/resources/application.yaml"));
System.out.println("TMSD moduleRoot=" + c.moduleRoot());
System.out.println("TMSD configFile=" + c.configFile());
System.out.println("TMSD ontopConfigured=" + c.ontopConfigured() + " rabbitmqDefined=" + c.rabbitmqDefined() + " mysqlDefined=" + c.mysqlDefined() + " bpmnPathDefined=" + c.bpmnPathDefined());
var p = EnvConfig.load(Path.of("OntologyFrameworkExample/src/main/resources/application.yaml"));
System.out.println("PIZZA moduleRoot=" + p.moduleRoot());
System.out.println("PIZZA configFile=" + p.configFile());
System.out.println("PIZZA ontopConfigured=" + p.ontopConfigured() + " rabbitmqDefined=" + p.rabbitmqDefined() + " mysqlDefined=" + p.mysqlDefined() + " bpmnPathDefined=" + p.bpmnPathDefined());
System.out.println("PIZZA dbName=" + p.dbName() + " ontologyFile=" + p.ontologyFile() + " queue=" + p.queue() + " exchange=" + p.exchange());
/exit
