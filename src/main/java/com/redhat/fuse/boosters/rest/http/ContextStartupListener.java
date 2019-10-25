package com.redhat.fuse.boosters.rest.http;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.management.event.CamelContextStartedEvent;
import org.apache.camel.management.event.CamelContextStoppingEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;
import java.util.logging.Logger;

@Component
public class ContextStartupListener extends EventNotifierSupport implements ApplicationContextAware {

    private Logger logger = Logger.getLogger(ContextStartupListener.class.getName());

    private CamelContext context;
    private MeterRegistry meterRegistry;
    private ApplicationContext springContext;

    public void setApplicationContext(ApplicationContext context) {
        this.springContext=context;
    }


    @Override
    public void notify(EventObject event) throws Exception {
        try {
            if (event instanceof CamelContextStartedEvent) {
                logger.info("Processing Camel Context Started Event");
                initializeBeans();
                configMetrics();
                logger.info("Camel Context Started Event Processed");
            }
            if (event instanceof CamelContextStoppingEvent) {
                //send stop email notification
            }
        } catch (Exception e) {
            logger.severe("Problem loading metrics: "+ e.getMessage());
            e.printStackTrace();
        }
    }

    private void initializeBeans(){
        context = springContext.getBean("camelContext", CamelContext.class);
        meterRegistry = springContext.getBean(MeterRegistry.class);
    }

    @Override
    public boolean isEnabled(EventObject event) {
        if (event instanceof CamelContextStartedEvent || event instanceof CamelContextStoppingEvent) {
            return true;
        }
        return false;
    }

    private long getExchangesCompleted(String routeId){
        long completed = 0;

        try {
            ManagedRouteMBean route = context.getManagedRoute(routeId, ManagedRouteMBean.class);
            completed = route.getExchangesCompleted();
        }catch(Exception e){
            logger.severe("error getting context info");
        }

        return completed;
    }

    private void configMetrics(){
        String name = "org_apache_camel_ExchangesCompleted";
        String description = "Completed Exchange";

        for(Route route: context.getRoutes()){

            logger.fine("creating metric" + name + "for route "+ route.getId());

            List<Tag> tags = new ArrayList<>();
            tags.add(Tag.of("context", context.getName()));
            tags.add(Tag.of("route", route.getId()));
            tags.add(Tag.of("type", "routes"));

            Gauge.builder(name, this, value -> getExchangesCompleted(route.getId()) )
                    .description(description)
                    .tags(tags)
                    .baseUnit("number")
                    .register(meterRegistry);
        }
    }
}