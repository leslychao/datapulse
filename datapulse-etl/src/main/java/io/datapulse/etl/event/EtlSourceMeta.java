package io.datapulse.etl.event;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EtlSourceMeta {

  MarketplaceEvent[] events() default {};

  MarketplaceType marketplace();

  String rawTableName();

  int order() default 0;
}
