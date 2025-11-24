package io.datapulse.etl.event;

import io.datapulse.domain.MarketplaceEvent;
import io.datapulse.domain.MarketplaceType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EtlSourceMeta {

  MarketplaceEvent event();

  MarketplaceType marketplace();

  int order() default 0;
}
