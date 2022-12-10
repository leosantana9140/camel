package br.com.caelum.camel;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.impl.DefaultCamelContext;

public class RotaPedidos {
    public static void main(String[] args) throws Exception {
        CamelContext context = new DefaultCamelContext();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(
                        deadLetterChannel("file:erro")
                        .logExhaustedMessageHistory(true)
                        .maximumRedeliveries(3)
                        .redeliveryDelay(2000)
                );

                from("file:pedidos?delay=5s&noop=true")
                .routeId("Rota-Pedidos")
                .to("validator:pedido.xsd")
                //Enviando a mesma mensagem de entrada para as duas sub-rotas
                .multicast()
                .to("direct:http")
                .to("direct:soap");

                from("direct:http")
                        .routeId("Pedido-HTTP")
                        .setProperty("pedidoId", xpath("/pedido/id/text()"))
                        .setProperty("clientId", xpath("/pedido/pagamento/email-titular/text()"))
                        .split()
                            .xpath("/pedido/itens/item")
                        .filter()
                            .xpath("/item/formato[text()='EBOOK']")
                        .setProperty("ebookId", xpath("/item/livro/codigo/text()"))
                        .marshal()
                            .xmljson()
                        .log("${body}")
                        .setHeader(Exchange.HTTP_METHOD, HttpMethods.GET)
                        .setHeader(Exchange.HTTP_QUERY, simple("ebookId=${property.ebookId}&pedidoId=${property.pedidoId}&clienteId=${property.clienteId}"))
                .to("http4://localhost:8080/webservices/ebook/item");

                from("direct:soap")
                        .routeId("Pedido-SOAP")
                        .to("xslt:pedido-para-soap.xslt")
                        .log("${body}")
                        .setHeader(Exchange.CONTENT_TYPE, constant("text/xml"))
                .to("http4://localhost:8080/webservices/financeiro");
            }
        });

        context.start();
        Thread.sleep(20000);
        context.stop();
    }
}
