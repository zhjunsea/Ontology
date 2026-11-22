2026-11-22 18:22:30.957 [pool-2-thread-1] ERROR c.o.o.tcm.TCMOntologyJobWorker - 方证失败
io.camunda.client.api.command.ProblemException: Failed with code 500: 'Internal Server Error'. Details: 'class ProblemDetail {
type: about:blank
title: INTERNAL
status: 500
detail: Unexpected error occurred during the request processing: No remote message handler registered for this message, subject command-api-1
instance: /v2/jobs/2251799813854193/completion
}'
at io.camunda.client.impl.http.ApiCallback.handleErrorResponse(ApiCallback.java:149)
at io.camunda.client.impl.http.ApiCallback.completed(ApiCallback.java:74)
at io.camunda.client.impl.http.ApiCallback.completed(ApiCallback.java:34)
at org.apache.hc.core5.concurrent.BasicFuture.completed(BasicFuture.java:148)
at org.apache.hc.core5.concurrent.ComplexFuture.completed(ComplexFuture.java:72)
at org.apache.hc.client5.http.impl.async.InternalAbstractHttpAsyncClient$2$1.completed(InternalAbstractHttpAsyncClient.java:321)
at org.apache.hc.core5.http.nio.support.AbstractAsyncResponseConsumer$1.completed(AbstractAsyncResponseConsumer.java:101)
at org.apache.hc.core5.http.nio.entity.AbstractBinAsyncEntityConsumer.completed(AbstractBinAsyncEntityConsumer.java:87)
at org.apache.hc.core5.http.nio.entity.AbstractBinDataConsumer.streamEnd(AbstractBinDataConsumer.java:83)
at org.apache.hc.core5.http.nio.support.AbstractAsyncResponseConsumer.streamEnd(AbstractAsyncResponseConsumer.java:142)
at org.apache.hc.client5.http.impl.async.HttpAsyncMainClientExec$1.streamEnd(HttpAsyncMainClientExec.java:283)
at org.apache.hc.core5.http.impl.nio.ClientHttp1StreamHandler.dataEnd(ClientHttp1StreamHandler.java:285)
at org.apache.hc.core5.http.impl.nio.ClientHttp1StreamDuplexer.dataEnd(ClientHttp1StreamDuplexer.java:376)
at org.apache.hc.core5.http.impl.nio.AbstractHttp1StreamDuplexer.onInput(AbstractHttp1StreamDuplexer.java:343)
at org.apache.hc.core5.http.impl.nio.AbstractHttp1IOEventHandler.inputReady(AbstractHttp1IOEventHandler.java:64)
at org.apache.hc.core5.http.impl.nio.ClientHttp1IOEventHandler.inputReady(ClientHttp1IOEventHandler.java:41)
at org.apache.hc.core5.reactor.InternalDataChannel.onIOEvent(InternalDataChannel.java:139)
at org.apache.hc.core5.reactor.InternalChannel.handleIOEvent(InternalChannel.java:51)
at org.apache.hc.core5.reactor.SingleCoreIOReactor.processEvents(SingleCoreIOReactor.java:193)
at org.apache.hc.core5.reactor.SingleCoreIOReactor.doExecute(SingleCoreIOReactor.java:140)
at org.apache.hc.core5.reactor.AbstractSingleCoreIOReactor.execute(AbstractSingleCoreIOReactor.java:92)
at org.apache.hc.core5.reactor.IOReactorWorker.run(IOReactorWorker.java:44)
at java.base/java.lang.Thread.run(Thread.java:1527)
2026-11-22 18:22:31.016 [pool-2-thread-1] WARN  io.camunda.client.job.worker - Worker TCMOntologyJobWorker#handleFangzhengClassification failed to handle job with key 2251799813854193 of type fangzheng-classification, sending fail command to broker
io.camunda.client.api.command.ProblemException: Failed with code 500: 'Internal Server Error'. Details: 'class ProblemDetail {
type: about:blank
title: INTERNAL
status: 500
detail: Unexpected error occurred during the request processing: No remote message handler registered for this message, subject command-api-1
instance: /v2/jobs/2251799813854193/error
}'
at io.camunda.client.impl.http.ApiCallback.handleErrorResponse(ApiCallback.java:149)
at io.camunda.client.impl.http.ApiCallback.completed(ApiCallback.java:74)
at io.camunda.client.impl.http.ApiCallback.completed(ApiCallback.java:34)
at org.apache.hc.core5.concurrent.BasicFuture.completed(BasicFuture.java:148)
at org.apache.hc.core5.concurrent.ComplexFuture.completed(ComplexFuture.java:72)
at org.apache.hc.client5.http.impl.async.InternalAbstractHttpAsyncClient$2$1.completed(InternalAbstractHttpAsyncClient.java:321)
at org.apache.hc.core5.http.nio.support.AbstractAsyncResponseConsumer$1.completed(AbstractAsyncResponseConsumer.java:101)
at org.apache.hc.core5.http.nio.entity.AbstractBinAsyncEntityConsumer.completed(AbstractBinAsyncEntityConsumer.java:87)import io
p=r"D:\work\Ontology\OntologyMachine\OntologyFrameworkTCM\_e2e.log"
d=io.open(p,"rb").read()
try:t=d.decode("utf-8")
except Exception:t=d.decode("gbk",errors="replace")
for l in t.splitlines():
    s=l.strip()
    if "Tests run:" in s or "BUILD" in s or "FAILURE!" in s or s.startswith("[ERROR]") and ("Test" in l or "expected" in l or "Exception" in l):
        print(s[:200])
